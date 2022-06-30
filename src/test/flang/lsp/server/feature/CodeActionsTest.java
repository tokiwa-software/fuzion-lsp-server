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
 * Source of class CodeActionsTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server.feature;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.flang.lsp.server.feature.CodeActions;
import dev.flang.lsp.server.feature.Diagnostics;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class CodeActionsTest extends ExtendedBaseTest
{

  @Test
  public void FixNaming()
  {
    var sourceText = """
      ex =>
        SomeFeature_Name is""";
    SourceText.setText(uri1, sourceText);

    assertEquals("some_feature_name", CodeActions
      .getCodeActions(Params(uri1))
      .get(0)
      .getRight()
      .getCommand()
      .getArguments()
      .get(3));
  }

  private static CodeActionParams Params(URI uri)
  {
    return new CodeActionParams(LSP4jUtils.TextDocumentIdentifier(uri),
      new Range(new Position(0, 0),
        new Position(100, 1)),
      new CodeActionContext(Diagnostics.getDiagnostics(uri).collect(Collectors.toList())));
  }

  @Test
  public void FixMandelbrotNaming()
  {
    SourceText.setText(uri1, Mandelbrot);

    assertTrue(CodeActions
      .getCodeActions(Params(uri1))
      .size() > 0);
  }

  @Test @Timeout(value = 60, unit = TimeUnit.SECONDS) @Disabled // too slow
  public void CodeActions() throws IOException
  {
    StdLibAndAllTestFiles()
      .forEach(p -> {
        SourceText.setText(uri1, Read(p));
        assertDoesNotThrow(() -> CodeActions.getCodeActions(Params(uri1)));
      });
  }
}
