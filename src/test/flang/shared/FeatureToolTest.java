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

import dev.flang.shared.FeatureTool;
import dev.flang.shared.FuzionParser;
import dev.flang.shared.SourceText;

public class FeatureToolTest extends BaseTest
{
  @Test
  public void ASTbrokenSource()
  {
    SourceText.setText(uri1, UnknownCall);
    var ex = FuzionParser.Main(uri1);
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

        # first comment line
        # second comment line
        innerFeat is
          say "nothing"
      """;
    SourceText.setText(uri1, CommentExample);
    var innerFeature = FuzionParser
      .DeclaredFeatures(FuzionParser.Main(uri1))
      .findFirst()
      .orElseThrow();
    assertEquals("first comment line" + System.lineSeparator() + "second comment line",
      FeatureTool.CommentOf(innerFeature));
  }

  @Test
  public void CommentOfStdLibFeature()
  {
    var yak = DeclaredInUniverse("yak", 1);
    var actual = FeatureTool.CommentOf(yak);

    assertEquals(
      """
        A handy shortcut for stdout.print, output string representation of
        an object, do not add a line break at the end.

        The term 'yak' was taken from the expression 'Yakety Yak' as in the
        song by The Coasters.""" + System.lineSeparator(),
      actual);
  }

  @Test
  public void CommentOfRedef()
  {
    var psSet = DeclaredInUniverse("psSet", 2);
    var psSetasArray = FuzionParser.DeclaredFeatures(psSet)
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
    var array = DeclaredInUniverse("array", 2);
    assertEquals("array<T>(length i32, init Function<array.T, i32>) => array<array.T> : Object",
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

}
