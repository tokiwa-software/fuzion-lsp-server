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
 * Source of class FuzionHelperTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Call;
import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.SourceText;
import dev.flang.lsp.server.Util;
import dev.flang.lsp.server.util.Bridge;
import dev.flang.lsp.server.util.FuzionLexer;
import dev.flang.lsp.server.util.FuzionParser;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.parser.Lexer.Token;

class FuzionHelperTest extends BaseTest
{
  private static final String LoremIpsum =
    """
      Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
      Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
      Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.
      Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum
          """;

  private static final String ManOrBoy = """
    man_or_boy is

      a(k i32, x1, x2, x3, x4, x5 () -> i32) i32 is
        b => set k := k - 1; a k (() -> b) x1 x2 x3 x4
        if k <= 0 x4() + x5() else b

      K(n i32) () -> i32 is () -> n

      (0..10) | n ->
        say \"manorboy a($n) = {a n (K 1) (K -1) (K -1) (K 1) (K 0)}\"
            """;

  private static final String HelloWorld = """
      HelloWorld is
        say "Hello World!"
    """;

  private static final String PythagoreanTriple = """
      pythagoreanTriple is
        cₘₐₓ := 100    # max length of hypothenuse

        # iterate over all interesting real/imag pairs while c<max
        for real in 1..cₘₐₓ do
          for
            # imag >= real is not interesting, v².real or v².imag would be negative
            # so we end imag at real-1
            imag in 1..real-1

            v := complex real imag
            v² := v * v
            f := v².real.gcd v².imag  # 1 or 2 (if real+imag is even)
            a := v².real / f
            b := v².imag / f
            c := v .abs² / f
          while c < cₘₐₓ
            if real.gcd imag = 1  # filter duplicates
              say "{a}² + {b}² = {c}² = {a*a} + {b*b} = {c*c}"
    """;


  @Test
  void NextTokenOfType_at_start()
  {
    var foundToken = FuzionLexer.nextTokenOfType(", aösldkjf", Util.HashSetOf(Token.t_comma));

    assertEquals(1, foundToken.start()._column);
    assertEquals(2, foundToken.end()._column);
    assertEquals(",", foundToken.text());
  }

  @Test
  void NextTokenOfType_at_end()
  {
    var foundToken = FuzionLexer.nextTokenOfType("1234,", Util.HashSetOf(Token.t_comma));

    assertEquals(5, foundToken.start()._column);
    assertEquals(6, foundToken.end()._column);
    assertEquals(",", foundToken.text());
  }

  @Test
  void NextToken_a()
  {

    SourceText.setText(uri1, ManOrBoy);

    var nextToken =
      FuzionLexer.rawTokenAt(LSP4jUtils.TextDocumentPositionParams(uri1, 2, 2));
    assertEquals("a", nextToken.text());
    assertEquals(4, nextToken.end()._column);
  }

  @Test
  void NextToken_i32()
  {

    SourceText.setText(uri1, ManOrBoy);

    var nextToken =
      FuzionLexer.rawTokenAt(LSP4jUtils.TextDocumentPositionParams(uri1, 6, 7));
    assertEquals("i32", nextToken.text());
    assertEquals(10, nextToken.end()._column);
  }

  @Test
  void EndOfToken_man_or_boy()
  {
    SourceText.setText(uri1, ManOrBoy);

    var endOfToken = FuzionLexer.endOfToken(uri1, new Position(0, 0));
    assertEquals(10, endOfToken.getCharacter());
    assertEquals(0, endOfToken.getLine());

  }

  @Test
  void EndOfToken_i32()
  {
    SourceText.setText(uri1, ManOrBoy);

    var endOfToken = FuzionLexer.endOfToken(uri1, new Position(2, 6));
    assertEquals(9, endOfToken.getCharacter());
    assertEquals(2, endOfToken.getLine());
  }

  @Test
  void EndOfToken_opening_brace()
  {
    SourceText.setText(uri1, ManOrBoy);

    var endOfToken = FuzionLexer.endOfToken(uri1, new Position(2, 3));
    assertEquals(4, endOfToken.getCharacter());
    assertEquals(2, endOfToken.getLine());
  }

