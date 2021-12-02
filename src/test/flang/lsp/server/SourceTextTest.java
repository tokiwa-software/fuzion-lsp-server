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
 * Source of class SourceTestText
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.SourceText;
import dev.flang.lsp.server.util.Bridge;
import dev.flang.lsp.server.util.FuzionParser;

class SourceTextTest extends BaseTest
{
  @Test
  public void StringAt_multi_line()
  {

    SourceText.setText(uri1, LoremIpsum);
    var text = SourceText.getText(uri1, new Range(new Position(1, 3), new Position(2, 4)));
    assertEquals(
      "enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat."
        + System.lineSeparator() + "Duis",
      text);
  }

  @Test
  public void StringAt_single_line()
  {

    SourceText.setText(uri1, LoremIpsum);
    var text = SourceText.getText(uri1, new Range(new Position(1, 3), new Position(1, 23)));
    assertEquals("enim ad minim veniam", text);
  }


  @Test
  public void SourceText()
  {
    var CommentExample = """
      myFeat is
      """;
    SourceText.setText(uri1, CommentExample);
    var myFeatIs = FuzionParser.MainOrUniverse(uri1);
    var sourceText = SourceText.getText(Bridge.ToTextDocumentPosition(myFeatIs.pos()));
    assertEquals(true, sourceText.contains("myFeat is"));
  }

  @Test
  public void SourceTextStdLibFile()
  {
    var CommentExample = """
      myFeat is
      """;
    SourceText.setText(uri1, CommentExample);
    var yak = FuzionParser
      .DeclaredFeatures(FuzionParser.universe(uri1))
      .filter(f -> f.featureName().baseName().endsWith("yak"))
      .findFirst()
      .get();
    var sourceText = SourceText.getText(Bridge.ToTextDocumentPosition(yak.pos()));
    assertEquals(true, sourceText.contains("yak(s ref Object) => stdout.print(s)"));
  }



}

