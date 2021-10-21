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
 * Source of class ParserHelperTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.FuzionTextDocumentService;
import dev.flang.lsp.server.ParserHelper;
import dev.flang.util.Errors;

public class ParserHelperTest
{
  @Test
  void getMainFeatureTest()
  {
    FuzionTextDocumentService.setText("file://uri", """
HelloWorld is
  say "Hello World!"
            """);
    var mainFeature = ParserHelper.getMainFeature("file://uri");
    assertEquals(0, Errors.count());
    assertEquals(true, mainFeature.isPresent());
    assertEquals("HelloWorld", mainFeature.get().featureName().baseName());
    assertEquals("file://uri", ParserHelper.getUri(mainFeature.get().pos()));
  }
}
