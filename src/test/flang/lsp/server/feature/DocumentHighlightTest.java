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
 * Source of class DocumentHighlightTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server.feature;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.lsp4j.DocumentHighlightParams;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.flang.lsp.server.feature.Highlight;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class DocumentHighlightTest extends ExtendedBaseTest
{
  @Test @Timeout(value = 5000) @Disabled // too slow
  public void DocumentHighlight()
  {
    var text = Read("test_data/webserver_document_hightlight_EmptyStackException.fz");
    SourceText.setText(uri1, text);

    var lineNum = new AtomicInteger(-1);
    text.lines().forEach(line -> {
      var currentLine = lineNum.incrementAndGet();

      var columnNum = new AtomicInteger(-1);

      line.codePoints().forEach(c -> {
        var currentColumn = columnNum.incrementAndGet();

        var cursor = Cursor(uri1, currentLine, currentColumn);
        assertDoesNotThrow(
          () -> Highlight.getHightlights(new DocumentHighlightParams(TextDocument(cursor), Position(cursor))),
          "line " + currentLine + " column " + currentColumn);
      });

    });

  }
}
