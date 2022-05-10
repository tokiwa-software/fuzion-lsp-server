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
 * Source of class Highlight
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.feature;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;

import dev.flang.lsp.server.util.Bridge;
import dev.flang.lsp.server.util.QueryAST;
import dev.flang.shared.FeatureTool;

/**
 * return document hightlights for calls to feature
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_documentHighlight
 */
public class Highlight
{
  public static List<? extends DocumentHighlight> getHightlights(DocumentHighlightParams params)
  {
    var feature = QueryAST.FeatureAt(params);
    if (feature.isEmpty())
      {
        return List.of();
      }
    return FeatureTool.CallsTo(feature.get())
      .map(entry -> entry.getKey())
      .map(c -> Bridge.ToHighlight(c))
      .collect(Collectors.toList());
  }
}
