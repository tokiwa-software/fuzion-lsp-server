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
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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

  public static TokenInfo nextTokenOfType(SourcePosition start, Set<Token> tokens)
  {
    return IO.WithTextInputStream(SourceText.getText(start), () -> {
      var lexer = NewLexerStdIn();
      while (lexer.current() != Token.t_eof && lexer.pos() < start.bytePos())
        {
          lexer.nextRaw();
        }
      while (lexer.current() != Token.t_eof && !tokens.contains(lexer.current()))
        {
          lexer.next();
        }
      return tokenInfo(start, lexer);
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
      return tokenInfo(params, lexer);
    });
  }

  private static TokenInfo tokenInfo(SourcePosition pos, Lexer lexer)
  {
    var uri = toURI(pos);
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
      return tokenInfo(params, lexer);
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
      .replace(FuzionConstants.SYMBOLIC_FUZION_HOME.toString(), SourceText.FuzionHome.toString())).toUri();
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

  /**
   * @param p
   * @param n number of columns to go back
   * @return
   */
  public static Optional<SourcePosition> GoBackInLine(SourcePosition p, int n)
  {
    if (p._column - n < 1)
      {
        return Optional.empty();
      }
    return Optional.of(new SourcePosition(p._sourceFile, p._line, p._column - n));
  }

  public static Optional<TokenInfo> IdentifierTokenAt(SourcePosition pos)
  {
    var currentToken = tokenAt(pos);
    if (!rawTokenAt(pos).token().equals(Token.t_ident)
      && GoBackInLine(pos, 1).isPresent())
      {
        currentToken = tokenAt(GoBackInLine(pos, 1).get());
      }
    return currentToken.token() == Token.t_ident ? Optional.of(currentToken): Optional.empty();
  }

  public static Stream<TokenInfo> Tokens(String str)
  {
    return IO.WithTextInputStream(str, () -> {
      var result = new ArrayList<TokenInfo>();
      var lexer = NewLexerStdIn();
      while (lexer.current() != Token.t_eof)
        {
          lexer.next();
          result.add(tokenInfo(lexer.sourcePos(), lexer));
        }
      return result;
    }).stream();
  }

  public static TokenInfo rawTokenAt(SourcePosition sourcePosition, int shift)
  {
    var token =
      FuzionLexer.rawTokenAt(
        new SourcePosition(sourcePosition._sourceFile, sourcePosition._line, sourcePosition._column + shift));
    return token;
  }

}
