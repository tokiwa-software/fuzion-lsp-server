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
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionContext;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.feature.Completion;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.lsp.server.util.QueryAST;
import dev.flang.shared.IO;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class CompletionTest extends ExtendedBaseTest
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

  private static final String IntervallCompletion = """
      example =>
        (1..2).
    """;

  @Test
  public void getIntervallCompletions()
  {
    SourceText.setText(uri1, IntervallCompletion);
    var expected = """
      map<${2:B}> (□ -> □)
      asString
      infix : ${1:step}
      asList
      asStream
      forAll (□ -> □)
      contains ${1:e}
      size
      upper
      lower
      sizeOption
      asString ${1:sep}
      infix | (□ -> □)
      infix & (□ -> □)
      slice ${1:from} ${2:to}
      fold ${1:m}
      first
      asArray
      isEmpty
      count
      last
      postfix |
      forWhile (□ -> □)
      before (□ -> □)
      filter (□ -> □)
      infix |& (□ -> □)
      infix ∀ (□ -> □)
      infix ∃ (□ -> □)
      take ${1:n}
      drop ${1:n}
      takeWhile (□ -> □)
      dropWhile (□ -> □)
      concatSequences ${1:s}
      infix ++ ${1:s}
      cycle
      tails
      mapSequence<${2:B}> (□ -> □)
      insert ${1:at} ${2:v}
      zip<${3:U}, ${4:V}> ${1:b} (□, □ -> □)
      hashCode
      prefix $""";
    var actual = Completion.getCompletions(params(uri1, 1, 9))
      .getLeft()
      .stream()
      .map(x -> x.getInsertText())
      .collect(Collectors.joining(System.lineSeparator()));

    assertEquals(expected, actual);
  }


  @Test
  public void getListCompletions()
  {
    SourceText.setText(uri1, ListCompletion);
    var completions = Completion.getCompletions(params(uri1, 7, 7));
    assertTrue(completions.getLeft().stream().anyMatch(x -> x.getLabel().startsWith("fold")));
  }

  @Test
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

  @Test
  void CompletionInMatchOfPrecondition()
  {
    var sourceText = """
      towers is

        numOrNil i32|nil := nil
        s
          pre
            match numOrNil
              nil => true
              num i32 => num.
          is""";
    SourceText.setText(uri1, sourceText);
    assertEquals("num", QueryAST.FeatureAt(Cursor(uri1, 7, 23)).get().featureName().baseName());
    var completions = Completion.getCompletions(params(uri1, 7, 23));
    // NYI replace with future proof assertion
    assertEquals(192, completions.getLeft().size());
  }

}
