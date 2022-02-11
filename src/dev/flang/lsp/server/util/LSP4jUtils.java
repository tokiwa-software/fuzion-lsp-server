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
 * Source of class LSP4jUtils
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.util;

import java.net.URI;
import java.util.Optional;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.shared.Util;
import dev.flang.shared.records.TokenInfo;

public final class LSP4jUtils
{
  public static TextDocumentIdentifier TextDocumentIdentifier(URI uri)
  {
    return new TextDocumentIdentifier(uri.toString());
  }

  public static TextDocumentPositionParams TextDocumentPositionParams(URI uri, Position position)
  {
    return new TextDocumentPositionParams(TextDocumentIdentifier(uri), position);
  }

  public static TextDocumentPositionParams TextDocumentPositionParams(URI uri, int line, int character)
  {
    return TextDocumentPositionParams(uri, new Position(line, character));
  }

  public static Position getPosition(TextDocumentPositionParams params)
  {
    return params.getPosition();
  }

  public static URI getUri(TextDocumentIdentifier params)
  {
    return Util.toURI(params.getUri());
  }

  public static URI getUri(TextDocumentPositionParams params)
  {
    return getUri(params.getTextDocument());
  }

  public static int ComparePosition(Position position1, Position position2)
  {
    var result = position1.getLine() < position2.getLine() ? -1: position1.getLine() > position2.getLine() ? +1: 0;
    if (result == 0)
      {
        result = position1.getCharacter() < position2.getCharacter() ? -1
                          : position1.getCharacter() > position2.getCharacter() ? +1: 0;
      }
    return result;
  }

  public static Range Range(TokenInfo tokenInfo)
  {
    var start = Bridge.ToPosition(tokenInfo.start());
    var end = Bridge.ToPosition(tokenInfo.end());
    return new Range(start, end);
  }

}
