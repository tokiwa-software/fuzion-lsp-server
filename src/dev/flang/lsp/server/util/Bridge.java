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

import java.net.URI;
import java.nio.file.Path;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.AbstractFeature;
import dev.flang.lsp.server.Converters;
import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.Util;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

/**
 * provides bridge utility functions between lsp4j <-> fuzion
 */
public class Bridge {

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
    return new Range(ToPosition(feature.pos()), ToPosition(FuzionHelpers.endOfFeature(feature)));
  }

  public static DocumentSymbol ToDocumentSymbol(AbstractFeature feature)
  {
    return new DocumentSymbol(Converters.ToLabel(feature), SymbolKind.Key, ToRange(feature), ToRange(feature));
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

  public static TextDocumentPositionParams ToTextDocumentPosition(SourcePosition sourcePosition)
  {
    return Util.TextDocumentPositionParams(FuzionParser.getUri(sourcePosition), ToPosition(sourcePosition));
  }

}
