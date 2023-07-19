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

import org.junit.jupiter.api.Test;

import dev.flang.shared.LexerTool;
import dev.flang.shared.SourceText;

public class LexerToolTest extends BaseTest
{
  @Test
  public void NextToken_a()
  {
    SourceText.setText(uri1, ManOrBoy);

    var nextToken =
      LexerTool.TokensAt(Cursor(uri1, 2, 2))
        .right();
    assertEquals("a", nextToken.text());
    assertEquals(4, nextToken.end().column());
  }

  @Test
  public void NextToken_i32()
  {
    SourceText.setText(uri1, ManOrBoy);

    var tokens = LexerTool.TokensAt(Cursor(uri1, 6, 7));
    assertTrue(tokens.right().equals(tokens.left()));
    var nextToken = tokens.right();
    assertEquals("i32", nextToken.text());
    assertEquals(10, nextToken.end().column());
  }

  @Test
  public void EndOfToken_man_or_boy()
  {
    SourceText.setText(uri1, ManOrBoy);

    var endOfToken = LexerTool.EndOfToken(Cursor(uri1, 0, 0));
    assertEquals(11, endOfToken.column());
    assertEquals(1, endOfToken.line());

  }

  @Test
  public void EndOfToken_i32()
  {
    SourceText.setText(uri1, ManOrBoy);

    var endOfToken = LexerTool.EndOfToken(Cursor(uri1, 2, 6));
    assertEquals(10, endOfToken.column());
    assertEquals(3, endOfToken.line());
  }

  @Test
  public void EndOfToken_opening_brace()
  {
    SourceText.setText(uri1, ManOrBoy);

    var endOfToken = LexerTool.EndOfToken(Cursor(uri1, 2, 3));
    assertEquals(5, endOfToken.column());
    assertEquals(3, endOfToken.line());
  }

  @Test
  public void Tokens()
  {
    SourceText.setText(uri1, ManOrBoy);

    var beforeSay = Cursor(uri1, 9, 3);
    assertTrue(LexerTool.TokensFrom(beforeSay).findFirst().get().text().isBlank());
    var atSayStart = Cursor(uri1, 9, 4);
    assertEquals("say", LexerTool.TokensFrom(atSayStart).findFirst().get().text());
    var atSayMiddle = Cursor(uri1, 9, 6);
    assertEquals("say", LexerTool.TokensFrom(atSayMiddle).findFirst().get().text());
    var atSayEnd = Cursor(uri1, 9, 7);
    assertEquals(" ", LexerTool.TokensFrom(atSayEnd).findFirst().get().text());

    var start = Cursor(uri1, 0, 0);
    assertTrue(LexerTool.TokensFrom(start).count() > 10);
    assertTrue(LexerTool.TokensFrom(start).count() > 10);
    assertTrue(LexerTool.TokensFrom(start).anyMatch(t -> t.text().equals("i32")));
  }
}
