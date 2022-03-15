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
 * Source of class DefinitionTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server.feature;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.feature.Definition;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class DefinitionTest extends ExtendedBaseTest
{
    @Test
    public void JumpToSayThenJumpToStdOutPrintln()
    {
        SourceText.setText(uri1, HelloWorld);
        var cursor = Cursor(uri1, 1, 3);

        var sayLocation =
            Definition.getDefinitionLocation(new DefinitionParams(cursor.getTextDocument(), cursor.getPosition()))
                .getLeft()
                .get(0);
        var sayUri = sayLocation.getUri();
        var sayStartPosition = sayLocation.getRange().getStart();

        assertTrue(sayUri.endsWith("lib/say.fz"));
        assertEquals(28, sayStartPosition.getLine());
        assertEquals(0, sayStartPosition.getCharacter());

        /**
         * `say(s ref Object) => stdout.┋println s`
         * ┋ indicates the Position used below
         */
        var printlnLocation = Definition
            .getDefinitionLocation(new DefinitionParams(new TextDocumentIdentifier(sayUri), new Position(28, 28)))
            .getLeft()
            .get(0);

        assertTrue(printlnLocation.getUri().endsWith("lib/io/out.fz"));
        assertEquals(41, printlnLocation.getRange().getStart().getLine());
        assertEquals(2, printlnLocation.getRange().getStart().getCharacter());
    }
}
