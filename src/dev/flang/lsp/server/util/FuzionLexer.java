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
 * Source of class FuzionLexer
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.util;

import java.net.URI;
import java.util.HashSet;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.lsp.server.Converters;
import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.Util;
import dev.flang.lsp.server.records.TokenInfo;
import dev.flang.parser.Lexer;
import dev.flang.parser.Lexer.Token;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

public class FuzionLexer
{

  public static Boolean IsValidIdentifier(String str)
  {
    var isIdentifier = Util.WithTextInputStream(str, () -> {
      var lexer = new Lexer(SourceFile.STDIN);
      return lexer.current() == Token.t_ident;
    });
    return isIdentifier;
  }

  /**
   * @param str example: "infix %%"
   * @return example: text: "%%", start: 7
   */
  public static TokenInfo nextTokenOfType(String str, HashSet<Token> tokens)
  {
    return Util.WithTextInputStream(str, () -> {
      var lexer = new Lexer(SourceFile.STDIN);

      while (lexer.current() != Token.t_eof && !tokens.contains(lexer.current()))
        {
          lexer.next();
        }
      return FuzionLexer.tokenInfo(lexer);
    });
  }

  public static TokenInfo rawTokenAt(TextDocumentPositionParams params)
  {
    var sourceText = FuzionHelpers.sourceText(params);
    return Util.WithTextInputStream(sourceText, () -> {

      var lexer = new Lexer(SourceFile.STDIN);
      lexer.setPos(lexer.lineStartPos(params.getPosition().getLine() + 1));

      while (lexer.current() != Token.t_eof
        && FuzionLexer.lexerEndPosIsBeforeOrAtTextDocumentPosition(params, lexer))
        {
          lexer.nextRaw();
        }
      return FuzionLexer.tokenInfo(Util.toURI(params.getTextDocument().getUri()), lexer);
    });
  }

  private static TokenInfo tokenInfo(Lexer lexer)
  {
    return FuzionLexer.tokenInfo(SourceFile.STDIN.toUri(), lexer);
  }

  private static TokenInfo tokenInfo(URI uri, Lexer lexer)
  {
    var lexerSourcePosition = lexer.sourcePos(lexer.pos());
    var start =
      new SourcePosition(Bridge.ToSourceFile(uri), lexerSourcePosition._line, lexerSourcePosition._column);
    var tokenString = lexer.asString(lexer.pos(), lexer.bytePos());
    return new TokenInfo(start, tokenString);
  }

  private static boolean lexerEndPosIsBeforeOrAtTextDocumentPosition(TextDocumentPositionParams params, Lexer lexer)
  {
    return (lexer.sourcePos()._column - 1) <= params.getPosition().getCharacter();
  }

  public static TokenInfo tokenAt(TextDocumentPositionParams params)
  {
    var sourceText = FuzionHelpers.sourceText(params);
    return Util.WithTextInputStream(sourceText, () -> {

      var lexer = new Lexer(SourceFile.STDIN);
      lexer.setPos(lexer.lineStartPos(params.getPosition().getLine() + 1));

      while (lexer.current() != Token.t_eof
        && lexerEndPosIsBeforeOrAtTextDocumentPosition(params, lexer))
        {
          lexer.next();
        }
      return tokenInfo(Util.toURI(params.getTextDocument().getUri()), lexer);
    });
  }

  public static boolean isCommentLine(TextDocumentPositionParams params)
  {
    var sourceText = FuzionHelpers.sourceText(params);
    return Util.WithTextInputStream(sourceText, () -> {
      var lexer = new Lexer(SourceFile.STDIN);
      lexer.setPos(lexer.lineStartPos(params.getPosition().getLine() + 1));
      lexer.nextRaw();
      while (lexer.current() == Token.t_ws)
        {
          if (lexer.sourcePos()._line != params.getPosition().getLine() + 1)
            {
              return false;
            }
          lexer.nextRaw();
        }
      return lexer.current() == Token.t_comment;
    });
  }

  public static Position endOfToken(URI uri, Position start)
  {
    var textDocumentPosition = Converters.TextDocumentPositionParams(uri, start);
    var token = rawTokenAt(textDocumentPosition);
    return Bridge.ToPosition(token.end());
  }

}
