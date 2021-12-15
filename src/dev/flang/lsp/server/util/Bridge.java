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
 * Source of class Bridge
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.util;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.AbstractFeature;
import dev.flang.lsp.server.Util;
import dev.flang.util.SourcePosition;

/**
 * provides bridge utility functions converting between lsp4j <-> fuzion
 */
public class Bridge
{

  public static Position ToPosition(SourcePosition sourcePosition)
  {
    return new Position(sourcePosition._line - 1, sourcePosition._column - 1);
  }

  public static Location ToLocation(SourcePosition sourcePosition)
  {
    var position = ToPosition(sourcePosition);
    return new Location(FuzionParser.getUri(sourcePosition).toString(), new Range(position, position));
  }

  public static Range ToRange(AbstractFeature feature)
  {
    return new Range(ToPosition(feature.pos()), ToPosition(FuzionParser.endOfFeature(feature)));
  }

  public static DocumentSymbol ToDocumentSymbol(AbstractFeature feature)
  {
    return new DocumentSymbol(FeatureTool.ToLabel(feature), SymbolKind.Key, ToRange(feature), ToRange(feature));
  }

  public static TextDocumentPositionParams ToTextDocumentPosition(SourcePosition sourcePosition)
  {
    return LSP4jUtils.TextDocumentPositionParams(FuzionParser.getUri(sourcePosition), ToPosition(sourcePosition));
  }

  public static SourcePosition ToSourcePosition(TextDocumentPositionParams params)
  {
    return new SourcePosition(FuzionLexer.ToSourceFile(Util.toURI(params.getTextDocument().getUri())), params.getPosition().getLine() + 1, params.getPosition().getCharacter() + 1);
  }

}
