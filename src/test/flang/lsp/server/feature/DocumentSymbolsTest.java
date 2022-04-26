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
 * Source of class DocumentSymbolsTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server.feature;

import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.feature.DocumentSymbols;
import dev.flang.shared.ParserTool;
import dev.flang.shared.SourceText;
import test.flang.shared.BaseTest;

public class DocumentSymbolsTest extends BaseTest
{
  @Test
  public void DocumentSymbols(){
    SourceText.setText(uri1, Mandelbrot);
    var main = ParserTool.TopLevelFeatures(uri1).findFirst().get();
    var documentSymbolTree = DocumentSymbols.DocumentSymbolTree(main);
    assertTrue(documentSymbolTree.getChildren().size() > 0);
  }
}
