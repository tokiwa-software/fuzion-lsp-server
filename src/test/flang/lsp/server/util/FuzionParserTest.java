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
 * Source of class FuzionParserTest
 *
 *---------------------------------------------------------------------*/



package test.flang.lsp.server.util;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.SourceText;
import dev.flang.lsp.server.Util;
import dev.flang.lsp.server.util.FeatureTool;
import dev.flang.lsp.server.util.FuzionParser;
import dev.flang.lsp.server.util.QueryAST;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;
import test.flang.lsp.server.BaseTest;

public class FuzionParserTest extends BaseTest
{
  @Test
  public void endOfFeature()
  {
    var sourceText = """
      ex7 is
        (1..10).forAll()
              """;
    SourceText.setText(uri1, sourceText);
    var endOfFeature = FuzionParser.endOfFeature(FuzionParser.main(uri1).get());
    assertEquals(2, endOfFeature._line);
    assertEquals(19, endOfFeature._column);
  }

  @Test
  public void EndOfFeatureLambdaDefinition(){
    SourceText.setText(uri1, ManOrBoy);
    var feature_b = FeatureTool.DeclaredFeaturesRecursive(FuzionParser.main(uri1).get())
      .filter(x -> x.featureName().baseName().equals("b")).findFirst().get();
    var endOfFeature = FuzionParser.endOfFeature(feature_b);
    assertEquals(4, endOfFeature._line);
    assertEquals(52, endOfFeature._column);

  }

  @Test
  public void noSourceText()
  {
    var sourceText = """
      """;
    SourceText.setText(uri1, sourceText);
    var f = FuzionParser.main(uri1);
    assertEquals("#universe", f.get().qualifiedName());
  }

  @Test
  public void declaredFeatures()
  {
    var sourceText = """
        example is
          childFeat1 is
            grandChild1 is
            grandChild2 is
          childFeat2 is
            grandChild3 is
      """;
    SourceText.setText(uri1, sourceText);
    var f = FuzionParser.main(uri1);
    var df = FuzionParser.DeclaredFeatures(f.get()).collect(Collectors.toList());
    assertEquals(2, df.size());
    assertTrue(df.stream().anyMatch(x -> x.featureName().baseName().equals("childFeat1")));
  }


  @Test
  public void declaredFeaturesUniverse()
  {
    SourceText.setText(uri1, HelloWorld);
    assertTrue(FuzionParser.DeclaredFeatures(FuzionParser.universe(uri1)).count() > 10);
  }

  @Test
  public void getMainFeatureTest()
  {
    SourceText.setText(uri1, """
      HelloWorld is
        say "Hello World!"
                  """);
    var mainFeature = FuzionParser.main(uri1);
    assertEquals(0, FuzionParser.Errors(uri1).count());
    assertEquals(true, mainFeature.isPresent());
    assertEquals("HelloWorld", mainFeature.get().featureName().baseName());
    assertEquals(uri1, FuzionParser.getUri(mainFeature.get().pos()));
  }

  @Test
  public void getMainFeatureBrokenSourceCodeTest()
  {
    SourceText.setText(uri1, """
      factors1 is

        (1..10).forAll(x -> say "sadf")
          (1..n) & (x -> n %% x) | fun print
        say

        (1..n) | m ->
          say("factors of $m: " +
              ((1..m) & (x ->  m %% x)))


                  """);
    var mainFeature = FuzionParser.main(uri1);
    assertEquals(true, FuzionParser.Errors(uri1).count() > 0);
    assertEquals(true, mainFeature.isPresent());
  }

  @Test
  public void getUriStdLibFile()
  {
    var uri = FuzionParser.getUri(new SourcePosition(new SourceFile(Path.of("fuzion/build/lib/yak.fz")), 0, 0));
    assertEquals(Util.toURI(Path.of("./").normalize().toUri().toString() + "fuzion/build/lib/yak.fz"), uri);
  }

  @Test
  public void RunBrokenSource()
  {
    SourceText.setText(uri1, UnknownCall);
    assertThrows(ExecutionException.class, () -> FuzionParser.Run(uri1, 10000));
    assertEquals(1, QueryAST.DeclaredFeaturesRecursive(uri1).count());
  }


}
