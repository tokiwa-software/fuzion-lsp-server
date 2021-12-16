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
 * Source of class UriTest
 *
 *---------------------------------------------------------------------*/

package test.flang.shared;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import dev.flang.shared.Util;

public class UtilTest extends BaseTest
{
  @Test
  public void DecodeEncodeTest() throws URISyntaxException, UnsupportedEncodingException
  {
    assertTrue(new URI("file:/c:/temp.fz").equals(Util.toURI("file:///c%3A/temp.fz")));
    assertTrue(Util.toURI("file:/c:/temp file.fz").equals(Util.toURI("file:///c%3A/temp file.fz")));
    assertTrue(Path.of(new URI("file:/c:/temp.fz")).toUri().equals(Util.toURI("file:///c%3A/temp.fz")));
  }

}
