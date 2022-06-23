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
 * Source of class SemanticToken
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.feature;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.lsp.server.enums.TokenModifier;
import dev.flang.lsp.server.enums.TokenType;
import dev.flang.lsp.server.util.Bridge;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.shared.ASTWalker;
import dev.flang.shared.LexerTool;
import dev.flang.shared.records.TokenInfo;
import dev.flang.util.ANY;
import dev.flang.util.HasSourcePosition;

public class SemanticToken extends ANY
{

  public static final SemanticTokensLegend Legend =
    new SemanticTokensLegend(TokenType.asList, TokenModifier.asList);

  public static SemanticTokens getSemanticTokens(SemanticTokensParams params)
  {
    // NYI HACK since there is cases now where multiple features have same sourceposition
    // should be changed in the compiler.
    var pos2Item = new HashMap<Integer, HashSet<HasSourcePosition>>();

    ASTWalker
      .Traverse(LSP4jUtils.getUri(params.getTextDocument()))
      .map(e -> e.getKey())
      .filter(x -> x instanceof AbstractFeature || x instanceof AbstractCall)
      .forEach(x -> {
        var key = TokenInfo.KeyOf(x.pos());
        pos2Item.putIfAbsent(key, new HashSet<HasSourcePosition>());
        pos2Item.get(key).add(x);
      });

    var lexerTokens =
      LexerTool
        .TokensFrom(
          Bridge.ToSourcePosition(
            new TextDocumentPositionParams(params.getTextDocument(), new Position(0, 0))),
          // raw because we need comments
          true)
        .filter(x -> x.IsSemanticToken(pos2Item))
        .collect(Collectors.toList());

    return new SemanticTokens(SemanticTokenData(lexerTokens, pos2Item));
  }

  private static List<Integer> SemanticTokenData(List<TokenInfo> lexerTokens,
    Map<Integer, HashSet<HasSourcePosition>> pos2Item)
  {
    return IntStream
      .range(0, lexerTokens.size())
      .mapToObj(x -> {
        Optional<TokenInfo> previousToken = x == 0 ? Optional.empty(): Optional.of(lexerTokens.get(x - 1));
        return lexerTokens.get(x).SemanticTokenData(previousToken, pos2Item);
      })
      .flatMap(x -> x)
      .collect(Collectors.toList());
  }

}
