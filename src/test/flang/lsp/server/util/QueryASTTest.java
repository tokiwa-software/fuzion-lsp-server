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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import dev.flang.shared.ParserTool;
import dev.flang.shared.QueryAST;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class QueryASTTest extends ExtendedBaseTest
{
  @Test
  public void SelfAndDescandants()
  {
    SourceText.setText(uri1, HelloWorld);
    SourceText.setText(uri2, UnknownCall);
    SourceText.setText(uri3, ManOrBoy);
    SourceText.setText(uri4, PythagoreanTriple);
    assertEquals(1, QueryAST.SelfAndDescendants(uri1).count());
    assertEquals(1, QueryAST.SelfAndDescendants(uri2).count());
    assertEquals(11, QueryAST.SelfAndDescendants(uri3).count());
    assertEquals(3, QueryAST.SelfAndDescendants(uri4).count());
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
        (1..10).for_each(num -> say num)
                  """;
    SourceText.setText(uri1, sourceText);

    assertEquals("infix ..", QueryAST.FeatureAt(Cursor(uri1, 1, 4))
      .get()
      .featureName()
      .baseName());

    assertEquals("for_each", QueryAST.FeatureAt(Cursor(uri1, 1, 10))
      .get()
      .featureName()
      .baseName());

    assertEquals("for_each", QueryAST.FeatureAt(Cursor(uri1, 1, 18))
      .get()
      .featureName()
      .baseName());

    assertEquals("say", QueryAST.FeatureAt(Cursor(uri1, 1, 26))
      .get()
      .featureName()
      .baseName());

    assertEquals("say", QueryAST.FeatureAt(Cursor(uri1, 1, 28))
      .get()
      .featureName()
      .baseName());
  }

  @Test
  public void FeatureAtFixFeature()
  {
    var sourceText = Read("fuzion/lib/String.fz");
    SourceText.setText(uri1, sourceText);

    var atStartOfFeature = QueryAST.FeatureAt(Cursor(uri1, 56, 2)).get();
    var atStartOfBarename = QueryAST.FeatureAt(Cursor(uri1, 56, 8)).get();
    var atEndOrBarename = QueryAST.FeatureAt(Cursor(uri1, 56, 9)).get();
    assertEquals("infix *", atStartOfFeature.featureName().baseName());
    assertEquals("infix *", atStartOfBarename.featureName().baseName());
    assertEquals("infix *", atEndOrBarename.featureName().baseName());
  }

  @Test
  public void FeatureAtWithinLoop()
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
    assertEquals("x", feature.featureName().baseName());
    feature = QueryAST.FeatureAt(Cursor(uri1, 4, 9)).get();
    assertEquals("x", feature.featureName().baseName());

    feature = QueryAST.FeatureAt(Cursor(uri1, 4, 11)).get();
    assertEquals("y", feature.featureName().baseName());

    feature = QueryAST.FeatureAt(Cursor(uri1, 4, 20)).get();
    assertEquals("z", feature.featureName().baseName());

    feature = QueryAST.FeatureAt(Cursor(uri1, 4, 21)).get();
    assertEquals("z", feature.featureName().baseName());

    feature = QueryAST.FeatureAt(Cursor(uri1, 4, 22)).get();
    assertEquals("i32", feature.featureName().baseName());
  }

  @Test
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
  public void FeatureAtInheritanceDeclaration()
  {
    var sourceText = "i33 : wrapping_integer i33 is";
    SourceText.setText(uri1, sourceText);
    var wrappingInteger = QueryAST.FeatureAt(Cursor(uri1, 0, 6)).get();
    assertEquals("wrapping_integer", wrappingInteger.featureName().baseName());
  }

  @Test
  public void FeatureAtActualGenericOfInherited()
  {
    var sourceText = "i33 : wrapping_integer i33 is";
    SourceText.setText(uri1, sourceText);
    var wrappingInteger = QueryAST.FeatureAt(Cursor(uri1, 0, 24)).get();
    assertEquals("i33", wrappingInteger.featureName().baseName());
  }

  @Test
  public void FeatureAtActualGeneric()
  {
    var sourceText = """
      ex =>
        bench<unit> (() -> say "something") 1E3
            """;
    SourceText.setText(uri1, sourceText);
    var unitAtStart = QueryAST.FeatureAt(Cursor(uri1, 1, 8)).get();
    var unitAtEnd = QueryAST.FeatureAt(Cursor(uri1, 1, 12)).get();
    assertEquals("unit", unitAtStart.featureName().baseName());
    assertEquals("unit", unitAtEnd.featureName().baseName());
  }

  @Test
  public void InFeatureManOrBoyExample()
  {
    SourceText.setText(uri1, ManOrBoy);
    assertEquals("a", QueryAST.InFeature(Cursor(uri1, 4, 3)).get().featureName().baseName());
  }

  @Test
  public void TargetFeature()
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
      .TargetFeature(Cursor(uri1, 4, 9))
      .get()
      .featureName()
      .baseName());
  }

  @Test
  public void TargetFeatureArray()
  {
    var sourceText = """
      ex =>
        [1].""";
    SourceText.setText(uri1, sourceText);
    assertEquals("array", QueryAST
      .TargetFeature(Cursor(uri1, 1, 6))
      .get()
      .qualifiedName());
  }

  @Test
  public void TargetFeatureTuple()
  {
    var sourceText = """
      ex =>
        (1,2).""";
    SourceText.setText(uri1, sourceText);
    assertEquals("tuple", QueryAST
      .TargetFeature(Cursor(uri1, 1, 8))
      .get()
      .qualifiedName());
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
      .InFeature(Cursor(uri1, 3, 4))
      .get()
      .featureName()
      .baseName());

    assertEquals("level2", QueryAST
      .InFeature(Cursor(uri1, 3, 5))
      .get()
      .featureName()
      .baseName());

    assertEquals("level3", QueryAST
      .InFeature(Cursor(uri1, 3, 7))
      .get()
      .featureName()
      .baseName());
  }

  @Test
  @Disabled // failing
  public void RunBrokenSource()
  {
    SourceText.setText(uri1, UnknownCall);
    assertThrows(RuntimeException.class, () -> ParserTool.Run(uri1, 10000));
    assertEquals(1, QueryAST.SelfAndDescendants(uri1).count());
  }


  @Test
  public void TargetNumLiteral()
  {
    var sourceText = """
      ex =>
        (1.2).
        """;

    SourceText.setText(uri1, sourceText);
    var calledFeature = QueryAST.TargetFeature(Cursor(uri1, 1, 8));
    assertEquals("f64", calledFeature.get().qualifiedName());
  }

  @Test
  public void TargetString()
  {
    var sourceText = """
      ex =>
        "asdf".
        """;

    SourceText.setText(uri1, sourceText);
    var calledFeature = QueryAST.TargetFeature(Cursor(uri1, 1, 9));
    assertEquals("String", calledFeature.get().qualifiedName());
  }


  @Test
  public void TargetLambdaArgument()
  {
    var sourceText = """
      ex=>
        (1..2).map<f64> (x -> x.)
              """;

    SourceText.setText(uri1, sourceText);
    var calledFeature = QueryAST.TargetFeature(Cursor(uri1, 1, 26));
    assertEquals("i32", calledFeature.get().featureName().baseName());
  }


  @Test
  public void InStringComplex()
  {
    var sourceText = """
      ex =>
        a := "a"
        l := "l"
        "h{a}l$l o"
              """;
    SourceText.setText(uri1, sourceText);
    assertFalse(QueryAST.InString(Cursor(uri1, 3, 2)));
    assertTrue(QueryAST.InString(Cursor(uri1, 3, 3)));

    // {a}
    assertFalse(QueryAST.InString(Cursor(uri1, 3, 5)));
    assertFalse(QueryAST.InString(Cursor(uri1, 3, 6)));

    // l
    assertTrue(QueryAST.InString(Cursor(uri1, 3, 7)));

    // $l
    assertFalse(QueryAST.InString(Cursor(uri1, 3, 9)));

    assertTrue(QueryAST.InString(Cursor(uri1, 3, 11)));
    assertTrue(QueryAST.InString(Cursor(uri1, 3, 12)));
    assertFalse(QueryAST.InString(Cursor(uri1, 3, 13)));
  }

  @Test
  public void InStringSimple()
  {
    var sourceText = """
      ex =>
        "hallo "
          """;

    SourceText.setText(uri1, sourceText);
    assertFalse(QueryAST.InString(Cursor(uri1, 1, 2)));
    assertTrue(QueryAST.InString(Cursor(uri1, 1, 3)));
    assertTrue(QueryAST.InString(Cursor(uri1, 1, 9)));
    assertFalse(QueryAST.InString(Cursor(uri1, 1, 10)));
  }

  @Test
  public void TargetFeatureNone()
  {
    var sourceText = """
      ex =>
        a := "first"
        b""" + " ";
    SourceText.setText(uri1, sourceText);
    var calledFeature = QueryAST.TargetFeature(Cursor(uri1, 2, 4));
    assertTrue(calledFeature.isEmpty());
  }


}