  @Test
  void StringAt_multi_line()
  {

    SourceText.setText(uri1, LoremIpsum);
    var text = SourceText.getText(uri1, new Range(new Position(1, 3), new Position(2, 4)));
    assertEquals(
      "enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat."
        + System.lineSeparator() + "Duis",
      text);
  }

  @Test
  void StringAt_single_line()
  {

    SourceText.setText(uri1, LoremIpsum);
    var text = SourceText.getText(uri1, new Range(new Position(1, 3), new Position(1, 23)));
    assertEquals("enim ad minim veniam", text);
  }

  /**
   * test if we can run more than one program
   * successfully and thus statically held stuff does not
   * get in the way.
   * @throws IOException
   * @throws InterruptedException
   * @throws ExecutionException
   * @throws TimeoutException
   */
  @Test
  void RunMultiple() throws IOException, InterruptedException, ExecutionException, TimeoutException
  {
    SourceText.setText(uri1, HelloWorld);
    SourceText.setText(uri2, PythagoreanTriple);

    FuzionHelpers.Run(uri1);
    FuzionHelpers.Run(uri2);
    var message = FuzionHelpers.Run(uri1);

    assertEquals("Hello World!" + "\n", message.getMessage());
  }

  @Test
  void RunSuccessfulAfterRunWithTimeoutException()
    throws IOException, InterruptedException, ExecutionException, TimeoutException
  {
    SourceText.setText(uri1, ManOrBoy);
    SourceText.setText(uri2, HelloWorld);
    SourceText.setText(uri3, PythagoreanTriple);

    // NYI this will not throw once fuzion gets faster, how to test properly?
    assertThrows(TimeoutException.class, () -> FuzionHelpers.Run(uri1, 100));
    assertThrows(TimeoutException.class, () -> FuzionHelpers.Run(uri3, 50));

    assertEquals("Hello World!" + "\n", FuzionHelpers.Run(uri2).getMessage());
  }

  @Test
  void Features()
  {
    SourceText.setText(uri1, HelloWorld);
    SourceText.setText(uri2, ManOrBoy);
    assertEquals(1, FuzionHelpers.Features(uri1).count());
    assertEquals(13, FuzionHelpers.Features(uri2).count());
  }

