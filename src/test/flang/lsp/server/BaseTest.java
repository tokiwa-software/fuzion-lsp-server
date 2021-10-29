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
 * Source of class BaseTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server;

import dev.flang.lsp.server.Util;
import java.net.URI;
import java.nio.file.Path;

public abstract class BaseTest {
  protected static final URI uri1 = Util.toURI(Path.of("/").toUri().toString() + "uri1");
  protected static final URI uri2 = Util.toURI(Path.of("/").toUri().toString() + "uri2");
  protected static final URI uri3 = Util.toURI(Path.of("/").toUri().toString() + "uri3");
}
