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
 * Source of class FeatureToolTest
 *
 *---------------------------------------------------------------------*/


package test.flang.shared;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.flang.ast.AbstractFeature;
import dev.flang.shared.ASTWalker;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.ParserTool;
import dev.flang.shared.SourceText;
import dev.flang.util.SourcePosition;

public class FeatureToolTest extends BaseTest
{
  @Test
  public void ASTbrokenSource()
  {
    SourceText.setText(uri1, UnknownCall);
    var ex = ParserTool.TopLevelFeatures(uri1).findFirst().get();
    var ast = FeatureTool.AST(ex);
    assertTrue(ast.contains("Call:hasInterval"));
    assertTrue(ast.contains("Call:called feature unknown"));
  }

  @Test
  public void CommentOf()
  {
    var CommentExample = """
      outerFeat is
        # this should not be part of comment

        # first comment line.
        # second comment line.
        innerFeat is
          say "nothing"
      """;
    SourceText.setText(uri1, CommentExample);
    var innerFeature = ParserTool
      .DeclaredFeatures(ParserTool.TopLevelFeatures(uri1).findFirst().get())
      .findFirst()
      .orElseThrow();
    assertEquals("first comment line." + System.lineSeparator() + "second comment line.",
      FeatureTool.CommentOf(innerFeature));
  }

  @Test
  public void CommentOfStdLibFeature()
  {
    var yak = DeclaredInUniverse("yak", 1);
    var actual = FeatureTool.CommentOf(yak);

    assertEquals(
      """
        yak -- shortcut for io.out.print

        A handy shortcut for io.out.print, output string representation of
        an object, do not add a line break at the end.

        The term 'yak' was taken from the expression 'Yakety Yak' as in the
        song by The Coasters.""" + System.lineSeparator(),
      actual);
  }

  @Test
  public void CommentOfRedef()
  {
    var psSet = DeclaredInUniverse("psSet", 3);
    var psSetasArray = ParserTool.DeclaredFeatures(psSet)
      .filter(f -> f.featureName().baseName().equals("asArray"))
      .findFirst()
      .get();
    var actual = FeatureTool.CommentOf(psSetasArray);

    assertEquals(
      """
        create a sorted array from the elements of this set

        redefines Sequence.asArray:
        collect the contents of this Sequence into an array""" + System.lineSeparator(),
      actual);
  }

  @Test
  public void ToLabel()
  {
    var array = DeclaredInUniverse("array", 5);
    assertEquals(
      "array<T>(T Object, internalArray fuzion.sys.array<array.T>, _ unit, _ unit, _ unit) => array<array.T> : Sequence<T>",
      FeatureTool.ToLabel(array));
  }

  @Test
  public void CallGraph()
  {
    var f = DeclaredInUniverse("yak", 1);
    var graph = FeatureTool.CallGraph(f);

    var expected = List.of("""
      "yak" -> "io";
      """, """
      .call" -> "yak";
      """);

    assertTrue(expected.stream().allMatch(e -> graph.contains(e)));
  }

  @Test
  public void BaseNamePositionTest()
  {
    var ex = """
      i33(val i33) : wrappingInteger<i33>, hasInterval<i33>, i33s is
        redef  prefix  -° i33 is intrinsic
        infix  ⋃ (other i33) is abstract
        private  div (other i33) i33 is intrinsic
        lamda_fun (i33,i33) -> i32 := (first_lamdba_arg, second_lambda_arg) -> 3
        feat (arg_one, arg_two i33) is
        redef max i33 is

      i33s is
        """;
    ;
    SourceText.setText(uri1, ex);

    assertEquals(18, GetFeature("-°")._column);
    assertEquals(10, GetFeature("⋃")._column);
    assertEquals(12, GetFeature("div")._column);
    assertEquals(34, GetFeature("first_lamdba_arg")._column);
    assertEquals(52, GetFeature("second_lambda_arg")._column);
    assertEquals(9, GetFeature("arg_one")._column);
    assertEquals(18, GetFeature("arg_two")._column);
    assertEquals(9, GetFeature("max")._column);
  }

  private SourcePosition GetFeature(String name)
  {
    var first_lambda_arg = FeatureTool.BareNamePosition(ASTWalker
      .Traverse(uri1)
      .filter(x -> x.getKey() instanceof AbstractFeature af && af.featureName().baseName().contains(name))
      .map(x -> (AbstractFeature) x.getKey())
      .findFirst()
      .get());
    return first_lambda_arg;
  }

}