  @Test
  void CommentOf()
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
      .DeclaredFeatures(FuzionParser.getMainFeature(uri1).get())
      .findFirst()
      .orElseThrow();
    assertEquals("# first comment line" + System.lineSeparator() + "# second comment line",
      FuzionHelpers.CommentOf(innerFeature));

  }

  @Test
  void CommentOfStdLibFeature()
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
    assertEquals(
      "# A handy shortcut for stdout.print, output string representation of" + System.lineSeparator()
        + "# an object, do not add a line break at the end." + System.lineSeparator() + "#",
      FuzionHelpers.CommentOf(yak));
  }

  @Test
  void SourceText()
  {
    var CommentExample = """
      myFeat is
      """;
    SourceText.setText(uri1, CommentExample);
    var myFeatIs = FuzionParser.getMainFeature(uri1)
      .get();
    var sourceText = SourceText.getText(Bridge.ToTextDocumentPosition(myFeatIs.pos()));
    assertEquals(true, sourceText.get().contains("myFeat is"));
  }

  @Test
  void SourceTextStdLibFile()
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
    assertEquals(true, sourceText.get().contains("yak(s ref Object) => stdout.print(s)"));
  }

  @Test
  void featureAt()
  {
    var sourceText = """
      myfeat is

        myi32 :i32 is

        print(x, y myi32, z i32) =>
          say "$x"
      """;
    SourceText.setText(uri1, sourceText);

    var feature = FuzionHelpers.featureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 5, 4)).get();
    assertEquals("say", feature.featureName().baseName());

    feature = FuzionHelpers.featureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 4, 8)).get();
    assertEquals("myi32", feature.featureName().baseName());

    feature = FuzionHelpers.featureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 4, 20)).get();
    assertEquals("i32", feature.featureName().baseName());
  }

  @Test
  void featureAtWithinLoop()
  {
    var sourceText = """
      ex8 is
        for s in ["one", "two", "three"] do
          say "$s"
                """;
    SourceText.setText(uri1, sourceText);
    var feature = FuzionHelpers.featureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 2, 4))
      .get();
    assertEquals("say", feature.featureName().baseName());
  }

  @Test
  void featureAtWithinFunction()
  {
    var sourceText = """
      ex is
        (1..10).forAll(num -> say num)
                  """;
    SourceText.setText(uri1, sourceText);

    assertEquals("infix ..", FuzionHelpers.featureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 1, 4))
      .get()
      .featureName()
      .baseName());

    assertEquals("forAll", FuzionHelpers.featureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 1, 10))
      .get()
      .featureName()
      .baseName());

    assertEquals("forAll", FuzionHelpers.featureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 1, 16))
      .get()
      .featureName()
      .baseName());

    assertEquals("say", FuzionHelpers.featureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 1, 24))
      .get()
      .featureName()
      .baseName());

    assertEquals("say", FuzionHelpers.featureAt(LSP4jUtils.TextDocumentPositionParams(uri1, 1, 26))
      .get()
      .featureName()
      .baseName());
  }

  @Test
  void endOfFeature()
  {
    var sourceText = """
      ex7 is
        (1..10).forAll()
              """;
    SourceText.setText(uri1, sourceText);
    var endOfFeature = FuzionParser.endOfFeature(FuzionParser.getMainFeature(uri1).get());
    assertEquals(2, endOfFeature._line);
    assertEquals(19, endOfFeature._column);
  }

  @Test
  void callAt()
  {
    var sourceText = """
      ex7 is
        (1..10).myCall()
              """;
    SourceText.setText(uri1, sourceText);
    var call = FuzionHelpers.callAt(LSP4jUtils.TextDocumentPositionParams(uri1, 1, 17))
      .get();
    assertEquals("myCall", call.name);
  }

  @Test
  void noSourceText()
  {
    var sourceText = """
      """;
    SourceText.setText(uri1, sourceText);
    var f = FuzionParser.getMainFeature(uri1);
    assertEquals("#universe", f.get().qualifiedName());
  }

  @Test
  void declaredFeatures()
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
    var f = FuzionParser.getMainFeature(uri1);
    var df = FuzionParser.DeclaredFeatures(f.get()).collect(Collectors.toList());
    assertEquals(2, df.size());
    assertTrue(df.stream().anyMatch(x -> x.featureName().baseName().equals("childFeat1")));
  }

  @Test
  void allOf()
  {
    SourceText.setText(uri1, HelloWorld);
    assertEquals("HelloWorld", FuzionHelpers
      .allOf(uri1, AbstractFeature.class)
      .filter(FuzionHelpers.IsFeatureInFile(uri1))
      .findFirst()
      .get()
      .featureName()
      .baseName());
    assertEquals("say", FuzionHelpers
      .allOf(uri1, Call.class)
      .filter(call -> uri1.equals(FuzionParser.getUri(call.pos())))
      .findFirst()
      .get()
      .calledFeature()
      .featureName()
      .baseName());
  }

  @Test
  void declaredFeaturesUniverse()
  {
    SourceText.setText(uri1, HelloWorld);
    assertTrue(FuzionParser.DeclaredFeatures(FuzionParser.universe(uri1)).count() > 10);
  }

  @Test
  void ASTbrokenSource()
  {
    var sourceText = """
      ex is
        (1..10).
            """;
    SourceText.setText(uri1, sourceText);
    var ex = FuzionParser.getMainFeature(uri1).get();
    var ast = FuzionHelpers.AST(ex);
    assertTrue(ast.contains("Call:hasInterval"));
    assertTrue(ast.contains("Call:#universe"));
  }



}

