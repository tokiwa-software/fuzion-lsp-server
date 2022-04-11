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
 * Source of class LexerTool
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import dev.flang.parser.Lexer;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.records.TokenInfo;
import dev.flang.util.ANY;
import dev.flang.util.FuzionConstants;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

public class LexerTool extends ANY
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
   * Stream of Tokens from start
   * Never empty, last token eof is always present
   * @param start
   * @param includeRaw
   * @return
   */
  public static Stream<TokenInfo> Tokens(SourcePosition start, boolean includeRaw)
  {
    var eof = Stream.of(new TokenInfo(start, "", Token.t_eof));
    return IO.WithTextInputStream(SourceText.getText(start), () -> {
      var lexer = NewLexerStdIn();
      try
        {
          lexer.setPos(lexer.lineStartPos(start._line));
        }
      catch (ArrayIndexOutOfBoundsException e)
        {
          return eof;
        }
      while (lexer.current() != Token.t_eof
        && (lexer.sourcePos()._line < start._line
          || lexer.sourcePos()._column <= start._column))
        {
          advance(lexer, includeRaw);
        }
      return Stream.concat(Stream.generate(() -> {
        var result = tokenInfo(lexer, toURI(start));
        advance(lexer, includeRaw);
        return result;
      })
        .takeWhile(tokenInfo -> tokenInfo.token() != Token.t_eof), eof);
    });
  }

  private static void advance(Lexer lexer, boolean includeRaw)
  {
    if (includeRaw)
      {
        lexer.nextRaw();
      }
    else
      {
        lexer.next();
      }
  }

  public static TokenInfo NextTokenOfType(SourcePosition start, Set<Token> tokens)
  {
    return Tokens(start, true)
      .filter(x -> tokens.contains(x.token()))
      .findFirst()
      .orElse(new TokenInfo(start, "", Token.t_eof));
  }

  public static TokenInfo TokenAt(SourcePosition params)
  {
    var token = Tokens(params, false)
      .findFirst()
      .get();
    if(POSTCONDITIONS)
      ensure(params._line == token.start()._line);
    return token;
  }

  public static TokenInfo RawTokenAt(SourcePosition params)
  {
    var token = Tokens(params, true)
      .findFirst()
      .get();
    if(POSTCONDITIONS)
      ensure(params._line == token.start()._line);
    return token;
  }

  private static Lexer NewLexerStdIn()
  {
    return new Lexer(SourceFile.STDIN);
  }

  private static TokenInfo tokenInfo(Lexer lexer, URI uri)
  {
    var lexerSourcePosition = lexer.sourcePos(lexer.pos());
    var start =
      new SourcePosition(ToSourceFile(uri), lexerSourcePosition._line, lexerSourcePosition._column);
    var tokenString = lexer.asString(lexer.pos(), lexer.bytePos());
    return new TokenInfo(start, tokenString, lexer.current());
  }

  public static boolean isCommentLine(SourcePosition params)
  {
    return Tokens(params, true)
      .filter(x -> x.start()._line == params._line)
      .dropWhile(x -> x.token() == Token.t_ws)
      .findFirst()
      .map(x -> x.token() == Token.t_comment)
      .orElse(false);
  }

  public static SourcePosition EndOfToken(SourcePosition start)
  {
    return RawTokenAt(start)
      .end();
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
  public static SourcePosition GoBackInLine(SourcePosition p, int n)
  {
    if (p._column - n < 1)
      {
        return new SourcePosition(p._sourceFile, p._line, 1);
      }
    return new SourcePosition(p._sourceFile, p._line, p._column - n);
  }

  /**
   * looks for an identifier token at position
   * if none found look for identifier token at position - 1
   * if none found look for operator   token at position
   * if none found look for operator   token at position - 1
   * @param pos
   * @return
   */
  public static Optional<TokenInfo> IdentOrOperatorTokenAt(SourcePosition pos)
  {
    var currentToken = TokenAt(pos);
    if (!RawTokenAt(pos).token().equals(Token.t_ident))
      {
        currentToken = TokenAt(GoBackInLine(pos, 1));
      }
    return currentToken.token() == Token.t_ident ? Optional.of(currentToken): OperatorTokenAt(pos);
  }

  private static Optional<TokenInfo> OperatorTokenAt(SourcePosition pos)
  {
    var currentToken = TokenAt(pos);
    if (!RawTokenAt(pos).token().equals(Token.t_op))
      {
        currentToken = TokenAt(GoBackInLine(pos, 1));
      }
    return currentToken.token() == Token.t_op ? Optional.of(currentToken): Optional.empty();
  }

}
