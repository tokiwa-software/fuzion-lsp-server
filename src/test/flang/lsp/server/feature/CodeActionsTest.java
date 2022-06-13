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

import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.feature.CodeActions;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class CodeActionsTest extends ExtendedBaseTest
{
    @Test
    public void FixNaming()
    {
        var sourceText = """
            ex =>
              SomeFeatureName is""";
        SourceText.setText(uri1, sourceText);

        var textDocument = Cursor(uri1, 1, 5).getTextDocument();


        var codeActionParams = new CodeActionParams(textDocument,
            new Range(new Position(0, 0),
                new Position(10, 1)),
            new CodeActionContext());

        assertEquals("some_feature_name", CodeActions
            .getCodeActions(codeActionParams)
            .get(0)
            .getRight()
            .getEdit()
            .getChanges()
            .get(textDocument.getUri())
            .get(0)
            .getNewText());
    }
}
