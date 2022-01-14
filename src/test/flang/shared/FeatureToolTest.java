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
    var ex = FuzionParser.MainOrUniverse(uri1);
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
      .DeclaredFeatures(FuzionParser.MainOrUniverse(uri1))
      .findFirst()
      .orElseThrow();
    assertEquals("first comment line" + System.lineSeparator() + "second comment line",
      FeatureTool.CommentOf(innerFeature));
  }

  @Test
  public void CommentOfStdLibFeature()
  {
    var yak = DeclaredInUniverse("yak", 1);

    assertEquals(
      "A handy shortcut for stdout.print, output string representation of" + System.lineSeparator()
        + "an object, do not add a line break at the end." + System.lineSeparator(),
      FeatureTool.CommentOf(yak));
  }

}
