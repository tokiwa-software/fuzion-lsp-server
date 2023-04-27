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
import dev.flang.shared.ParserTool;
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
            Definition.getDefinitionLocation(new DefinitionParams(TextDocument(cursor), Position(cursor)))
                .getLeft()
                .get(0);
        var sayUri = sayLocation.getUri();
        var sayStartPosition = sayLocation.getRange().getStart();

        assertTrue(sayUri.endsWith("lib/say.fz"));
        assertEquals(30, sayStartPosition.getLine());
        assertEquals(0, sayStartPosition.getCharacter());

        /**
         * `say(s ref Object) => stdout.┋println s`
         * ┋ indicates the Position used below
         */
        var printlnLocation = Definition
            .getDefinitionLocation(new DefinitionParams(new TextDocumentIdentifier(sayUri), new Position(30, 28)))
            .getLeft()
            .get(0);

        assertTrue(printlnLocation.getUri().endsWith("lib/io/print_effect.fz"));
        assertEquals(34, printlnLocation.getRange().getStart().getLine());
        assertEquals(2, printlnLocation.getRange().getStart().getCharacter());
    }

    @Test
    public void JumpToFeatureWrongArgCount()
    {
        SourceText.setText(uri1,
            """
                ex =>
                  x (y u32) =>
                    say y
                  x
                                """);

        var cursor = Cursor(uri1, 3, 2);

        var definitions = Definition
            .getDefinitionLocation(new DefinitionParams(TextDocument(cursor), Position(cursor)))
            .getLeft();

        assertEquals(1, definitions.size());
        assertTrue(definitions.get(0).getUri().startsWith("file:///tmp/fuzion-lsp-server"));
        assertEquals(1, definitions.get(0).getRange().getStart().getLine());
        assertEquals(2, definitions.get(0).getRange().getStart().getCharacter());
    }

    @Test
    public void JumpToDefinitionOfReturnType()
    {
        SourceText.setText(uri1, "ex =>");

        var effect = DeclaredInUniverse("effect", 1);

        var uri = ParserTool.getUri(effect.pos());
        // private abortable<T: Function<unit>>(f T) unit is intrinsic
        // --------------------------------------------^
        var cursor = Cursor(uri, 61, 52);

        var definitions = Definition
            .getDefinitionLocation(new DefinitionParams(TextDocument(cursor), Position(cursor)))
            .getLeft();

        assertEquals(1, definitions.size());
        assertTrue(definitions.get(0).getUri().contains("unit.fz"));
    }
}
