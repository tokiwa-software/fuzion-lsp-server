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
 * Source of class SignatureHelperTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server.feature;

import java.net.URI;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.SourceText;
import dev.flang.lsp.server.feature.SignatureHelper;
import dev.flang.lsp.server.util.LSP4jUtils;
import test.flang.lsp.server.BaseTest;

public class SignatureHelperTest extends BaseTest
{


  @Test
  public void getSignatureHelpMultipleSignatures()
  {
    SourceText.setText(uri1, HelloWorld_Incomplete);
    assertEquals("say() => unit", LabelAt(uri1, new Position(1, 5), 0));
    assertEquals("say(s Object) => unit", LabelAt(uri1, new Position(1, 5), 1));
  }

  @Test
  @Tag("TAG")
  public void getSignatureHelpLambda()
  {
    var sourceText = """
      ex is
        (1..10)
          .map<i64>(x -> x.as_i64)
          .drop(1)
          .take(1)
          .forAll(x -> say x)
        say "samo"
      """;

    SourceText.setText(uri1, sourceText);
    assertTrue(LabelAt(uri1, new Position(2, 6), 0).startsWith("map"));
    assertTrue(LabelAt(uri1, new Position(3, 8), 0).startsWith("drop"));
    assertTrue(LabelAt(uri1, new Position(4, 8), 0).startsWith("take"));
    assertTrue(LabelAt(uri1, new Position(5, 10), 0).startsWith("forAll"));
    assertTrue(LabelAt(uri1, new Position(6, 6), 0).startsWith("say"));
  }

  @Test
  public void getSignatureHelpMandelbrot()
  {
    SourceText.setText(uri1, Mandelbrot);
    assertEquals("yak(s Object) => unit", LabelAt(uri1, new Position(17, 13), 0));
    assertEquals("mandelbrotImage(yStart f64, yStep f64, xStart f64, xStep f64, height i32, width i32) => unit",
      LabelAt(uri1, new Position(20, 17), 0));
  }

  private String LabelAt(URI uri, final Position position, int index)
  {
    return SignatureHelper.getSignatureHelp(new SignatureHelpParams(LSP4jUtils.TextDocumentIdentifier(uri), position))
      .getSignatures()
      .get(index)
      .getLabel();
  }

}
