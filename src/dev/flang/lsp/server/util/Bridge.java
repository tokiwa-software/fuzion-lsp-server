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

import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.AbstractCall;
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
      require(!sourcePosition.isBuiltIn());
    return new Position(sourcePosition._line - 1, sourcePosition._column - 1);
  }

  public static Location ToLocation(SourcePosition start, SourcePosition end)
  {
    if (PRECONDITIONS)
      require(!start.isBuiltIn());
    return new Location(ParserTool.getUri(start).toString(), new Range(ToPosition(start), ToPosition(end)));
  }

  public static Range ToRange(AbstractFeature feature)
  {
    if (PRECONDITIONS)
      require(!feature.pos().isBuiltIn());

    return new Range(ToPosition(feature.pos()), ToPosition(ParserTool.endOfFeature(feature)));
  }

  public static Range ToRangeBaseName(AbstractFeature feature)
  {
    var bareNamePosition = FeatureTool.BareNamePosition(feature);
    return new Range(
      ToPosition(bareNamePosition),
      ToPosition(new SourcePosition(bareNamePosition._sourceFile,
        bareNamePosition._line,
        bareNamePosition._column + Util.CharCount(FeatureTool.BareName(feature)))));
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

  public static Location ToLocation(AbstractCall call)
  {
    if (PRECONDITIONS)
      require(!call.pos().isBuiltIn());
    return new Location(ParserTool.getUri(call.pos()).toString(),
      ToRange(call));
  }

  private static Range ToRange(AbstractCall call)
  {
    return new Range(ToPosition(call.pos()), ToPosition(CallTool.endOfCall(call)));
  }

  public static Location ToLocation(AbstractFeature af)
  {
    if (PRECONDITIONS)
      require(!af.pos().isBuiltIn());
    return new Location(ParserTool.getUri(af.pos()).toString(),
      new Range(ToPosition(af.pos()), ToPosition(ParserTool.endOfFeature(af))));
  }

  public static DocumentHighlight ToHighlight(AbstractCall c)
  {
    // NYI set correct DocumentHighlightKind
    return new DocumentHighlight(ToRange(c), DocumentHighlightKind.Read);
  }
}
