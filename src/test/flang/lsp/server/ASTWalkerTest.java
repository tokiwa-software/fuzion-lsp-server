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
 * Source of class ASTWalkerTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server;

import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.ASTWalker;
import dev.flang.lsp.server.SourceText;
import dev.flang.lsp.server.util.FuzionParser;

public class ASTWalkerTest extends BaseTest
{

  @Test
  public void NoStackOverflow(){
    var sourceText = """
  ex8 is

    x := mapOf ["one", "two"] [1, 2]

    for s in ["one", "two", "three"] do
      say "$s maps to {x[s]}"
  """;
    SourceText.setText(uri1, sourceText);
    ASTWalker.Traverse(FuzionParser.main(uri1));
  }
}
