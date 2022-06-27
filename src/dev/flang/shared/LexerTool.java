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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.parser.Lexer;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.records.TokenInfo;
import dev.flang.shared.records.Tokens;
import dev.flang.util.ANY;
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

  private static TokenInfo EOFTokenInfo(SourcePosition pos)
  {
    // NYI reuse lexer, also feed lexer from file
    return IO.WithTextInputStream(SourceText.getText(pos), () -> {
      var lexer = NewLexerStdIn();
      var lastLine = lexer.lineNum(lexer.byteLength());
      return new TokenInfo(new SourcePosition(pos._sourceFile, lastLine + 1, 1), "", Token.t_eof);
    });
  }

  /**
   * Stream of Tokens from start
   * Never empty, last token eof is always present
   * @param start
   * @param includeRaw
   * @return
   */
  public static Stream<TokenInfo> TokensFrom(SourcePosition start, boolean includeRaw)
  {
    // NYI reuse lexer, also feed lexer from file
    return IO.WithTextInputStream(SourceText.getText(start), () -> {
      var lexer = NewLexerStdIn();

      var eof = Stream.of(EOFTokenInfo(start));
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
          || lexer.sourcePos()._column < start._column))
        {
          advance(lexer, includeRaw);
        }
      return Stream.concat(Stream.generate(() -> {
        var result = tokenInfo(lexer, SourceText.UriOf(start));
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
    return TokensFrom(start, true)
      .filter(x -> tokens.contains(x.token()))
      .findFirst()
      .orElse(EOFTokenInfo(start));
  }

  public static Tokens TokensAt(SourcePosition params, boolean includeRaw)
  {
    var tokens = TokensFrom(new SourcePosition(params._sourceFile, params._line, 1), includeRaw)
      .filter(x -> x.start()._line == params._line)
      .dropWhile(x -> x.end()._column < params._column)
      .takeWhile(x -> x.start()._column <= params._column)
      .collect(Collectors.toList());

    switch (tokens.size())
      {
      case 0 :
        var eof = EOFTokenInfo(params);
        return new Tokens(eof, eof);
      case 1 :
        return new Tokens(tokens.get(0), tokens.get(0));
      case 2 :
        return new Tokens(tokens.get(0), tokens.get(1));
      default:
        throw new RuntimeException("too many tokens in result");
      }
  }

  private static Lexer NewLexerStdIn()
  {
    var lexer = new Lexer(SourceFile.STDIN);
    // HACK the following is necessary because currently on instantiation
    // lexer calls next(), skipping any raw tokens at start
    lexer.setPos(0);
    lexer.nextRaw();
    return lexer;
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
    return TokensFrom(params, true)
      .filter(x -> x.start()._line == params._line)
      .dropWhile(x -> x.token() == Token.t_ws)
      .findFirst()
      .map(x -> x.token() == Token.t_comment)
      .orElse(false);
  }

  public static SourcePosition EndOfToken(SourcePosition start)
  {
    return TokensAt(start, true)
      .right()
      .end();
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
    return IdentTokenAt(pos)
      .or(() -> {
        var tokens = TokensAt(pos, false);
        if (tokens.right().token() == Token.t_op)
          {
            return Optional.of(tokens.right());
          }
        if (tokens.left().token() == Token.t_op)
          {
            return Optional.of(tokens.left());
          }
        return Optional.empty();
      });
  }

  public static Optional<TokenInfo> IdentTokenAt(SourcePosition pos)
  {
    var tokens = TokensAt(pos, false);
    if (tokens.right().token() == Token.t_ident)
      {
        return Optional.of(tokens.right());
      }
    if (tokens.left().token() == Token.t_ident)
      {
        return Optional.of(tokens.left());
      }
    return Optional.empty();
  }

}
