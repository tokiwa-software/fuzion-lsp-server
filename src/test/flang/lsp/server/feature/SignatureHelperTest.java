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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.FuzionTextDocumentService;
import dev.flang.lsp.server.feature.SignatureHelper;

public class SignatureHelperTest
{

  private static final String HelloWorld = """
      HelloWorld is
        say "
    """;

  @Test
  void getSignatureHelp()
  {
    FuzionTextDocumentService.setText("file://uri", HelloWorld);
    var signatureHelp = SignatureHelper.getSignatureHelp(new SignatureHelpParams(new TextDocumentIdentifier("file://uri"), new Position(1, 5)));
    assertEquals("say() => unit", signatureHelp.getSignatures().get(0).getLabel());
    assertEquals("say(s Object) => unit", signatureHelp.getSignatures().get(1).getLabel());
  }

}
