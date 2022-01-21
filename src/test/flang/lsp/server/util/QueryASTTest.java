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
 * Source of class QueryASTTest
 *
 *---------------------------------------------------------------------*/


package test.flang.lsp.server.util;

import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.util.QueryAST;
import dev.flang.shared.FuzionParser;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class QueryASTTest extends ExtendedBaseTest
{
  @Test
  public void DeclaredFeaturesRecursive()
  {
    SourceText.setText(uri1, UnknownCall);
    SourceText.setText(uri2, HelloWorld);
    SourceText.setText(uri3, ManOrBoy);
    SourceText.setText(uri4, PythagoreanTriple);
    assertEquals(1, QueryAST.DeclaredFeaturesRecursive(uri1).count());
    assertEquals(1, QueryAST.DeclaredFeaturesRecursive(uri2).count());
    assertEquals(13, QueryAST.DeclaredFeaturesRecursive(uri3).count());
    assertEquals(3, QueryAST.DeclaredFeaturesRecursive(uri4).count());
  }

  @Test
  public void callAt()
  {
    var sourceText = """
      ex7 is
        (1..10).myCall()
              """;
    SourceText.setText(uri1, sourceText);
    var call = QueryAST.callAt(Cursor(uri1, 1, 17))
      .get();
    assertEquals("1.infix ..(10).myCall", call.toString());
  }

  @Test
  public void featureAtWithinFunction()
  {
    var sourceText = """
      ex is
        (1..10).forAll(num -> say num)
                  """;
    SourceText.setText(uri1, sourceText);

    assertEquals("infix ..", QueryAST.FeatureAt(Cursor(uri1, 1, 4))
      .get()
      .featureName()
      .baseName());

    assertEquals("forAll", QueryAST.FeatureAt(Cursor(uri1, 1, 10))
      .get()
      .featureName()
      .baseName());

    assertEquals("forAll", QueryAST.FeatureAt(Cursor(uri1, 1, 16))
      .get()
      .featureName()
      .baseName());

    assertEquals("say", QueryAST.FeatureAt(Cursor(uri1, 1, 24))
      .get()
      .featureName()
      .baseName());

    assertEquals("say", QueryAST.FeatureAt(Cursor(uri1, 1, 26))
      .get()
      .featureName()
      .baseName());
  }

  @Test
  public void featureAtWithinLoop()
  {
    var sourceText = """
      ex8 is
        for s in ["one", "two", "three"] do
          say "$s"
                """;
    SourceText.setText(uri1, sourceText);
    var feature = QueryAST.FeatureAt(Cursor(uri1, 2, 4))
      .get();
    assertEquals("say", feature.featureName().baseName());
  }


  @Test
  public void FeatureAt()
  {
    var sourceText = """
      myfeat is

        myi32 :i32 is

        print(x, y myi32, z i32) =>
          say "$x"
      """;
    SourceText.setText(uri1, sourceText);

    var feature = QueryAST.FeatureAt(Cursor(uri1, 5, 4)).get();
    assertEquals("say", feature.featureName().baseName());

    feature = QueryAST.FeatureAt(Cursor(uri1, 4, 8)).get();
    assertEquals("myi32", feature.featureName().baseName());

    feature = QueryAST.FeatureAt(Cursor(uri1, 4, 20)).get();
    assertEquals("i32", feature.featureName().baseName());
  }

  @Test
  // NYI failing...
  public void FeatureAtResultType()
  {
    var sourceText = """
      isGreaterThan(x, y i32) bool is
        x > y
      """;
    SourceText.setText(uri1, sourceText);

    var feature = QueryAST.FeatureAt(Cursor(uri1, 0, 24)).get();
    assertEquals("bool", feature.featureName().baseName());
  }

  @Test
  public void FeatureAtChoiceTypeMatch()
  {
    var sourceText = """
      choice_example2 is
        apple is
        pear is
        color(f apple | pear) =>
          match f
            apple  => "red"
            pear   => "green"
      """;
    SourceText.setText(uri1, sourceText);

    var feature = QueryAST.FeatureAt(Cursor(uri1, 4, 10)).get();
    assertEquals("f", feature.featureName().baseName());
  }

  @Test
  // NYI failing...
  public void FeatureAtChoiceTypeArgument()
  {
    var sourceText = """
      choice_example2 is
        apple is
        pear is
        color(f apple | pear) =>
          match f
            apple  => "red"
            pear   => "green"
            """;
    SourceText.setText(uri1, sourceText);

    var apple = QueryAST.FeatureAt(Cursor(uri1, 3, 10)).get();
    assertEquals("apple", apple.featureName().baseName());
    var pear = QueryAST.FeatureAt(Cursor(uri1, 3, 18)).get();
    assertEquals("pear", pear.featureName().baseName());
  }

  @Test
  // NYI failing
  public void FeatureAtInheritanceDeclaration()
  {
    var sourceText = "i33 : wrappingInteger<i33> is";
    SourceText.setText(uri1, sourceText);
    var wrappingInteger = QueryAST.FeatureAt(Cursor(uri1, 0, 6)).get();
    assertEquals("wrappingInteger", wrappingInteger.featureName().baseName());
  }

  @Test
  public void InFeatureManOrBoyExample()
  {
    SourceText.setText(uri1, ManOrBoy);
    assertEquals("a", QueryAST.InFeature(Cursor(uri1, 4, 3)).get().featureName().baseName());
  }

  @Test
  public void CallCompletionsAtStdLib()
  {
    var sourceText = """
      HelloWorld is
        say "Hello, World!"
        (1..10).size().
      """;
    SourceText.setText(uri1, sourceText);
    assertTrue(QueryAST.CallCompletionsAt(Cursor(uri1, 2, 10)).count() > 0);
    assertTrue(QueryAST.CallCompletionsAt(Cursor(uri1, 2, 10)).noneMatch(f -> f.featureName().baseName().length() < 3));
    assertTrue(
      QueryAST.CallCompletionsAt(Cursor(uri1, 2, 10)).anyMatch(f -> f.featureName().baseName().equals("sizeOption")));
    assertEquals(0, QueryAST.CallCompletionsAt(Cursor(uri1, 2, 17)).count());
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
  public void CalledFeature()
  {
    var sourceText = """
      HelloWorld is
        level1 is
          level2 is
            level3 is
        level1.
      """;
    SourceText.setText(uri1, sourceText);
    assertEquals("level1", QueryAST
      .CalledFeature(Cursor(uri1, 4, 9))
      .get()
      .featureName()
      .baseName());

  }

  @Test
  public void InFeature()
  {
    var sourceText = """
      HelloWorld is
        level1 is
          level2 is
            level3 is""";
    sourceText += System.lineSeparator() + "    ";
    SourceText.setText(uri1, sourceText);

    assertEquals("level1", QueryAST
      .InFeature(Cursor(uri1, 4, 4))
      .get()
      .featureName()
      .baseName());
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

    var expectedCompletions = """
      HelloWorld.level1.level2
      HelloWorld.innnerFeat
      HelloWorld.level1
      Cons
      FALSE
      Function
      HelloWorld
      InitArray
      Monoid
      Object
      Sequence
      Sequences
      Set
      TRUE
      analysis
      array
      array
      array2
      array3
      bitset
      bitsets
      bool
      choice
      codepoint
      codepoints
      complex
      complex
      complexes
      cons
      conststring
      debug
      debug
      debugLevel
      error
      f128
      f16
      f32
      f32s
      f64
      f64s
      false
      float
      floats
      fraction
      fraction
      fuzion
      hasEquals
      hasHash
      hasInterval
      hashMap
      hashMap
      i128
      i128
      i128s
      i16
      i16
      i16s
      i32
      i32
      i32s
      i64
      i64
      i64s
      i8
      i8
      i8s
      int
      integer
      java
      list
      lists
      map
      mapOf
      marray
      marray
      matrices
      matrix
      monad
      nil
      numOption
      numOption
      numeric
      numerics
      option
      option
      ordered
      orderedMap
      orderedMap
      outcome
      outcome
      partiallyOrdered
      pedantic
      psMap
      psMap
      psMap
      psSet
      psSet
      psSet
      quantors
      safety
      say
      say
      searchableList
      searchablelist
      setOf
      setOf
      some
      sortedArray
      sortedArray
      spit
      stdout
      stream
      string
      strings
      sum
      sys
      true
      tuple
      u128
      u128
      u128s
      u16
      u16
      u16s
      u32
      u32
      u32s
      u64
      u64
      u64s
      u8
      u8
      u8s
      uint
      unit
      void
      wrappingInteger
      wrappingIntegers
      yak
      Object.asString
      Object.hashCode
      Object.prefix $""";

    var completions = QueryAST
      .CompletionsAt(Cursor(uri1, 5, 4))
      .map(f -> f.qualifiedName())
      .collect(Collectors.joining("\n"));

    assertEquals(expectedCompletions, completions);

  }

  @Test
  public void RunBrokenSource()
  {
    SourceText.setText(uri1, UnknownCall);
    assertThrows(ExecutionException.class, () -> FuzionParser.Run(uri1, 10000));
    assertEquals(1, QueryAST.DeclaredFeaturesRecursive(uri1).count());
  }

}
