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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
import dev.flang.shared.records.TokenInfo;
import dev.flang.util.ANY;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

public class SemanticToken extends ANY
{

  public static final SemanticTokensLegend Legend =
    new SemanticTokensLegend(TokenType.asList, TokenModifier.asList);

  public static SemanticTokens getSemanticTokens(SemanticTokensParams params)
  {
    var pos2Item = Pos2Items(params)
      .entrySet()
      .stream()
      .map(e -> {

        // try to filter all generated features/calls
        var tmp = e.getValue().stream().filter(x -> {
          if (x instanceof AbstractFeature af)
            {
              return LexerTool
                .TokensAt(FeatureTool.BareNamePosition(af), false)
                .right()
                .text()
                .equals(FeatureTool.BareName(af));
            }
          var c = (AbstractCall) x;
          return LexerTool
            .TokensAt(c.pos(), false)
            .right()
            .text()
            .equals(FeatureTool.BareName(c.calledFeature()));
        }).collect(Collectors.toList());


        // happens for e.g. syntax sugar for tuples
        if (tmp.isEmpty())
          {
            return null;
          }

        return new SimpleEntry<Integer, HasSourcePosition>(e.getKey(), tmp.get(0));
      })
      .filter(x -> x != null)
      .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
    return new SemanticTokens(SemanticTokenData(LexerTokens(params, pos2Item), pos2Item));
  }

  private static List<TokenInfo> LexerTokens(SemanticTokensParams params, Map<Integer, HasSourcePosition> pos2Item)
  {
    return LexerTool
      .TokensFrom(
        Bridge.ToSourcePosition(
          new TextDocumentPositionParams(params.getTextDocument(), new Position(0, 0))),
        // raw because we need comments
        true)

      // map all special strings to normal strings plus operator(s)
      .flatMap(t -> {
        switch (t.token())
          {
          case t_stringBQ :    // '}+-*"' in "abc{x}+-*"
            return Stream.of(
              new TokenInfo(new SourcePosition(t.start()._sourceFile, t.start()._line, t.start()._column),
                t.text().substring(0, 1), Token.t_op),
              new TokenInfo(new SourcePosition(t.start()._sourceFile, t.start()._line, t.start()._column + 1),
                t.text().substring(1), Token.t_stringQQ));
          case t_stringQD :    // '"x is $' in "x is $x.".
          case t_StringDD :    // '+-*$' in "abc$x+-*$x.".
          case t_stringQB :    // '"a+b is {' in "a+b is {a+b}."
          case t_StringDB :    // '+-*{' in "abc$x+-*{a+b}."
            return Stream.of(
              new TokenInfo(new SourcePosition(t.start()._sourceFile, t.start()._line, t.start()._column),
                t.text().substring(0, t.text().length() - 1), Token.t_stringQQ),
              new TokenInfo(
                new SourcePosition(t.start()._sourceFile, t.start()._line, t.start()._column + t.text().length() - 1),
                t.text().substring(t.text().length() - 1, t.text().length()), Token.t_op));
          case t_stringBD :    // '}+-*$' in "abc{x}+-*$x.".
          case t_stringBB :    // '}+-*{' in "abc{x}+-*{a+b}."
            return Stream.of(
              new TokenInfo(new SourcePosition(t.start()._sourceFile, t.start()._line, t.start()._column), "}",
                Token.t_op),
              new TokenInfo(new SourcePosition(t.start()._sourceFile, t.start()._line, t.start()._column + 1),
                t.text().substring(1, t.text().length() - 1), Token.t_stringQQ),
              new TokenInfo(
                new SourcePosition(t.start()._sourceFile, t.start()._line, t.start()._column + t.text().length() - 1),
                t.text().substring(t.text().length() - 1, t.text().length()), Token.t_op));
          default:
            return Stream.of(t);
          }
      })
      .filter(x -> x.IsSemanticToken(pos2Item))
      .collect(Collectors.toList());
  }

  private static Map<Integer, HashSet<HasSourcePosition>> Pos2Items(SemanticTokensParams params)
  {
    var result = new TreeMap<Integer, HashSet<HasSourcePosition>>();

    ASTWalker
      .Traverse(LSP4jUtils.getUri(params.getTextDocument()))
      .map(e -> e.getKey())
      .filter(x -> x instanceof AbstractFeature || x instanceof AbstractCall)
      .forEach(x -> {
        var key = TokenInfo.KeyOf(x instanceof AbstractFeature af ? FeatureTool.BareNamePosition(af): x.pos());
        result.computeIfAbsent(key, (k) -> new HashSet<HasSourcePosition>());
        result.computeIfPresent(key, (k, v) -> {
          v.add(x);
          return v;
        });
      });
    return result;
  }

  private static List<Integer> SemanticTokenData(List<TokenInfo> lexerTokens,
    Map<Integer, HasSourcePosition> pos2Item)
  {
    return IntStream
      .range(0, lexerTokens.size())
      .mapToObj(x -> {
        var beginningOfFileToken =
          new TokenInfo(SourcePosition.notAvailable, "", Token.t_undefined);
        var previousToken = x == 0 ? beginningOfFileToken: lexerTokens.get(x - 1);
        return lexerTokens.get(x).SemanticTokenData(previousToken, pos2Item);
      })
      .flatMap(x -> x)
      .collect(Collectors.toList());
  }

}
