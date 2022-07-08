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
import java.util.List;
import java.util.Map;
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
    return new TokenInfo(
      new SourcePosition(
        pos._sourceFile,
        (int) SourceText.getText(pos).lines().count() + 1,
        1),
      "",
      Token.t_eof);
  }

  private static Map<String, List<TokenInfo>> tokenCache =
    Util.ThreadSafeLRUMap(10, (removed) -> {

    });

  private static Stream<TokenInfo> Tokenize(SourcePosition start)
  {
    return IO.WithTextInputStream(SourceText.getText(start), () -> {
      var lexer = NewLexerStdIn();
      var eof = Stream.of(EOFTokenInfo(start));
      return Stream.concat(Stream.generate(() -> {
        var result = tokenInfo(lexer, start._sourceFile);
        advance(lexer);
        return result;
      }).takeWhile(tokenInfo -> tokenInfo.token() != Token.t_eof), eof);
    });
  }

  /**
   * @param start
   * @return stream of tokens starting at start.
   * if start is in the middle of a token return this token as well.
   */
  public static Stream<TokenInfo> TokensFrom(SourcePosition start)
  {
    return tokenCache.computeIfAbsent(SourceText.getText(start),
      (k) -> Tokenize(start).collect(Collectors.toUnmodifiableList()))
      .stream()
      .dropWhile(x -> x.end()._line < start._line
        || x.end()._column <= start._column);
  }

  private static void advance(Lexer lexer)
  {
    lexer.nextRaw();
    while (lexer.current() == Token.t_error ||
      lexer.current() == Token.t_undefined)
      {
        lexer.nextRaw();
      }
  }

  public static TokenInfo NextTokenOfType(SourcePosition start, Set<Token> tokens)
  {
    return TokensFrom(start)
      .filter(x -> tokens.contains(x.token()))
      .findFirst()
      .orElse(EOFTokenInfo(start));
  }

  /**
   * @param the position of the cursor
   * @return token left and right of cursor
   */
  public static Tokens TokensAt(SourcePosition params)
  {
    var tokens = Stream.concat(TokensFrom(GoLeft(params))
      .limit(2), Stream.of(EOFTokenInfo(params)))
      .collect(Collectors.toList());

    // between two tokens
    if (tokens.get(0).end()._line == params._line
      && tokens.get(0).end()._column == params._column)
      {
        return new Tokens(tokens.get(0), tokens.get(1));
      }
    return new Tokens(tokens.get(0), tokens.get(0));
  }

  private static Lexer NewLexerStdIn()
  {
    var lexer = new Lexer(SourceFile.STDIN);
    // HACK the following is necessary because currently on instantiation
    // lexer calls next(), skipping any raw tokens at start
    lexer.setPos(0);
    advance(lexer);
    return lexer;
  }

  private static TokenInfo tokenInfo(Lexer lexer, SourceFile sf)
  {
    var lexerSourcePosition = lexer.sourcePos(lexer.pos());
    var start =
      new SourcePosition(sf, lexerSourcePosition._line, lexerSourcePosition._column);
    var tokenString = lexer.asString(lexer.pos(), lexer.bytePos());
    return new TokenInfo(start, tokenString, lexer.current());
  }

  public static boolean isCommentLine(SourcePosition params)
  {
    return TokensFrom(params)
      .filter(x -> x.start()._line == params._line)
      .dropWhile(x -> x.token() == Token.t_ws)
      .findFirst()
      .map(x -> x.token() == Token.t_comment)
      .orElse(false);
  }

  public static SourcePosition EndOfToken(SourcePosition start)
  {
    return TokensAt(start)
      .right()
      .end();
  }

  public static SourceFile ToSourceFile(URI uri)
  {
    if (PRECONDITIONS)
      require(!uri.equals(SourceFile.STDIN.toUri()));

    var filePath = Path.of(uri);
    if (filePath.equals(SourcePosition.builtIn._sourceFile._fileName))
      {
        return SourcePosition.builtIn._sourceFile;
      }
    return new SourceFile(filePath);
  }

  /**
   * Move cursor one left. If at start of line same position.
   * @param p
   * @return
   */
  public static SourcePosition GoLeft(SourcePosition p)
  {
    if (p._column == 1)
      {
        return p;
      }
    return new SourcePosition(p._sourceFile, p._line, p._column - 1);
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
        var tokens = TokensAt(pos);
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
    var tokens = TokensAt(pos);
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
