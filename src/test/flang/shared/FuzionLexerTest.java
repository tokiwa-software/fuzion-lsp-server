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
 * Source of class FuzionLexerTest
 *
 *---------------------------------------------------------------------*/


package test.flang.shared;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import dev.flang.shared.FuzionLexer;
import dev.flang.shared.SourceText;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

public class FuzionLexerTest extends BaseTest
{
  @Test
  public void NextToken_a()
  {

    SourceText.setText(uri1, ManOrBoy);

    var nextToken =
      FuzionLexer.rawTokenAt(CursorPosition(uri1, 3, 3));
    assertEquals("a", nextToken.text());
    assertEquals(4, nextToken.end()._column);
  }

  @Test
  public void NextToken_i32()
  {

    SourceText.setText(uri1, ManOrBoy);

    var nextToken =
      FuzionLexer.rawTokenAt(CursorPosition(uri1, 7, 8));
    assertEquals("i32", nextToken.text());
    assertEquals(10, nextToken.end()._column);
  }

  @Test
  public void EndOfToken_man_or_boy()
  {
    SourceText.setText(uri1, ManOrBoy);

    var endOfToken = FuzionLexer.endOfToken(new SourcePosition(new SourceFile(Path.of(uri1)), 1, 1));
    assertEquals(11, endOfToken._column);
    assertEquals(1, endOfToken._line);

  }

  @Test
  public void EndOfToken_i32()
  {
    SourceText.setText(uri1, ManOrBoy);

    var endOfToken = FuzionLexer.endOfToken(new SourcePosition(new SourceFile(Path.of(uri1)), 3, 7));
    assertEquals(10, endOfToken._column);
    assertEquals(3, endOfToken._line);
  }

  @Test
  public void EndOfToken_opening_brace()
  {
    SourceText.setText(uri1, ManOrBoy);

    var endOfToken = FuzionLexer.endOfToken(new SourcePosition(new SourceFile(Path.of(uri1)), 3, 4));
    assertEquals(5, endOfToken._column);
    assertEquals(3, endOfToken._line);
  }
}
