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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.lsp.server.enums.TokenModifier;
import dev.flang.lsp.server.enums.TokenType;
import dev.flang.lsp.server.util.Bridge;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.LexerTool;
import dev.flang.shared.Util;
import dev.flang.shared.records.TokenInfo;
import dev.flang.util.ANY;
import dev.flang.util.SourcePosition;

public class SemanticToken extends ANY
{

  public static final SemanticTokensLegend Legend =
    new SemanticTokensLegend(TokenType.asList, TokenModifier.asList);

  public static SemanticTokens getSemanticTokens(SemanticTokensParams params)
  {
    return new SemanticTokens(SemanticTokenData(LexerTokens(params)));
  }

  private static List<TokenInfo> LexerTokens(SemanticTokensParams params)
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
          // discard these tokens
          case t_error :
          case t_ws :
          case t_comma :
          case t_lparen :
          case t_rparen :
          case t_lbrace :
          case t_rbrace :
          case t_lcrochet :
          case t_rcrochet :
          case t_semicolon :
          case t_eof :
          case t_barLimit :
          case t_colonLimit :
          case t_indentationLimit :
          case t_lineLimit :
          case t_spaceLimit :
          case t_undefined :
            return Stream.empty();
          default:
            return Stream.of(t);
          }
      })
      .collect(Collectors.toList());
  }

  private static List<Integer> SemanticTokenData(List<TokenInfo> lexerTokens)
  {
    return IntStream
      .range(0, lexerTokens.size())
      .mapToObj(x -> {
        var beginningOfFileToken =
          new TokenInfo(
            SourcePosition.notAvailable,
            new SourcePosition(lexerTokens.get(x).start()._sourceFile, 1, 1),
            "",
            Token.t_undefined);
        var previousToken = x == 0 ? beginningOfFileToken: lexerTokens.get(x - 1);
        return lexerTokens.get(x).SemanticTokenData(previousToken);
      })
      .flatMap(x -> x)
      .collect(Collectors.toList());
  }

}
