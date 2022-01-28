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
 * Source of class DiagnosticsTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server.feature;

import java.util.stream.Collectors;

import org.eclipse.lsp4j.DiagnosticTag;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.feature.Diagnostics;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class DiagnosticsTest extends ExtendedBaseTest
{
    // @Test
    // NYI failing
    public void DiagnosticsShowAirErrors()
    {
        var sourceText = """
            t is
              node(parent node|nil) is
              arr := array<node> 1 (i -> (node nil))""";
        SourceText.setText(uri1, sourceText);
        var diagnostics = Diagnostics.getDiagnostics(uri1);
        assertEquals(1, diagnostics.count());
    }

    @Test
    public void Unused()
    {
        var sourceText = """
            ex is
              a := 0
              b := "asdf"
              say b
            """;
        SourceText.setText(uri1, sourceText);
        var diagnostics = Diagnostics.getDiagnostics(uri1).collect(Collectors.toList());
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).getTags().get(0).equals(DiagnosticTag.Unnecessary));
    }

    // @Test
    // NYI failing
    public void UnusedArgument()
    {
        var sourceText = """
            storage =>
              store(data array<store>|array<nil>) is
              say "asdf"
                        """;
        SourceText.setText(uri1, sourceText);
        var diagnostics = Diagnostics.getDiagnostics(uri1).collect(Collectors.toList());
        assertEquals(2, diagnostics.size());
        assertEquals(1, diagnostics.get(0).getRange().getStart().getLine());
        assertEquals(2, diagnostics.get(0).getRange().getStart().getCharacter());
        assertEquals(1, diagnostics.get(0).getRange().getEnd().getLine());
        assertEquals(37, diagnostics.get(0).getRange().getEnd().getCharacter());
    }

}
