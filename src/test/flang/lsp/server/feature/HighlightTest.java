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
 * Source of class HighlightTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server.feature;

import org.eclipse.lsp4j.DocumentHighlightParams;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.feature.Highlight;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class HighlightTest extends ExtendedBaseTest
{
  @Test
  public void Highlight()
  {

    SourceText.setText(uri1, Read("fuzion/tests/rosettacode_primes/primes.fz"));
    var c = Cursor(uri1, 54, 15);
    assertDoesNotThrow(
      () -> Highlight.getHightlights(new DocumentHighlightParams(c.getTextDocument(), c.getPosition())));

  }
}
