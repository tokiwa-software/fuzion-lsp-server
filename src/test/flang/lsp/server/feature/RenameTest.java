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
 * Source of class RenameTest
 *
 *---------------------------------------------------------------------*/


package test.flang.lsp.server.feature;

import org.eclipse.lsp4j.RenameParams;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.SourceText;
import dev.flang.lsp.server.feature.Rename;
import test.flang.lsp.server.BaseTest;

public class RenameTest extends BaseTest
{


  @Test
  public void PrepareRename() throws Exception
  {
    var sourceText = """
      factors is
        print(i i32) =>
          yak " $i"
        (1..12) | fun print
            """;

    SourceText.setText(uri1, sourceText);

    assertEquals("print", Rename.getPrepareRenameResult(Cursor(uri1, 1, 2)).getPlaceholder());
    assertEquals("print", Rename.getPrepareRenameResult(Cursor(uri1, 3, 16)).getPlaceholder());
    assertEquals(null, Rename.getPrepareRenameResult(Cursor(uri1, 1, 7)).getPlaceholder());
  }


  @Test
  public void RenameFun() throws Exception
  {
    var sourceText = """
      factors is
        print(i i32) =>
          yak " $i"
        (1..12) | fun print
            """;

    SourceText.setText(uri1, sourceText);

    var cursor = Cursor(uri1, 1, 2);
    var values = Rename.getWorkspaceEdit(new RenameParams(cursor.getTextDocument(), cursor.getPosition(), "write"))
      .getChanges()
      .values();
    assertEquals(1, values.size());
    var textEdits = values.stream().findFirst().get();
    assertEquals(2, textEdits.size());
    assertTrue(textEdits.stream().anyMatch(edit -> {
      return edit.getRange().getStart().getLine() == 3
        && edit.getRange().getStart().getCharacter() == 16
        && edit.getRange().getEnd().getLine() == 3
        && edit.getRange().getEnd().getCharacter() == 21;
    }));

  }


}
