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
import org.junit.jupiter.api.Tag;

import dev.flang.lsp.server.feature.Completion;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.shared.QueryAST;
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
      map (${101:has_interval} -> ${102:B})
      as_string
      as_list
      as_stream
      for_each (${101:has_interval} -> ${102:unit})
      contains ${1:e}
      size
      upper
      lower
      size_option
      add
      as_string ${1:sep}
      as_array
      slice ${1:from} ${2:to}
      fold ${1:m}
      is_empty
      count
      take ${1:n}
      drop ${1:n}
      take_while (${101:T} -> ${102:bool})
      drop_while (${101:T} -> ${102:bool})
      cycle
      tails
      for_while (${101:T} -> ${102:bool})
      before (${101:T} -> ${102:bool})
      filter (${101:T} -> ${102:bool})
      split_at ${1:at}
      concat_sequences ${1:s}
      map_sequence (${101:T} -> ${102:B})
      insert ${1:at} ${2:v}
      sort (${101:T}, ${102:T} -> ${103:bool})
      sort_by (${101:T} -> ${102:O})
      zip ${1:b} (${201:T}, ${202:U} -> ${203:V})
      hash_code""";
    var actual = Completion.getCompletions(params(uri1, 1, 9, Completion.TriggerCharacters.Dot))
      .map(x -> x.getInsertText())
      .collect(Collectors.toList());

    Arrays
      .stream(expected.split(System.lineSeparator()))
      .forEach(e -> {
        assertTrue(actual.stream().anyMatch(a -> a.equals(e)), () -> "expected: " + e);
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
    var actual = Completion.getCompletions(params(uri1, 3, 4, Completion.TriggerCharacters.Dot))
      .findFirst()
      .get()
      .getInsertText();

    assertEquals("b ( (${10001:i32} -> ${10002:i32}) ->  (${10001:i32} -> ${10002:i32}))", actual);
  }

  @Test
  public void CompletionInChainedCalls()
  {
    var sourceText = """
      example =>
        envir.args.drop(2).first.parse_i32.val
            """;

    SourceText.setText(uri1, sourceText);
    var actual = Completion.getCompletions(params(uri1, 1, 27, Completion.TriggerCharacters.Dot));

    assertTrue(actual.anyMatch(ci -> ci.getInsertText().equals("parse_i32")));
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
            .map (_ -> selectRandom())
            .
          """;

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 7, 7, Completion.TriggerCharacters.Dot));
    assertTrue(completions.anyMatch(x -> x.getLabel().startsWith("fold")));
  }

  @Test
  public void GenericWithConstraintCompletions()
  {
    var sourceText = """
      a =>
        b(T type : Sequence T, c T) =>
          c.
                """;

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 2, 6, Completion.TriggerCharacters.Dot));
    assertTrue(completions.anyMatch(x -> x.getLabel().startsWith("zip")));
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
            .map String (_ -> selectRandom())
            .fold(String.type.)
          """;
    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 7, 24, Completion.TriggerCharacters.Dot));
    assertTrue(completions.anyMatch(x -> x.getInsertText().equals("concat")));
  }

  private CompletionParams params(URI uri, int line, int character, Completion.TriggerCharacters triggerCharacter)
  {
    var result = new CompletionParams(
      LSP4jUtils.TextDocumentIdentifier(uri),
      new Position(line, character),
      new CompletionContext(CompletionTriggerKind.Invoked));
    if (triggerCharacter != null)
      {
        result.setContext(new CompletionContext(CompletionTriggerKind.TriggerCharacter, triggerCharacter.toString()));
      }
    return result;
  }

  @Test
  void CompletionInMatchOfPrecondition()
  {
    var sourceText = """
      towers is

        num_or_nil i32|nil := nil
        s
          pre
            match num_or_nil
              nil => true
              n i32 => n.
          is""";
    SourceText.setText(uri1, sourceText);
    assertEquals("n", QueryAST.FeatureAt(Cursor(uri1, 7, 18)).get().featureName().baseName());
    var completions = Completion.getCompletions(params(uri1, 7, 19, Completion.TriggerCharacters.Dot));
    assertTrue(completions.count() > 10);
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
    assertTrue(QueryAST.DotCallCompletionsAt(Cursor(uri1, 2, 10)).count() > 0);
    assertTrue(
      QueryAST.DotCallCompletionsAt(Cursor(uri1, 2, 10)).anyMatch(f -> f.featureName().baseName().equals("size_option")));

    assertTrue(QueryAST.DotCallCompletionsAt(Cursor(uri1, 2, 15)).anyMatch(x -> x.featureName().baseName().equals("max")));
  }

  @Test
  public void CompletionOfLoopVariable()
  {
    var sourceText = """
      ex =>
        ball is
          color is

        balls := array 10 (i -> ball)

        for b in balls do
          b.
      """;

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 7, 6, Completion.TriggerCharacters.Dot));
    assertTrue(completions.anyMatch(x -> x.getInsertText().equals("color")));
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
      .DotCallCompletionsAt(Cursor(uri1, 4, 9))
      .collect(Collectors.toList());

    assertEquals("level2", completions.get(0).featureName().baseName());
    assertTrue(completions.stream()
      .allMatch(f -> f.outer().featureName().baseName().equals("level1")
        || f.outer().featureName().baseName().equals("Any")));
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
          "String",
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
    var completions = Completion.getCompletions(params(uri1, 1, 4, Completion.TriggerCharacters.Dot));
    assertTrue(completions.count() == 0);
  }

  @Test
  public void DotTypeCompletions()
  {
    var sourceText = """
      ex =>
        String.type.
        """;

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 1, 14, Completion.TriggerCharacters.Dot));
    assertTrue(completions.anyMatch(x -> x.getInsertText().equals("concat")));
  }

  @Test
  public void NoCompletionsInString()
  {
    var sourceText = """
      ex =>
        "hallo "
        """;

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 1, 9, Completion.TriggerCharacters.Space));
    assertTrue(completions.count() == 0);
  }

  @Test
  public void CompletionsInIncompleteFeature()
  {
    var sourceText = """
      ex =>
        my_feat(a i32) """;

    SourceText.setText(uri1, sourceText);

    var completions = Completion.getCompletions(params(uri1, 1, 12, Completion.TriggerCharacters.Space));
    assertTrue(completions.anyMatch(x -> x.getInsertText().equals("i32")));
    completions = Completion.getCompletions(params(uri1, 1, 12, Completion.TriggerCharacters.Space));
    // analysis does not define a type
    assertFalse(completions.anyMatch(x -> x.getInsertText().equals("analysis")));

    completions = Completion.getCompletions(params(uri1, 1, 16, null));
    assertTrue(completions.count() == 0);

    completions = Completion.getCompletions(params(uri1, 1, 17, Completion.TriggerCharacters.Space));
    assertTrue(completions.findFirst().get().getInsertText().equals("is"));
  }


  @Test
  public void CompletionNumericLiteral()
  {
    var sourceText = """
      ex =>
        1""" + " ";

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 1, 4, Completion.TriggerCharacters.Space));
    assertTrue(completions.anyMatch(x -> x.getLabel().startsWith("infix +")));
  }


  @Test
  public void CompletionNoneExistant()
  {
    var sourceText = """
      main =>
        counts := mapOf string u64 [] []
        stdin.""";

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 2, 8, Completion.TriggerCharacters.Dot));
    assertEquals(2, completions.count());
  }

  @Test
  @Disabled // failing
  public void CompletionTuple()
  {
    var sourceText = """
      main =>
        my_tupl := (1,2,3,4)
        my_tupl.values.""";

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 2, 17, Completion.TriggerCharacters.Dot));
    assertTrue(completions.anyMatch(x -> x.getLabel().startsWith("3")));
  }

  @Test
  public void CompletionTupleInBraces()
  {
    var sourceText = """
      main : io.stdin is
        my_tupl := (1,2,3,4)
        say "{my_tupl.}

        unit

      """;

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 2, 16, Completion.TriggerCharacters.Dot));
    assertTrue(completions.anyMatch(x -> x.getLabel().startsWith("values")));
  }

  @Test
  public void CompletionInfix()
  {
    var sourceText = """
      ex =>
        all := (container.ps_set i32).type.from_sequence 0..100
        removed := (container.ps_set i32).type.from_sequence 0..100:2
        ((container.ps_set i32).type.from_sequence (all.filter (x -> !(removed.contains x))))""" + " ";

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 3, 88, Completion.TriggerCharacters.Space));
    assertTrue(completions.anyMatch(x -> x.getLabel().startsWith("infix ∪")));
  }

  @Test
  @Disabled // NYI ParserTool.endOfFeature is broken
  public void CompletionInLambdaArg()
  {
    var sourceText = """
      ex =>
        iter := bench (() -> random.) 1E3
        say "iterations per sec: $iter"
        """;

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 1, 30, Completion.TriggerCharacters.Dot));
    assertTrue(completions.anyMatch(x -> x.getLabel().startsWith("next_f64")));
  }

  @Test
  public void CompletionInActualArg()
  {
    var sourceText = """
      map_of(K type : has_total_order, V type, kvs array (tuple K V)) container.Map K V is
        container.ps_map kvs. kvs.length kvs.length+1
        """;

    SourceText.setText(uri1, sourceText);
    var completions = Completion.getCompletions(params(uri1, 1, 23, Completion.TriggerCharacters.Dot));
    assertTrue(completions.anyMatch(x -> x.getLabel().startsWith("internal_array")));
  }

}
