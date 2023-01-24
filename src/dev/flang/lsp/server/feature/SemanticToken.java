/*

This file is part of the Fuzion language server protocol implementation.

The Fuzion language server protocol implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language server protocol implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa Software GmbH, Germany
 *
 * Source of class SemanticToken
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.feature;

import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.lsp.server.enums.TokenModifier;
import dev.flang.lsp.server.enums.TokenType;
import dev.flang.lsp.server.util.Bridge;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.ASTWalker;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.LexerTool;
import dev.flang.shared.Util;
import dev.flang.shared.records.TokenInfo;
import dev.flang.util.ANY;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.SourcePosition;

public class SemanticToken extends ANY
{

  public static final SemanticTokensLegend Legend =
    new SemanticTokensLegend(TokenType.asList, TokenModifier.asList);

  public static SemanticTokens getSemanticTokens(SemanticTokensParams params)
  {
    var pos2Item = Pos2Items(params);

    return new SemanticTokens(SemanticTokenData(LexerTokens(params, pos2Item), pos2Item));
  }

  private static List<TokenInfo> LexerTokens(SemanticTokensParams params, Map<Integer, HasSourcePosition> pos2Item)
  {
    return LexerTool
      .TokensFrom(
        Bridge.ToSourcePosition(
          new TextDocumentPositionParams(params.getTextDocument(), new Position(0, 0))))
      // - map all special strings to normal strings plus operator(s)
      // - split mulitline comment
      .flatMap(t -> {
        var startPlusOne = new SourcePosition(t.start()._sourceFile, t.start()._line, t.start()._column + 1);
        switch (t.token())
          {
          case t_stringBQ :    // '}+-*"' in "abc{x}+-*"
            return Stream.of(
              new TokenInfo(t.start(),
                startPlusOne,
                t.text().substring(0, 1), Token.t_op),
              new TokenInfo(startPlusOne,
                t.end(),
                t.text().substring(1), Token.t_stringQQ));
          case t_stringQD :    // '"x is $' in "x is $x.".
          case t_StringDD :    // '+-*$' in "abc$x+-*$x.".
          case t_stringQB :    // '"a+b is {' in "a+b is {a+b}."
          case t_StringDB :    // '+-*{' in "abc$x+-*{a+b}."
            return Stream.of(
              new TokenInfo(t.start(),
                new SourcePosition(t.start()._sourceFile, t.start()._line,
                  t.start()._column + Util.CodepointCount(t.text()) - 1),
                t.text().substring(0, Util.CharCount(t.text()) - 1), Token.t_stringQQ),
              new TokenInfo(
                new SourcePosition(t.start()._sourceFile, t.start()._line,
                  t.start()._column + Util.CodepointCount(t.text()) - 1),
                t.end(),
                t.text().substring(Util.CharCount(t.text()) - 1, Util.CharCount(t.text())), Token.t_op));
          case t_stringBD :    // '}+-*$' in "abc{x}+-*$x.".
          case t_stringBB :    // '}+-*{' in "abc{x}+-*{a+b}."
            return Stream.of(
              new TokenInfo(t.start(), startPlusOne, "}",
                Token.t_op),
              new TokenInfo(startPlusOne,
                new SourcePosition(t.start()._sourceFile, t.start()._line,
                  t.start()._column + Util.CodepointCount(t.text()) - 1),
                t.text().substring(1, Util.CharCount(t.text()) - 1), Token.t_stringQQ),
              new TokenInfo(
                new SourcePosition(t.start()._sourceFile, t.start()._line,
                  t.start()._column + Util.CodepointCount(t.text()) - 1),
                t.end(),
                t.text().substring(Util.CharCount(t.text()) - 1, Util.CharCount(t.text())), Token.t_op));
          case t_comment :
            var lines = t.text()
              .split("\\r?\\n");
            return IntStream.range(0, (int) lines.length)
              .mapToObj(
                idx -> {
                  return new TokenInfo(
                    new SourcePosition(t.start()._sourceFile, t.start()._line + idx, idx == 0 ? t.start()._column: 1),
                    new SourcePosition(t.start()._sourceFile, t.start()._line + idx, Util.CharCount(lines[idx])),
                    lines[idx],
                    Token.t_comment);
                });
          default:
            return Stream.of(t);
          }
      })
      .filter(x -> x.IsSemanticToken(pos2Item))
      .collect(Collectors.toList());
  }

  /**
   * A simple entry whose equality is decided by comparing its key only.
   */
  private static class EntryEqualByKey<T1, T2> extends SimpleEntry<T1, T2>
  {
    public EntryEqualByKey(T1 key, T2 value)
    {
      super(key, value);
    }

    @Override
    public boolean equals(Object arg0)
    {
      var other = (EntryEqualByKey<T1, T2>) arg0;
      return (this.getKey() == null ? other.getKey() == null: this.getKey().equals(other.getKey()));
    }

    @Override
    public int hashCode()
    {
      return this.getKey().hashCode();
    }
  }

  /*
   * returns a map of: position -> call/feature
   * remark: position is encoded by integer index via function TokenInfo.KeyOf()
   */
  private static Map<Integer, HasSourcePosition> Pos2Items(SemanticTokensParams params)
  {
    return ASTWalker
      .Traverse(LSP4jUtils.getUri(params.getTextDocument()))
      .map(e -> e.getKey())
      .filter(x -> x instanceof AbstractFeature || x instanceof AbstractCall)
      // try to filter all generated features/calls
      .filter(x -> {
        if (x instanceof AbstractFeature af)
          {
            return LexerTool
              .TokensAt(FeatureTool.BareNamePosition(af))
              .right()
              .text()
              .equals(FeatureTool.BareName(af));
          }
        var c = (AbstractCall) x;
        return LexerTool
          .TokensAt(c.pos())
          .right()
          .text()
          .equals(FeatureTool.BareName(c.calledFeature()));
      })
      .map(item -> new EntryEqualByKey<Integer, HasSourcePosition>(
        TokenInfo.KeyOf(item instanceof AbstractFeature af ? FeatureTool.BareNamePosition(af): item.pos()), item))
      // NYI which are the duplicates here? Can we do better in selecting the
      // 'right' ones?
      .distinct()
      .collect(Collectors.toUnmodifiableMap(e -> e.getKey(), e -> e.getValue()));
  }

  private static List<Integer> SemanticTokenData(List<TokenInfo> lexerTokens,
    Map<Integer, HasSourcePosition> pos2Item)
  {
    return IntStream
      .range(0, lexerTokens.size())
      .mapToObj(x -> {
        var beginningOfFileToken =
          new TokenInfo(SourcePosition.notAvailable, new SourcePosition(lexerTokens.get(x).start()._sourceFile, 1, 1),
            "", Token.t_undefined);
        var previousToken = x == 0 ? beginningOfFileToken: lexerTokens.get(x - 1);
        return lexerTokens.get(x).SemanticTokenData(previousToken, pos2Item);
      })
      .flatMap(x -> x)
      .collect(Collectors.toList());
  }

}
