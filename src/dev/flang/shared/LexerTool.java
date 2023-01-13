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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

  /**
   * Is str a valid identifier?
   * @param str
   * @return
   */
  public static boolean IsValidIdentifier(String str)
  {
    var isIdentifier = IO.WithTextInputStream(str, () -> {
      var lexer = NewLexerStdIn();
      var startsWithIdent = lexer.current() == Token.t_ident;
      lexer.nextRaw();
      return startsWithIdent && lexer.current().equals(Token.t_eof);
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

  private static Stream<TokenInfo> Tokenize(SourcePosition pos)
  {
    return IO.WithTextInputStream(SourceText.getText(pos), () -> {
      var lexer = NewLexerStdIn();
      var eof = Stream.of(EOFTokenInfo(pos));
      return Stream.concat(Stream.generate(() -> {
        var result = tokenInfo(lexer, pos._sourceFile);
        advance(lexer);
        return result;
      }).flatMap(x -> x).takeWhile(tokenInfo -> tokenInfo.token() != Token.t_eof), eof);
    });
  }

  /**
   * @param start
   * @return stream of tokens starting at start.
   * if start is in the middle of a token return this token as well.
   */
  public static Stream<TokenInfo> TokensFrom(SourcePosition start)
  {
    if (PRECONDITIONS)
      require(
        start._line > SourceText.getText(start).lines().count()
          || start._column - 1 <= Util.CodepointCount(SourceText.LineAt(start)));

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

  /**
   * Next token from start that matches one of tokens
   * or EOF.
   *
   * @param start
   * @param tokens
   * @return
   */
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

  /*
   * creates and initializes a lexer that reads from stdin
   */
  private static Lexer NewLexerStdIn()
  {
    var lexer = new Lexer(SourceFile.STDIN);
    // HACK the following is necessary because currently on instantiation
    // lexer calls next(), skipping any raw tokens at start
    lexer.setPos(0);
    advance(lexer);
    return lexer;
  }

  /**
   * creates a token info from the current lexer, multiple if
   * token comprises of more than one line.
   *
   * @param lexer
   * @param sf
   * @return
   *
   */
  private static Stream<TokenInfo> tokenInfo(Lexer lexer, SourceFile sf)
  {
    var lexerSourcePosition = lexer.sourcePos(lexer.pos());
    var start =
      new SourcePosition(sf, lexerSourcePosition._line, lexerSourcePosition._column);
    var tokenString = lexer.asString(lexer.pos(), lexer.bytePos());
    var token = lexer.current();

    var lines = tokenString
      .split("\\r?\\n");

    return IntStream.range(0, (int) lines.length)
      .mapToObj(
        idx -> new TokenInfo(new SourcePosition(start._sourceFile, start._line + idx, idx == 0 ? start._column: 1),
          lines[idx], token));
  }

  /**
   * Is this line a comment?
   * @param params
   * @return
   */
  public static boolean isCommentLine(SourcePosition params)
  {
    return TokensFrom(params)
      .filter(x -> x.start()._line == params._line)
      .dropWhile(x -> x.token() == Token.t_ws)
      .findFirst()
      .map(x -> x.token() == Token.t_comment)
      .orElse(false);
  }

  /**
   * End of the token to the right of the given pos
   */
  public static SourcePosition EndOfToken(SourcePosition pos)
  {

    return pos.isBuiltIn()
                           ? pos
                           : TokensAt(pos)
                             .right()
                             .end();
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

  /**
   * Ident token right or left of pos or empty.
   * @param pos
   * @return
   */
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
