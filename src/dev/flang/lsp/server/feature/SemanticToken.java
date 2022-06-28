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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
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

  // https://stackoverflow.com/questions/23699371/java-8-distinct-by-property
  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor)
  {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }

  public static SemanticTokens getSemanticTokens(SemanticTokensParams params)
  {
    var pos2Item = Pos2Item(params);
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

  private static Map<Integer, HasSourcePosition> Pos2Item(SemanticTokensParams params)
  {
    var pos2Item = ASTWalker
      .Traverse(LSP4jUtils.getUri(params.getTextDocument()))
      .map(e -> e.getKey())
      .filter(x -> x instanceof AbstractFeature || x instanceof AbstractCall)
      // NYI what to do if there is multiple things at same pos?
      .filter(distinctByKey(x -> {
        var pos = x instanceof AbstractFeature af ? FeatureTool.BaseNamePosition(af): x.pos();
        return TokenInfo.KeyOf(pos);
      }))
      .collect(Collectors.toUnmodifiableMap(x -> {
        var pos = x instanceof AbstractFeature af ? FeatureTool.BaseNamePosition(af): x.pos();
        return TokenInfo.KeyOf(pos);
      }, x -> x));
    return pos2Item;
  }

  private static List<Integer> SemanticTokenData(List<TokenInfo> lexerTokens,
    Map<Integer, HasSourcePosition> pos2Item)
  {
    return IntStream
      .range(0, lexerTokens.size())
      .mapToObj(x -> {
        var beginningOfFileToken =
          new TokenInfo(new SourcePosition(new SourceFile(SourceFile.STDIN), 1, 1), "", Token.t_undefined);
        var previousToken = x == 0 ? beginningOfFileToken: lexerTokens.get(x - 1);
        return lexerTokens.get(x).SemanticTokenData(previousToken, pos2Item);
      })
      .flatMap(x -> x)
      .collect(Collectors.toList());
  }

}
