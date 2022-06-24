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
 * Source of class SemanticTokenTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server.feature;


import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.enums.TokenType;
import dev.flang.lsp.server.feature.SemanticToken;
import dev.flang.shared.IO;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class SemanticTokenTest extends ExtendedBaseTest
{
  @Test
  public void GetSemanticTokensMandelbrot()
  {
    SourceText.setText(uri1, Mandelbrot);
    var semanticTokens =
      SemanticToken.getSemanticTokens(new SemanticTokensParams(Cursor(uri1, 0, 0).getTextDocument()));
    AssertBasicDataSanity(semanticTokens);
  }

  @Test
  public void GetSemanticTokensFaulhaber()
  {
    SourceText.setText(uri1, Faulhaber);
    var semanticTokens =
      SemanticToken.getSemanticTokens(new SemanticTokensParams(Cursor(uri1, 0, 0).getTextDocument()));
    AssertBasicDataSanity(semanticTokens);
  }

  @Test
  public void GetSemanticTokensFunKeyWord()
  {
    SourceText.setText(uri1, """
        ex is
          print(i i32) =>
            yak i

          (1..2)
            .forAll (fun print)
      """);
    var semanticTokens =
      SemanticToken.getSemanticTokens(new SemanticTokensParams(Cursor(uri1, 0, 0).getTextDocument()));
    AssertBasicDataSanity(semanticTokens);
  }

  private static void AssertNoNegative(SemanticTokens st)
  {
    assertTrue(st.getData().stream().noneMatch(x -> x < 0));
  }

  private static void AssertMultipleOfFive(SemanticTokens st)
  {
    assertTrue(st.getData().size() % 5 == 0);
  }

  private static void AssertLengthsGreaterZero(SemanticTokens st)
  {
    assertTrue(IntStream
      .range(0, st.getData().size() + 0)
      .filter(x -> x % 5 == 2)
      .allMatch(x -> st.getData().get(x) > 0));
  }

  private static void AssertBasicDataSanity(SemanticTokens st)
  {
    AssertMultipleOfFive(st);
    AssertNoNegative(st);
    AssertLengthsGreaterZero(st);
  }

  @Test
  public void SemanticTokensIgnoredTokenFirst()
  {
    SourceText.setText(uri1, """
      # comment
      feature is
            """);
    var semanticTokens =
      SemanticToken.getSemanticTokens(new SemanticTokensParams(Cursor(uri1, 0, 0).getTextDocument()));

    AssertBasicDataSanity(semanticTokens);

    // Comment
    assertEquals(semanticTokens, 0, 0, 0, "# comment\n".length(), TokenType.Comment, 0);

    // Feature
    assertEquals(semanticTokens, 1, 1, 0, 7, TokenType.Namespace, 0);

    // Keyword
    assertEquals(semanticTokens, 2, 0, 8, 2, TokenType.Keyword, 0);
  }

  private void assertEquals(SemanticTokens st, int tokenIndex, Integer relativeLine, Integer relativeStartChar,
    Integer length,
    TokenType type, Integer modifiers)
  {
    var data = st.getData();
    assertEquals(Integer.valueOf(relativeLine), data.get(tokenIndex * 5 + 0));
    assertEquals(Integer.valueOf(relativeStartChar), data.get(tokenIndex * 5 + 1));
    assertEquals(Integer.valueOf(length), data.get(tokenIndex * 5 + 2));
    assertEquals(Integer.valueOf(type.num), data.get(tokenIndex * 5 + 3));
    assertEquals(Integer.valueOf(modifiers), data.get(tokenIndex * 5 + 4));
  }

  @Test
  public void SemanticTokenTypeParameter()
  {
    SourceText.setText(uri1, """
      ex =>
        a (H type, b H) is
        b := 1.2
        a f64 b
                  """);

    var semanticTokens =
      SemanticToken.getSemanticTokens(new SemanticTokensParams(Cursor(uri1, 0, 0).getTextDocument()));

    AssertBasicDataSanity(semanticTokens);

    // expected Tokens: ex, =>, a, H, type, b, H, is, b, :=, 1.2, a, f64, b
    assertEquals(14, semanticTokens.getData().size() / 5);

    // a (H type, b H) is
    // ---^
    assertEquals(semanticTokens, 3, 0, 3, 1, TokenType.TypeParameter, 0);

    // a (H type, b H) is
    // -----------^
    assertEquals(semanticTokens, 5, 0, 6, 1, TokenType.Parameter, 4);

    // a (H type, b H) is
    // -------------^
    assertEquals(semanticTokens, 6, 0, 2, 1, TokenType.Type, 0);

    // b := 1.2
    // ^
    assertEquals(semanticTokens, 8, 1, 2, 1, TokenType.Property, 4);

    // a f64 b
    // --^
    assertEquals(semanticTokens, 12, 0, 2, 3, TokenType.Type, 0);

  }

  @SuppressWarnings("unused")
  private void PrintDebug(SemanticTokens semanticTokens)
  {
    AtomicInteger counter = new AtomicInteger();
    semanticTokens.getData()
      .stream()
      .collect(Collectors.groupingBy(x -> counter.getAndIncrement() / 5))
      .values()
      .forEach(c -> {
        IO.SYS_ERR.println(c.stream().map(i -> i.toString()).collect(Collectors.joining(", ")));
      });
  }

}
