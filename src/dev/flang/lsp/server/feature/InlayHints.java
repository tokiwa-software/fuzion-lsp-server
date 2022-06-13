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
 * Source of class InlayHints
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.feature;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.ast.AbstractCall;
import dev.flang.lsp.server.util.Bridge;
import dev.flang.lsp.server.util.CallTool;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.shared.ASTWalker;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.ParserTool;
import dev.flang.util.ANY;
import dev.flang.util.SourcePosition;

/**
 * Provide inlay hints for actuals.
 * See: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlayHint
 */
public class InlayHints extends ANY
{
  private static final int MIN_PARAM_NAME_LENGTH = 2;

  public static List<InlayHint> getInlayHints(InlayHintParams params)
  {
    return ASTWalker.Traverse(ParserTool.TopLevelFeatures(LSP4jUtils.getUri(params.getTextDocument())))
      .filter(e -> e.getKey() instanceof AbstractCall)
      .map(e -> (AbstractCall) e.getKey())
      .filter(c -> IsInRange(params.getRange(), c.pos()))
      .filter(c -> !CallTool.IsFixLikeCall(c))
      .filter(c -> !FeatureTool.IsInternal(c.calledFeature()))
      .flatMap(c -> {

        check(c.actuals().size() == c.calledFeature().valueArguments().size());

        return IntStream.range(0, c.actuals().size())
          .filter(idx -> c.calledFeature().valueArguments().get(idx).featureName().baseName().length() >= MIN_PARAM_NAME_LENGTH)
          .mapToObj(idx -> {
            var inlayHint = new InlayHint(Bridge.ToPosition(c.actuals().get(idx).pos()),
              Either.forLeft(c.calledFeature().valueArguments().get(idx).featureName().baseName() + ":"));
            inlayHint.setKind(InlayHintKind.Parameter);
            inlayHint.setPaddingLeft(true);
            inlayHint.setPaddingRight(true);
            return inlayHint;
          });
      })
      .collect(Collectors.toList());
  }

  private static boolean IsInRange(Range range, SourcePosition pos)
  {
    var p = Bridge.ToPosition(pos);
    return range.getStart().getLine() <= p.getLine() && range.getEnd().getLine() >= p.getLine();
  }
}