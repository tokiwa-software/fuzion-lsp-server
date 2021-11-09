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

import dev.flang.ast.Call;
import dev.flang.lsp.server.SourceText;
import dev.flang.lsp.server.util.FuzionParser;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.lsp.server.util.QueryAST;
import test.flang.lsp.server.BaseTest;

public class QueryASTTest extends BaseTest
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
  public void allOf()
  {
    SourceText.setText(uri1, HelloWorld);
    assertEquals("HelloWorld", QueryAST
      .BaseFeature(uri1)
      .get()
      .featureName()
      .baseName());
    assertEquals("say", QueryAST
      .AllOf(uri1, Call.class)
      .filter(call -> uri1.equals(FuzionParser.getUri(call.pos())))
      .findFirst()
      .get()
      .calledFeature()
      .featureName()
      .baseName());
  }

  @Test
  public void callAt()
  {
    var sourceText = """
      ex7 is
        (1..10).myCall()
              """;
    SourceText.setText(uri1, sourceText);
    var call = QueryAST.callAt(LSP4jUtils.TextDocumentPositionParams(uri1, 1, 17))
      .get();
    assertEquals("myCall", call.name);
  }

  @Test
  public void featureAtWithinFunction()
  {
    var sourceText = """
      ex is
        (1..10).forAll(num -> say num)
                  """;
    SourceText.setText(uri1, sourceText);

    assertEquals("infix ..", QueryAST.FeatureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 1, 4))
      .get()
      .featureName()
      .baseName());

    assertEquals("forAll", QueryAST.FeatureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 1, 10))
      .get()
      .featureName()
      .baseName());

    assertEquals("forAll", QueryAST.FeatureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 1, 16))
      .get()
      .featureName()
      .baseName());

    assertEquals("say", QueryAST.FeatureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 1, 24))
      .get()
      .featureName()
      .baseName());

    assertEquals("say", QueryAST.FeatureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 1, 26))
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
    var feature = QueryAST.FeatureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 2, 4))
      .get();
    assertEquals("say", feature.featureName().baseName());
  }


  @Test
  public void featureAt()
  {
    var sourceText = """
      myfeat is

        myi32 :i32 is

        print(x, y myi32, z i32) =>
          say "$x"
      """;
    SourceText.setText(uri1, sourceText);

    var feature = QueryAST.FeatureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 5, 4)).get();
    assertEquals("say", feature.featureName().baseName());

    feature = QueryAST.FeatureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 4, 8)).get();
    assertEquals("myi32", feature.featureName().baseName());

    feature = QueryAST.FeatureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 4, 20)).get();
    assertEquals("i32", feature.featureName().baseName());
  }

}
