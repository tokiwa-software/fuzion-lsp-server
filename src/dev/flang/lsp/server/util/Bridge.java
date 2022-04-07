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
import dev.flang.shared.FeatureTool;
import dev.flang.shared.LexerTool;
import dev.flang.shared.ParserTool;
import dev.flang.shared.Util;
import dev.flang.util.ANY;
import dev.flang.util.SourcePosition;

/**
 * provides bridge utility functions converting between lsp4j <-> fuzion
 */
public class Bridge extends ANY
{

  public static Position ToPosition(SourcePosition sourcePosition)
  {
    if (PRECONDITIONS)
      require(IsValidSourcePosition(sourcePosition));
    return new Position(sourcePosition._line - 1, sourcePosition._column - 1);
  }

  public static Location ToLocation(SourcePosition sourcePosition)
  {
    if (PRECONDITIONS)
      require(IsValidSourcePosition(sourcePosition));
    var position = ToPosition(sourcePosition);
    return new Location(ParserTool.getUri(sourcePosition).toString(), new Range(position, position));
  }

  public static Range ToRange(AbstractFeature feature)
  {
    if (PRECONDITIONS)
      require(IsValidSourcePosition(feature.pos()));

    return new Range(ToPosition(feature.pos()), ToPosition(ParserTool.endOfFeature(feature)));
  }

  private static boolean IsValidSourcePosition(SourcePosition pos)
  {
    return !pos.equals(SourcePosition.notAvailable) &&
      !pos.equals(SourcePosition.builtIn);
  }

  public static Range ToRangeBaseName(AbstractFeature feature)
  {
    var baseNamePosition = FeatureTool.BaseNamePosition(feature);
    return new Range(
      ToPosition(baseNamePosition),
      ToPosition(new SourcePosition(baseNamePosition._sourceFile,
        baseNamePosition._line,
        baseNamePosition._column + feature.featureName().baseName().length())));
  }

  public static DocumentSymbol ToDocumentSymbol(AbstractFeature feature)
  {
    return new DocumentSymbol(FeatureTool.ToLabel(feature), SymbolKind(feature), ToRange(feature), ToRange(feature));
  }

  private static SymbolKind SymbolKind(AbstractFeature feature)
  {
    if (feature.isChoice())
      {
        return SymbolKind.Enum;
      }
    if (feature.isBuiltInPrimitive() && "bool".equals(feature.featureName().baseName()))
      {
        return SymbolKind.Boolean;
      }
    if (feature.isBuiltInPrimitive())
      {
        return SymbolKind.Number;
      }
    if (feature.isConstructor() || feature.isIntrinsicConstructor())
      {
        return SymbolKind.Constructor;
      }
    if (feature.isField())
      {
        return SymbolKind.Constant;
      }
    if (feature.isRoutine())
      {
        return SymbolKind.Function;
      }
    return SymbolKind.Class;
  }

  public static TextDocumentPositionParams ToTextDocumentPosition(SourcePosition sourcePosition)
  {
    return LSP4jUtils.TextDocumentPositionParams(ParserTool.getUri(sourcePosition), ToPosition(sourcePosition));
  }

  public static SourcePosition ToSourcePosition(TextDocumentPositionParams params)
  {
    return new SourcePosition(LexerTool.ToSourceFile(Util.toURI(params.getTextDocument().getUri())),
      params.getPosition().getLine() + 1, params.getPosition().getCharacter() + 1);
  }
}
