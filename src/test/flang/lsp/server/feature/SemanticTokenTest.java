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


import org.eclipse.lsp4j.SemanticTokensParams;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.enums.TokenType;
import dev.flang.lsp.server.feature.SemanticToken;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class SemanticTokenTest extends ExtendedBaseTest
{
  @Test
  public void GetSemanticTokens()
  {
    SourceText.setText(uri1, Mandelbrot);
    var semanticTokens =
      SemanticToken.getSemanticTokens(new SemanticTokensParams(Cursor(uri1, 0, 0).getTextDocument()));
    assertTrue(semanticTokens.getData().stream().noneMatch(x -> x < 0));
    assertTrue(semanticTokens.getData().size() % 5 == 0);
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

    assertTrue(semanticTokens.getData().stream().noneMatch(x -> x < 0));

    // Comment
    assertEquals(Integer.valueOf(0), semanticTokens.getData().get(0));
    assertEquals(Integer.valueOf(0), semanticTokens.getData().get(1));
    assertEquals(Integer.valueOf("# comment\n".length()), semanticTokens.getData().get(2));
    assertEquals(Integer.valueOf(TokenType.Comment.num), semanticTokens.getData().get(3));
    assertEquals(Integer.valueOf(0), semanticTokens.getData().get(4));

    // Feature
    assertEquals(Integer.valueOf(1), semanticTokens.getData().get(5));
    assertEquals(Integer.valueOf(0), semanticTokens.getData().get(6));
    assertEquals(Integer.valueOf(7), semanticTokens.getData().get(7));
    assertEquals(Integer.valueOf(TokenType.Namespace.num), semanticTokens.getData().get(8));
    assertEquals(Integer.valueOf(0), semanticTokens.getData().get(9));

    // Keyword
    assertEquals(Integer.valueOf(0), semanticTokens.getData().get(10));
    assertEquals(Integer.valueOf(8), semanticTokens.getData().get(11));
    assertEquals(Integer.valueOf(2), semanticTokens.getData().get(12));
    assertEquals(Integer.valueOf(TokenType.Keyword.num), semanticTokens.getData().get(13));
    assertEquals(Integer.valueOf(0), semanticTokens.getData().get(14));



  }

}
