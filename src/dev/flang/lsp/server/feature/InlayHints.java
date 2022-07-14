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
import java.util.stream.Stream;

import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.ast.AbstractCall;
import dev.flang.lsp.server.util.Bridge;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.shared.ASTWalker;
import dev.flang.shared.CallTool;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.Util;
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
    return ASTWalker.Traverse(LSP4jUtils.getUri(params.getTextDocument()))
      .filter(e -> e.getKey() instanceof AbstractCall)
      .map(e -> (AbstractCall) e.getKey())
      .filter(c -> IsInRange(params.getRange(), c.pos()))
      .filter(c -> !CallTool.IsFixLikeCall(c))
      .filter(CallTool.CalledFeatureNotInternal)
      .flatMap(c -> {
        if (c.actuals().size() == c.calledFeature().valueArguments().size())
          {
            return IntStream.range(0, c.actuals().size())
              .filter(idx -> Util.CharCount(
                c.calledFeature().valueArguments().get(idx).featureName().baseName()) >= MIN_PARAM_NAME_LENGTH)
              // this is the case e.g. for _ args
              .filter(idx -> !FeatureTool.IsInternal(c.calledFeature().valueArguments().get(idx)))
              // omit inlay hint if actual is call of same name as arg
              .filter(idx -> !(c.actuals().get(idx) instanceof AbstractCall ac && ac.calledFeature().featureName().baseName().equals(c.calledFeature().valueArguments().get(idx).featureName().baseName())))
              // for array initialization via [] syntax, don't show inlay hint
              .filter(idx -> !c.calledFeature().valueArguments().get(idx).qualifiedName().equals("array.internalArray"))
              .mapToObj(idx -> {
                var inlayHint = new InlayHint(Bridge.ToPosition(CallTool.StartOfExpr(c.actuals().get(idx))),
                  Either.forLeft(c.calledFeature().valueArguments().get(idx).featureName().baseName() + ":"));
                inlayHint.setKind(InlayHintKind.Parameter);
                inlayHint.setPaddingLeft(true);
                inlayHint.setPaddingRight(true);
                return inlayHint;
              });
          }
        // NYI when is actuals count != calledFeature valueArgs count?
        else
          {
            return Stream.empty();
          }
      })
      .collect(Collectors.toList());
  }

  private static boolean IsInRange(Range range, SourcePosition pos)
  {
    var p = Bridge.ToPosition(pos);
    return range.getStart().getLine() <= p.getLine() && range.getEnd().getLine() >= p.getLine();
  }
}
