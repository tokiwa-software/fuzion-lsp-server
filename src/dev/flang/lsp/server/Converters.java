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
 * Source of class Converters
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server;

import java.net.URI;
import java.nio.file.Path;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Assign;
import dev.flang.ast.Block;
import dev.flang.ast.Call;
import dev.flang.ast.Feature;
import dev.flang.ast.If;
import dev.flang.lsp.server.records.TokenInfo;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

/**
 * collection of static methods converting dev.flang objects to lsp4j
 */
public final class Converters
{

  public static Position ToPosition(SourcePosition sourcePosition)
  {
    return new Position(sourcePosition._line - 1, sourcePosition._column - 1);
  }

  public static Location ToLocation(SourcePosition sourcePosition)
  {
    var position = ToPosition(sourcePosition);
    return new Location(ParserHelper.getUri(sourcePosition).toString(), new Range(position, position));
  }

  public static Range ToRange(AbstractFeature feature)
  {
    return new Range(ToPosition(feature.pos()), ToPosition(FuzionHelpers.endOfFeature(feature)));
  }

  public static Range ToRange(TextDocumentPositionParams params)
  {
    var tokenIdent = LexerUtil.rawTokenAt(params);
    var line = params.getPosition().getLine();
    var characterStart = tokenIdent.start()._column - 1;
    return new Range(new Position(line, characterStart),
      new Position(line, characterStart + tokenIdent.text().length()));
  }

  public static DocumentSymbol ToDocumentSymbol(AbstractFeature feature)
  {
    return new DocumentSymbol(Converters.ToLabel(feature), SymbolKind.Key, ToRange(feature), ToRange(feature));
  }

  /**
   * @param feature
   * @return example: array<T>(length i32, init Function<array.T, i32>) => array<array.T>
   */
  public static String ToLabel(AbstractFeature feature)
  {
    if (!FuzionHelpers.IsRoutineOrRoutineDef(feature))
      {
        return feature.featureName().baseName();
      }
    var arguments = "(" + feature.arguments()
      .stream()
      .map(a -> a.thisType().featureOfType().featureName().baseName() + " " + a.thisType().featureOfType().resultType())
      .collect(Collectors.joining(", ")) + ")";
    return feature.featureName().baseName() + feature.generics() + arguments + " => " + feature.resultType();
  }

  public static Range ToRange(TokenInfo tokenInfo)
  {
    var start = ToPosition(tokenInfo.start());
    var end = ToPosition(tokenInfo.end());
    return new Range(start, end);
  }

  public static TextDocumentPositionParams ToTextDocumentPosition(SourcePosition sourcePosition)
  {
    return Util.TextDocumentPositionParams(ParserHelper.getUri(sourcePosition), ToPosition(sourcePosition));
  }

  public static SourceFile ToSourceFile(URI uri)
  {
    return Util.WithRedirectedStdErr(() -> {
      var filePath = Path.of(uri);
      if (filePath.equals(SourceFile.STDIN))
        {
          return new SourceFile(SourceFile.STDIN);
        }
      if (filePath.equals(SourceFile.BUILT_IN))
        {
          return new SourceFile(SourceFile.BUILT_IN);
        }
      return new SourceFile(filePath);
    });
  }

  public static String ToLabel(Object item)
  {
    try
      {
        if (item instanceof If || item instanceof Block)
          {
            return "";
          }
        if (item instanceof Call c)
          {
            return c.calledFeature().qualifiedName();
          }
        if (item instanceof Assign a)
          {
            return a._assignedField.qualifiedName();
          }
        if (item instanceof AbstractFeature af)
          {
            return ToLabel(af);
          }
        return item.toString();
      }
    catch (Exception e)
      {
        return "";
      }
  }

}
