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
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.SourceText;
import dev.flang.lsp.server.feature.SignatureHelper;
import dev.flang.lsp.server.util.LSP4jUtils;
import test.flang.lsp.server.BaseTest;

public class SignatureHelperTest extends BaseTest
{
  private static final String HelloWorld = """
      HelloWorld is
        say "
    """;

  private static final String Mandelbrot = """
  mandelbrotexample is
    isInMandelbrotSet(c complex<f64>, maxEscapeIterations i32, z complex<f64>) bool is
      maxEscapeIterations = 0 || z.abs² <= 4 && isInMandelbrotSet c maxEscapeIterations-1 z*z+c

    # NYI how to convert i32 to f64?
    to_f64(i i32) f64 is
      if i > 0 1.0 + to_f64(i - 1) else 1.0

    steps(start, step f64, numPixels i32) =>
      array<f64> numPixels (i -> start + to_f64(i) * step)

    mandelbrotImage(yStart, yStep, xStart, xStep f64, height, width i32) =>
      for y in steps yStart yStep height do
        for x in steps xStart xStep width do
          if isInMandelbrotSet (complex x y) 50 (complex 0.0 0.0)
            yak "⬤"
          else
            yak
        say ""

    mandelbrotImage 1 -0.05 -2 0.0315 40 80
  """;

  @Test
  public void getSignatureHelpMultipleSignatures()
  {
    SourceText.setText(uri1, HelloWorld);
    assertEquals("say() => unit", LabelAt(uri1, new Position(1, 5), 0));
    assertEquals("say(s Object) => unit", LabelAt(uri1, new Position(1, 5), 1));
  }

  @Test
  public void getSignatureHelpMandelbrot()
  {
    SourceText.setText(uri1, Mandelbrot);
    assertEquals("yak(s Object) => unit", LabelAt(uri1, new Position(17, 13), 0));
    assertEquals("mandelbrotImage(yStart f64, yStep f64, xStart f64, xStep f64, height i32, width i32) => unit", LabelAt(uri1, new Position(20, 17), 0));
  }

  private String LabelAt(URI uri, final Position position, int index)
  {
    return SignatureHelper.getSignatureHelp(new SignatureHelpParams(LSP4jUtils.TextDocumentIdentifier(uri), position))
    .getSignatures().get(index).getLabel();
  }

}
