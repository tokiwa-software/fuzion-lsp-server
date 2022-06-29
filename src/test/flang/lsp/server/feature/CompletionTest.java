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
import java.util.Arrays;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionContext;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.enums.TriggerCharacters;
import dev.flang.lsp.server.feature.Completion;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.lsp.server.util.QueryAST;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class CompletionTest extends ExtendedBaseTest
{
  @Test
  public void getIntervallCompletions()
  {
    var sourceText = """
        example =>
          (1..2).
      """;

    SourceText.setText(uri1, sourceText);
    var expected = """
      map (${101:H} -> ${102:B})
      asString
      asList
      asStream
      forAll (${101:H} -> ${102:unit})
      contains ${1:e}
      size
      upper
      lower
      sizeOption
      add
      asString ${1:sep}
      asArray
      slice ${1:from} ${2:to}
      fold ${1:m}
      isEmpty
      count
      take ${1:n}
      drop ${1:n}
      takeWhile (${101:T} -> ${102:bool})
      dropWhile (${101:T} -> ${102:bool})
      cycle
      tails
      forWhile (${101:T} -> ${102:bool})
      before (${101:T} -> ${102:bool})
      filter (${101:T} -> ${102:bool})
      splitAt ${1:at}
      concatSequences ${1:s}
      mapSequence (${101:T} -> ${102:B})
      reduce ${1:init} (${201:R}, ${202:T} -> ${203:R})
      insert ${1:at} ${2:v}
      sort (${101:T}, ${102:T} -> ${103:bool})
      sortBy (${101:T} -> ${102:O})
      zip ${1:b} (${201:T}, ${202:U} -> ${203:V})
      hashCode""";
    var actual = Completion.getCompletions(params(uri1, 1, 9, TriggerCharacters.Dot))
      .getLeft()
      .stream()
      .map(x -> x.getInsertText())
      .collect(Collectors.toList());

    Arrays
      .stream(expected.split(System.lineSeparator()))
      .forEach(e -> {
        assertTrue("expected: " + e, actual.stream().anyMatch(a -> a.equals(e)));
      });
  }

  @Test
  public void getNestedLambdaCompletions()
  {
    var sourceText = """
      example =>
        a is
          b(c (i32 -> i32) -> i32 -> i32) is
        a.
            """;

    SourceText.setText(uri1, sourceText);
    var actual = Completion.getCompletions(params(uri1, 3, 4, TriggerCharacters.Dot))
      .getLeft()
      .get(0)
      .getInsertText();

    assertEquals("b ( (${10001:i32} -> ${10002:i32}) ->  (${10001:i32} -> ${10002:i32}))", actual);
  }


  @Test
  public void ListCompletions()
  {
    var sourceText = """
      fasta =>
        selectRandom() =>
          "a"

        randomFasta() =>
          (1..10)
            .map<string>(_ -> selectRandom())
            .
          """;

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 7, 7, TriggerCharacters.Dot));
    assertTrue(completions.getLeft().stream().anyMatch(x -> x.getLabel().startsWith("fold")));
  }

  @Test
  public void GenericWithConstraintCompletions()
  {
    var sourceText = """
      a =>
        b<T:float<T>>(c T) =>
          c.
                """;

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 2, 6, TriggerCharacters.Dot));
    assertTrue(completions.getLeft().stream().anyMatch(x -> x.getLabel().startsWith("bytes")));
  }

  @Test
  public void getFeatureCallCompletions()
  {
    var sourceText = """
      fasta =>
        selectRandom() =>
          "a"

        randomFasta() =>
          (1..10)
            .map<string>(_ -> selectRandom())
            .fold(strings.)
          """;
    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 7, 20, TriggerCharacters.Dot));
    assertTrue(completions.getLeft().stream().anyMatch(x -> x.getInsertText().equals("concat")));
  }

  private CompletionParams params(URI uri, int line, int character, TriggerCharacters triggerCharacter)
  {
    return new CompletionParams(LSP4jUtils.TextDocumentIdentifier(uri), new Position(line, character),
      new CompletionContext(CompletionTriggerKind.TriggerCharacter, triggerCharacter.toString()));
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
    assertEquals("num", QueryAST.FeatureAt(Cursor(uri1, 7, 22)).get().featureName().baseName());
    var completions = Completion.getCompletions(params(uri1, 7, 23, TriggerCharacters.Dot));
    assertTrue(completions.getLeft().size() > 100);
  }

  @Test
  public void CallCompletionsAtStdLib()
  {
    var sourceText = """
      HelloWorld is
        say "Hello, World!"
        (1..10).size.
      """;
    SourceText.setText(uri1, sourceText);
    assertTrue(QueryAST.CallCompletionsAt(Cursor(uri1, 2, 10)).count() > 0);
    assertTrue(
      QueryAST.CallCompletionsAt(Cursor(uri1, 2, 10)).anyMatch(f -> f.featureName().baseName().equals("sizeOption")));

    assertTrue(QueryAST.CallCompletionsAt(Cursor(uri1, 2, 15)).anyMatch(x -> x.featureName().baseName().equals("max")));
  }

  @Test
  public void CallCompletionsAt()
  {
    var sourceText = """
      HelloWorld is
        level1 is
          level2 is
            level3 is
        level1.
      """;
    SourceText.setText(uri1, sourceText);
    var completions = QueryAST
      .CallCompletionsAt(Cursor(uri1, 4, 9))
      .collect(Collectors.toList());

    assertEquals("level2", completions.get(0).featureName().baseName());
    assertTrue(completions.stream()
      .allMatch(f -> f.outer().featureName().baseName().equals("level1")
        || f.outer().featureName().baseName().equals("Object")));
  }


  @Test
  public void CompletionsAt()
  {
    var sourceText = """
      HelloWorld is
        innnerFeat is
        level1 is
          level2 is
            level3 is""";
    sourceText += System.lineSeparator() + "    ";
    SourceText.setText(uri1, sourceText);

    var completions = QueryAST
      .CompletionsAt(Cursor(uri1, 5, 4))
      .map(f -> f.qualifiedName())
      .collect(Collectors.toSet());

    var expectedCompletions = Arrays.stream(new String[]
      {
          "HelloWorld.level1.level2",
          "HelloWorld.level1",
          "HelloWorld.innnerFeat",
          "Function",
          "list",
          "Sequence",
          "array",
          "bool",
          "false",
          "true",
          "float",
          "i32",
          "marray",
          "outcome",
          "say",
          "stream",
          "string",
          "strings",
          "tuple",
      }).collect(Collectors.toSet());

    assertTrue(completions.containsAll(expectedCompletions));
  }

  @Test
  public void NoCompletionsForNumericLiteral()
  {
    var sourceText = """
      ex =>
        1.
        """;

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 1, 4, TriggerCharacters.Dot));
    assertTrue(completions.getLeft().isEmpty());
  }

  @Test
  public void NoCompletionsInString()
  {
    var sourceText = """
      ex =>
        "hallo "
        """;

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 1, 9, TriggerCharacters.Space));
    assertTrue(completions.getLeft().isEmpty());
  }


  @Test
  public void CompletionNumericLiteral()
  {
    var sourceText = """
      ex =>
        1""" + " ";

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 1, 4, TriggerCharacters.Space));
    assertTrue(completions.getLeft().stream().anyMatch(x -> x.getLabel().startsWith("infix +")));
  }

  @Test
  @Disabled // failing
  public void CompletionInfix()
  {
    var sourceText = """
      ex =>
        all := psSet 0..100
        removed := psSet 0..100:2
        (psSet (all.filter (x -> !(removed.contains x))))""" + " ";

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 3, 52, TriggerCharacters.Space));
    assertTrue(completions.getLeft().stream().anyMatch(x -> x.getLabel().startsWith("infix ⋃")));
  }

  @Test
  public void CompletionInLambdaArg()
  {
    var sourceText = """
      ex =>
        iter := bench (() -> random.) 1E3
        say "iterations per sec: $iter"
        """;

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 1, 30, TriggerCharacters.Dot));
    assertTrue(completions.getLeft().stream().anyMatch(x -> x.getLabel().startsWith("next_f64")));
  }

}
