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
 * Source of class InlayHintTest
 *
 *---------------------------------------------------------------------*/


package test.flang.lsp.server.feature;

import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import dev.flang.lsp.server.feature.InlayHints;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class InlayHintTest extends ExtendedBaseTest
{

  // @Test
  // NYI currently failing
  public void InlayHintsComposedArg()
  {
    SourceText.setText(uri1, Mandelbrot);
    var cursor = Cursor(uri1, 1, 8);

    var inlayHints = InlayHints
      .getInlayHints(new InlayHintParams(cursor.getTextDocument(), new Range(new Position(0, 0), new Position(3,0))));

    assertEquals(1, inlayHints.size());

    assertEquals("maxEscapeIterations:", inlayHints.get(0).getLabel().getLeft());
    assertEquals(2, inlayHints.get(0).getPosition().getLine());
    assertEquals(66, inlayHints.get(0).getPosition().getCharacter());
  }


}