package test.flang.lsp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

