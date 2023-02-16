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

import dev.flang.lsp.server.feature.SignatureHelper;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.shared.SourceText;
import test.flang.shared.BaseTest;

public class SignatureHelperTest extends BaseTest
{
  private static final String HelloWorld = """
    HelloWorld is
      say "
        """;

  @Test
  public void getSignatureHelpMultipleSignatures()
  {
    SourceText.setText(uri1, HelloWorld);
    assertEquals("say() => unit : Any", LabelAt(uri1, new Position(1, 3), 0));
    assertEquals("say(s Any) => unit : Any", LabelAt(uri1, new Position(1, 3), 1));
  }

  @Test
  public void getSignatureHelpSimpleExample()
  {
    SourceText.setText(uri1, """
      ex =>
        say""" + " ");
    assertEquals("say() => unit : Any", LabelAt(uri1, new Position(1, 6), 0));
    assertEquals("say(s Any) => unit : Any", LabelAt(uri1, new Position(1, 6), 1));
  }

  @Test
  public void getSignatureHelpMandelbrot()
  {
    SourceText.setText(uri1, Mandelbrot);
    assertEquals("yak(s Any) => unit : Any", LabelAt(uri1, new Position(13, 13), 0));
    assertEquals(
      "mandelbrotImage(yStart f64, yStep f64, xStart f64, xStep f64, height i32, width i32) => unit : Any",
      LabelAt(uri1, new Position(16, 17), 0));
  }

  private String LabelAt(URI uri, final Position position, int index)
  {
    return SignatureHelper.getSignatureHelp(new SignatureHelpParams(LSP4jUtils.TextDocumentIdentifier(uri), position))
      .getSignatures()
      .get(index)
      .getLabel();
  }

}
