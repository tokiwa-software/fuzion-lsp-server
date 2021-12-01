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
 * Source of class CompletionTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server.feature;

import java.net.URI;

import org.eclipse.lsp4j.CompletionContext;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.SourceText;
import dev.flang.lsp.server.feature.Completion;
import dev.flang.lsp.server.util.LSP4jUtils;
import test.flang.lsp.server.BaseTest;

public class CompletionTest extends BaseTest
{
  private static final String ListCompletion = """
    fasta =>
      selectRandom() =>
        "a"

      randomFasta() =>
        (1..10)
          .map<string>(_ -> selectRandom())
          .
        """;

  private static final String FeatureCallCompletion = """
    fasta =>
      selectRandom() =>
        "a"

      randomFasta() =>
        (1..10)
          .map<string>(_ -> selectRandom())
          .fold(strings.)
        """;


  @Test
  public void getListCompletions()
  {
    SourceText.setText(uri1, ListCompletion);
    var completions = Completion.getCompletions(params(uri1, 7, 7));
    assertTrue(completions.getLeft().stream().anyMatch(x -> x.getLabel().startsWith("fold")));
  }

  @Test
  @Tag("TAG")
  public void getFeatureCallCompletions()
  {
    SourceText.setText(uri1, FeatureCallCompletion);
    var completions = Completion.getCompletions(params(uri1, 7, 20));
    assertTrue(completions.getLeft().stream().anyMatch(x -> x.getInsertText().equals("concat")));
  }

  private CompletionParams params(URI uri, int line, int character)
  {
    return new CompletionParams(LSP4jUtils.TextDocumentIdentifier(uri), new Position(line, character),
      new CompletionContext(CompletionTriggerKind.TriggerCharacter, "."));
  }

}
