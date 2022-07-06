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
 * Source of class ParserToolTest
 *
 *---------------------------------------------------------------------*/



package test.flang.shared;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.flang.parser.Lexer;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.IO;
import dev.flang.shared.ParserTool;
import dev.flang.shared.SourceText;
import dev.flang.shared.Util;
import dev.flang.util.Errors;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

public class ParserToolTest extends BaseTest
{
  @Test
  public void EndOfFeature()
  {
    var sourceText = """
      ex7 is
        (1..10).forAll()"""
      + System.lineSeparator() + " ";
    SourceText.setText(uri1, sourceText);
    var endOfFeature = ParserTool.endOfFeature(ParserTool.TopLevelFeatures(uri1).findFirst().get());
    assertEquals(3, endOfFeature._line);
    assertEquals(2, endOfFeature._column);
  }

  @Test
  public void EndOfFeatureLambdaDefinition()
  {
    SourceText.setText(uri1, ManOrBoy);
    var feature_b = FeatureTool.SelfAndDescendants(ParserTool.TopLevelFeatures(uri1).findFirst().get())
      .filter(x -> x.featureName().baseName().equals("b"))
      .findFirst()
      .get();
    var endOfFeature = ParserTool.endOfFeature(feature_b);
    assertEquals(5, endOfFeature._line);
    assertEquals(4, endOfFeature._column);

  }

  @Test
  public void EndOfFeatureArgument()
  {
    SourceText.setText(uri1, ManOrBoy);
    var feature_x1 = FeatureTool.SelfAndDescendants(ParserTool.TopLevelFeatures(uri1).findFirst().get())
      .filter(x -> x.featureName().baseName().equals("x1"))
      .findFirst()
      .get();
    var endOfFeature = ParserTool.endOfFeature(feature_x1);
    assertEquals(3, endOfFeature._line);
    assertEquals(14, endOfFeature._column);
  }

  @Test
  public void EndOfFeatureStdLib()
  {
    var yak = DeclaredInUniverse("yak", 1);
    assertEquals(35, ParserTool.endOfFeature(yak)._line);
    assertEquals(1, ParserTool.endOfFeature(yak)._column);
  }


  @Test
  public void EndOfFeatureNested()
  {
    var sourceText = """
      HelloWorld is
        level1 is
          level2 is
            level3 is"""
      + System.lineSeparator() + "    ";
    SourceText.setText(uri1, sourceText);

    var level2 = FeatureTool.SelfAndDescendants(ParserTool.TopLevelFeatures(uri1).findFirst().get())
      .filter(f -> f.qualifiedName().equals("HelloWorld.level1.level2"))
      .findFirst()
      .get();

    assertEquals(5, ParserTool.endOfFeature(level2)._line);
    assertEquals(5, ParserTool.endOfFeature(level2)._column);
  }


  @Test
  public void noSourceText()
  {
    var sourceText = """
      """;
    SourceText.setText(uri1, sourceText);
    var f = ParserTool.Universe(uri1);
    assertEquals("#universe", f.qualifiedName());
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
    var f = ParserTool.TopLevelFeatures(uri1).findFirst().get();
    var df = ParserTool.DeclaredFeatures(f).collect(Collectors.toList());
    assertEquals(2, df.size());
    assertTrue(df.stream().anyMatch(x -> x.featureName().baseName().equals("childFeat1")));
  }


  @Test
  public void declaredFeaturesUniverse()
  {
    SourceText.setText(uri1, HelloWorld);
    assertTrue(ParserTool.DeclaredFeatures(ParserTool.Universe(uri1)).count() > 10);
  }

  @Test
  public void getMainFeatureTest()
  {
    SourceText.setText(uri1, """
      HelloWorld is
        say "Hello World!"
                  """);
    var mainFeature = ParserTool.TopLevelFeatures(uri1).findFirst().get();
    assertEquals(0, ParserTool.Errors(uri1).count());
    assertEquals("HelloWorld", mainFeature.featureName().baseName());
    assertEquals(uri1, ParserTool.getUri(mainFeature.pos()));
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
    assertDoesNotThrow(() -> ParserTool.TopLevelFeatures(uri1).findFirst().get());
    assertEquals(true, ParserTool.Errors(uri1).count() > 0);
  }

  @Test @Timeout(value = 10000) @Tag("TAG")
  public void Fuzz()
  {
    Errors.MAX_ERROR_MESSAGES = Integer.MAX_VALUE;
    var rand = new Random();
    var keywords = Arrays.stream(Lexer.Token._keywords).map(x -> x.toString()).collect(Collectors.toList());
    var others = List.of("(",")","{","}","\"", "$","1", ".", ",");
    var ws = List.of(" ", "\n");

    for (int i = 0; i < 100; i++) {
      Stream<String> randomIdents = IntStream.range(0, 20).mapToObj(x -> getRandomIdent(rand));
      List<String> sourceWords = Stream.concat(randomIdents, Stream.concat(keywords.stream(), others.stream())).collect(Collectors.toList());
      var text = rand
        .ints(20, 0, sourceWords.size())
        .<String>mapToObj(idx -> sourceWords.get(idx) + ws.get(rand.nextInt(2)))
        .collect(Collectors.joining());

      System.err.println("====");
      System.err.println(text);
      System.err.println("====");

      SourceText.setText(uri1, text);
      ParserTool.TopLevelFeatures(uri1);
    }
  }

  private String getRandomIdent(Random rand)
  {
    return rand.ints()
      .filter(x -> Character.isValidCodePoint(x))
      .limit(10)
      .mapToObj(x -> Character.toString(x))
      .collect(Collectors.joining());
  }

  @Test
  public void getUriStdLibFile()
  {
    var uri = ParserTool.getUri(new SourcePosition(new SourceFile(Path.of("fuzion/build/lib/yak.fz")), 0, 0));
    assertEquals(Util.toURI(Path.of("./").normalize().toUri().toString() + "fuzion/build/lib/yak.fz"), uri);
  }

  @Test
  public void UniverseOfStdLibFile()
  {
    var uri = ParserTool.getUri(new SourcePosition(new SourceFile(Path.of("fuzion/build/lib/yak.fz")), 0, 0));
    assertTrue(ParserTool.Universe(uri).isUniverse());
  }

  @Test
  public void WarningsErrorsOfStdLibFile()
  {
    var uri = ParserTool.getUri(new SourcePosition(new SourceFile(Path.of("fuzion/build/lib/yak.fz")), 0, 0));
    assertTrue(ParserTool.Warnings(uri).findAny().isEmpty());
    assertTrue(ParserTool.Errors(uri).findAny().isEmpty());
  }


}
