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
 * Source of class LexerToolTest
 *
 *---------------------------------------------------------------------*/


package test.flang.shared;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import dev.flang.parser.Lexer.Token;
import dev.flang.shared.LexerTool;
import dev.flang.shared.SourceText;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

public class LexerToolTest extends BaseTest
{
  @Test
  public void NextToken_a()
  {
    SourceText.setText(uri1, ManOrBoy);

    var nextToken =
      LexerTool.TokensAt(CursorPosition(uri1, 3, 3))
        .right();
    assertEquals("a", nextToken.text());
    assertEquals(4, nextToken.end()._column);
  }

  @Test
  public void NextToken_i32()
  {
    SourceText.setText(uri1, ManOrBoy);

    var tokens = LexerTool.TokensAt(CursorPosition(uri1, 7, 8));
    assertTrue(tokens.right().equals(tokens.left()));
    var nextToken = tokens.right();
    assertEquals("i32", nextToken.text());
    assertEquals(10, nextToken.end()._column);
  }

  @Test
  public void EndOfToken_man_or_boy()
  {
    SourceText.setText(uri1, ManOrBoy);

    var endOfToken = LexerTool.EndOfToken(new SourcePosition(new SourceFile(Path.of(uri1)), 1, 1));
    assertEquals(11, endOfToken._column);
    assertEquals(1, endOfToken._line);

  }

  @Test
  public void EndOfToken_i32()
  {
    SourceText.setText(uri1, ManOrBoy);

    var endOfToken = LexerTool.EndOfToken(new SourcePosition(new SourceFile(Path.of(uri1)), 3, 7));
    assertEquals(10, endOfToken._column);
    assertEquals(3, endOfToken._line);
  }

  @Test
  public void EndOfToken_opening_brace()
  {
    SourceText.setText(uri1, ManOrBoy);

    var endOfToken = LexerTool.EndOfToken(new SourcePosition(new SourceFile(Path.of(uri1)), 3, 4));
    assertEquals(5, endOfToken._column);
    assertEquals(3, endOfToken._line);
  }

  @Test
  public void Tokens()
  {
    SourceText.setText(uri1, ManOrBoy);

    var beforeSay = CursorPosition(uri1, 10, 4);
    assertEquals("\n    ", LexerTool.TokensFrom(beforeSay).findFirst().get().text());
    var atSayStart = CursorPosition(uri1, 10, 5);
    assertEquals("say", LexerTool.TokensFrom(atSayStart).findFirst().get().text());
    var atSayMiddle = CursorPosition(uri1, 10, 7);
    assertEquals("say", LexerTool.TokensFrom(atSayMiddle).findFirst().get().text());
    var atSayEnd = CursorPosition(uri1, 10, 8);
    assertEquals(" ", LexerTool.TokensFrom(atSayEnd).findFirst().get().text());

    var start = CursorPosition(uri1, 1, 1);
    assertTrue(LexerTool.TokensFrom(start).count() > 10);
    assertTrue(LexerTool.TokensFrom(start).count() > 10);
    assertTrue(LexerTool.TokensFrom(start).anyMatch(t -> t.text().equals("i32")));
    assertTrue(LexerTool.TokensFrom(start).reduce((first, second) -> second).get().token() == Token.t_eof);
  }
}
