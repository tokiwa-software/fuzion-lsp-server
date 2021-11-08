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
 * Source of class SyncIncremental
 *
 *---------------------------------------------------------------------*/


package dev.flang.lsp.server.feature;

import java.util.List;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;

import dev.flang.lsp.server.SourceText;
import dev.flang.lsp.server.Util;

public class SyncIncremental
{
  // NYI this is broken (on windows)
  public String SyncKindIncremental(DidChangeTextDocumentParams params)
  {
    var uri = Util.getUri(params.getTextDocument());
    var text = SourceText.getText(uri).orElseThrow();
    var contentChanges = params.getContentChanges();
    return applyContentChanges(text, contentChanges);
  }

  /**
   * sort descending by line then descending by character
   *
   * @param contentChanges
   */
  private void reverseSort(List<TextDocumentContentChangeEvent> contentChanges)
  {
    contentChanges.sort((left, right) -> {
      if (right.getRange().getStart().getLine() == left.getRange().getStart().getLine())
        {
          return Integer.compare(right.getRange().getStart().getCharacter(), left.getRange().getStart().getCharacter());
        }
      else
        {
          return Integer.compare(right.getRange().getStart().getLine(), left.getRange().getStart().getLine());
        }
    });
  }

  private String applyContentChanges(String text, List<TextDocumentContentChangeEvent> contentChanges)
  {
    reverseSort(contentChanges);
    return contentChanges.stream().reduce(text, (_text, contentChange) -> {
      var start = ordinalIndexOf(_text, System.lineSeparator(), contentChange.getRange().getStart().getLine()) + 1
        + contentChange.getRange().getStart().getCharacter();
      var end = ordinalIndexOf(_text, System.lineSeparator(), contentChange.getRange().getEnd().getLine()) + 1
        + contentChange.getRange().getEnd().getCharacter();
      return _text.substring(0, start) + contentChange.getText() + _text.substring(end, _text.length());
    }, String::concat);
  }

  // taken from apache commons
  private static int ordinalIndexOf(String str, String substr, int n)
  {
    if (n == 0)
      {
        return -1;
      }
    int pos = str.indexOf(substr);
    while (--n > 0 && pos != -1)
      pos = str.indexOf(substr, pos + 1);
    return pos;
  }

}
