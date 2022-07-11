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
 * Source of class ExtendedBaseTest
 *
 * extends BaseTest with utility functions depending on lsp4j
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.util.SourcePosition;
import test.flang.shared.BaseTest;

public abstract class ExtendedBaseTest extends BaseTest
{

  protected static TextDocumentIdentifier TextDocument(SourcePosition pos)
  {
    return new TextDocumentIdentifier(pos._sourceFile._fileName.toUri().toString());
  }

  protected static Position Position(SourcePosition pos)
  {
    return new Position(pos._line - 1, pos._column - 1);
  }

  protected static TextDocumentPositionParams TextDocumentPosition(SourcePosition pos)
  {
    return new TextDocumentPositionParams(TextDocument(pos), Position(pos));
  }

}
