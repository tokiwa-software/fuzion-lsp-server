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
 * Source of class ConcurrencyTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server.util;

import org.junit.jupiter.api.Test;

import dev.flang.ast.Feature;
import dev.flang.ast.Type;
import dev.flang.lsp.server.util.ASTItem;
import test.flang.lsp.server.BaseTest;

public class ASTItemTest extends BaseTest{

  @Test
  // NYI This Test can be deleted in the future
  // as it basically only tests my understanding of
  // the instanceof operator.
  public void SourcePosition(){
    assertTrue(ASTItem.sourcePosition(new Feature()).isPresent());
    assertTrue(ASTItem.sourcePosition(new Type("none")).isPresent());
  }

}
