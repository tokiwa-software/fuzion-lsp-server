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


package test.flang.lsp.server.util;

import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.SourceText;
import dev.flang.lsp.server.Util;
import dev.flang.lsp.server.util.FuzionLexer;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.parser.Lexer.Token;
import test.flang.lsp.server.BaseTest;

public class FuzionLexerTest extends BaseTest {
  @Test
  public void NextTokenOfType_at_start()
  {
    var foundToken = FuzionLexer.nextTokenOfType(", aösldkjf", Util.HashSetOf(Token.t_comma));

    assertEquals(1, foundToken.start()._column);
    assertEquals(2, foundToken.end()._column);
    assertEquals(",", foundToken.text());
  }

  @Test
  public void NextTokenOfType_at_end()
  {
    var foundToken = FuzionLexer.nextTokenOfType("1234,", Util.HashSetOf(Token.t_comma));

    assertEquals(5, foundToken.start()._column);
    assertEquals(6, foundToken.end()._column);
    assertEquals(",", foundToken.text());
  }

  @Test
  public void NextToken_a()
  {

    SourceText.setText(uri(1), ManOrBoy);

    var nextToken =
      FuzionLexer.rawTokenAt(Cursor(uri(1), 2, 2));
    assertEquals("a", nextToken.text());
    assertEquals(4, nextToken.end()._column);
  }

  @Test
  public void NextToken_i32()
  {

    SourceText.setText(uri(1), ManOrBoy);

    var nextToken =
      FuzionLexer.rawTokenAt(Cursor(uri(1), 6, 7));
    assertEquals("i32", nextToken.text());
    assertEquals(10, nextToken.end()._column);
  }

  @Test
  public void EndOfToken_man_or_boy()
  {
    SourceText.setText(uri(1), ManOrBoy);

    var endOfToken = FuzionLexer.endOfToken(uri(1), new Position(0, 0));
    assertEquals(10, endOfToken.getCharacter());
    assertEquals(0, endOfToken.getLine());

  }

  @Test
  public void EndOfToken_i32()
  {
    SourceText.setText(uri(1), ManOrBoy);

    var endOfToken = FuzionLexer.endOfToken(uri(1), new Position(2, 6));
    assertEquals(9, endOfToken.getCharacter());
    assertEquals(2, endOfToken.getLine());
  }

  @Test
  public void EndOfToken_opening_brace()
  {
    SourceText.setText(uri(1), ManOrBoy);

    var endOfToken = FuzionLexer.endOfToken(uri(1), new Position(2, 3));
    assertEquals(4, endOfToken.getCharacter());
    assertEquals(2, endOfToken.getLine());
  }
}
