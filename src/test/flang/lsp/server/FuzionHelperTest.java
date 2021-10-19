package test.flang.lsp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.FuzionTextDocumentService;
import dev.flang.lsp.server.Util;
import dev.flang.parser.Lexer.Token;

class FuzionHelperTest
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
    var foundToken = FuzionHelpers.nextTokenOfType(", aösldkjf", Util.HashSetOf(Token.t_comma));

    assertEquals(1, foundToken.start()._column);
    assertEquals(2, foundToken.end()._column);
    assertEquals(",", foundToken.text());
  }

  @Test
  void NextTokenOfType_at_end()
  {
    var foundToken = FuzionHelpers.nextTokenOfType("1234,", Util.HashSetOf(Token.t_comma));

    assertEquals(5, foundToken.start()._column);
    assertEquals(6, foundToken.end()._column);
    assertEquals(",", foundToken.text());
  }

  @Test
  void NextToken_a()
  {

    FuzionTextDocumentService.setText("uri", ManOrBoy);

    var nextToken =
      FuzionHelpers.nextToken(new TextDocumentPositionParams(new TextDocumentIdentifier("uri"), new Position(2, 2)));
    assertEquals("a", nextToken.text());
    assertEquals(4, nextToken.end()._column);
  }

  @Test
  void NextToken_i32()
  {

    FuzionTextDocumentService.setText("uri", ManOrBoy);

    var nextToken =
      FuzionHelpers.nextToken(new TextDocumentPositionParams(new TextDocumentIdentifier("uri"), new Position(6, 7)));
    assertEquals("i32", nextToken.text());
    assertEquals(10, nextToken.end()._column);
  }

  @Test
  void EndOfToken_man_or_boy()
  {
    FuzionTextDocumentService.setText("uri", ManOrBoy);

    var endOfToken = FuzionHelpers.endOfToken("uri", new Position(0, 0));
    assertEquals(10, endOfToken.getCharacter());
    assertEquals(0, endOfToken.getLine());

  }

  @Test
  void EndOfToken_i32()
  {
    FuzionTextDocumentService.setText("uri", ManOrBoy);

    var endOfToken = FuzionHelpers.endOfToken("uri", new Position(2, 6));
    assertEquals(9, endOfToken.getCharacter());
    assertEquals(2, endOfToken.getLine());
  }

  @Test
  void EndOfToken_opening_brace()
  {
    FuzionTextDocumentService.setText("uri", ManOrBoy);

    var endOfToken = FuzionHelpers.endOfToken("uri", new Position(2, 3));
    assertEquals(4, endOfToken.getCharacter());
    assertEquals(2, endOfToken.getLine());
  }

  @Test
  void StringAt_multi_line()
  {

    FuzionTextDocumentService.setText("uri", LoremIpsum);
    var text = FuzionHelpers.stringAt("uri", new Range(new Position(1, 3), new Position(2, 4)));
    assertEquals(
      "enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.\nDuis",
      text);
  }

  @Test
  void StringAt_single_line()
  {

    FuzionTextDocumentService.setText("uri", LoremIpsum);
    var text = FuzionHelpers.stringAt("uri", new Range(new Position(1, 3), new Position(1, 23)));
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
    FuzionTextDocumentService.setText("uri", HelloWorld);
    FuzionTextDocumentService.setText("uri2", PythagoreanTriple);

    FuzionHelpers.Run("uri");
    FuzionHelpers.Run("uri2");
    var message = FuzionHelpers.Run("uri");

    assertEquals("Hello World!\n", message.getMessage());
  }

  @Test
  void RunSuccessfulAfterRunWithTimeoutException()
    throws IOException, InterruptedException, ExecutionException, TimeoutException
  {
    FuzionTextDocumentService.setText("uri", ManOrBoy);
    FuzionTextDocumentService.setText("uri2", HelloWorld);
    FuzionTextDocumentService.setText("uri3", PythagoreanTriple);

    // NYI this will not throw once fuzion gets faster, how to test properly?
    assertThrows(TimeoutException.class, () -> FuzionHelpers.Run("uri", 100));
    assertThrows(TimeoutException.class, () -> FuzionHelpers.Run("uri3", 50));

    assertEquals("Hello World!\n", FuzionHelpers.Run("uri2").getMessage());
  }

}

