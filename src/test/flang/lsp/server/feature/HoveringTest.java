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
 * Source of class HoveringTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server.feature;

import org.eclipse.lsp4j.HoverParams;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.feature.Hovering;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class HoveringTest extends ExtendedBaseTest
{
  private static final String ChoiceType = """
    binary_trees is
      node(left node|unit) ref is
        """;

  @Test
  public void hoverChoiceType()
  {
    SourceText.setText(uri1, ChoiceType);
    var cursor = Cursor(uri1, 1, 8);
    assertEquals("left choice node unit",
      Hovering.getHover(new HoverParams(TextDocument(cursor), Position(cursor)))
        .getContents()
        .getRight()
        .getValue());
  }


}
