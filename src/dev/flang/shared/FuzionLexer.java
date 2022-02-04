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

package dev.flang.shared;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;

import dev.flang.lsp.server.util.Bridge;
import dev.flang.parser.Lexer;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.records.TokenInfo;
import dev.flang.util.FuzionConstants;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

public class FuzionLexer
{

  public static Boolean IsValidIdentifier(String str)
  {
    var isIdentifier = IO.WithTextInputStream(str, () -> {
      var lexer = NewLexerStdIn();
      return lexer.current() == Token.t_ident;
    });
    return isIdentifier;
  }

  /**
   * @param str example: "infix %%"
   * @return example: text: "%%", start: 7
   */
  @Deprecated
  public static TokenInfo nextTokenOfType(String str, HashSet<Token> tokens)
  {
    return IO.WithTextInputStream(str, () -> {
      var lexer = NewLexerStdIn();

      while (lexer.current() != Token.t_eof && !tokens.contains(lexer.current()))
        {
          lexer.next();
        }
      return tokenInfo(lexer);
    });
  }

  public static TokenInfo nextTokenOfType(SourcePosition start, HashSet<Token> tokens)
  {
    return IO.WithTextInputStream(SourceText.getText(start), () -> {
      var lexer = NewLexerStdIn();
      lexer.setPos(start.bytePos());
      while (lexer.current() != Token.t_eof && !tokens.contains(lexer.current()))
        {
          lexer.next();
        }
      return tokenInfo(toURI(start), lexer);
    });
  }

  private static Lexer NewLexerStdIn()
  {
    return new Lexer(SourceFile.STDIN);
  }

  public static TokenInfo rawTokenAt(SourcePosition params)
  {
    var sourceText = SourceText.getText(params);
    return IO.WithTextInputStream(sourceText, () -> {

      var lexer = NewLexerStdIn();
      try
        {
          lexer.setPos(lexer.lineStartPos(params._line));
        }
      catch (ArrayIndexOutOfBoundsException e)
        {
          return new TokenInfo(params, "", Token.t_eof);
        }

      while (lexer.current() != Token.t_eof
        && lexerEndPosIsBeforeOrAtTextDocumentPosition(params, lexer))
        {
          lexer.nextRaw();
        }
      return tokenInfo(toURI(params), lexer);
    });
  }

  private static TokenInfo tokenInfo(Lexer lexer)
  {
    return tokenInfo(SourceFile.STDIN.toUri(), lexer);
  }

  private static TokenInfo tokenInfo(URI uri, Lexer lexer)
  {
    var lexerSourcePosition = lexer.sourcePos(lexer.pos());
    var start =
      new SourcePosition(ToSourceFile(uri), lexerSourcePosition._line, lexerSourcePosition._column);
    var tokenString = lexer.asString(lexer.pos(), lexer.bytePos());
    return new TokenInfo(start, tokenString, lexer.current());
  }

  private static boolean lexerEndPosIsBeforeOrAtTextDocumentPosition(SourcePosition params, Lexer lexer)
  {
    return lexer.sourcePos()._column <= params._column;
  }

  public static TokenInfo tokenAt(SourcePosition params)
  {
    var sourceText = SourceText.getText(params);
    return IO.WithTextInputStream(sourceText, () -> {

      var lexer = NewLexerStdIn();
      lexer.setPos(lexer.lineStartPos(params._line));

      while (lexer.current() != Token.t_eof
        && lexerEndPosIsBeforeOrAtTextDocumentPosition(params, lexer))
        {
          lexer.next();
        }
      return tokenInfo(toURI(params), lexer);
    });
  }

  public static boolean isCommentLine(SourcePosition params)
  {
    var sourceText = SourceText.getText(params);
    return IO.WithTextInputStream(sourceText, () -> {
      var lexer = NewLexerStdIn();
      lexer.setPos(lexer.lineStartPos(params._line));
      lexer.nextRaw();
      while (lexer.current() == Token.t_ws)
        {
          if (lexer.sourcePos()._line != params._line)
            {
              return false;
            }
          lexer.nextRaw();
        }
      return lexer.current() == Token.t_comment;
    });
  }

  public static SourcePosition endOfToken(SourcePosition start)
  {
    var token = rawTokenAt(start);
    return token.end();
  }

  public static URI toURI(SourcePosition sourcePosition)
  {
    return Path.of(sourcePosition._sourceFile._fileName.toString()
      .replace(FuzionConstants.SYMBOLIC_FUZION_HOME.toString(), System.getProperty("fuzion.home"))).toUri();
  }

  public static SourceFile ToSourceFile(URI uri)
  {
    var filePath = Path.of(uri);
    if (uri.equals(SourceFile.STDIN.toUri()))
      {
        return new SourceFile(SourceFile.STDIN);
      }
    if (filePath.equals(SourcePosition.builtIn._sourceFile._fileName))
      {
        return SourcePosition.builtIn._sourceFile;
      }
    return new SourceFile(filePath);
  }

}
