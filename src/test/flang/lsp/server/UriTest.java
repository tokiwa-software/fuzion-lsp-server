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

package test.flang.lsp.server;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class UriTest extends BaseTest
{
  @Test
  public void DecodeEncodeTest() throws URISyntaxException, UnsupportedEncodingException
  {
    assertTrue(new URI("file:/c:/temp.fz").equals(new URI(URLDecoder.decode("file:///c%3A/temp.fz", StandardCharsets.UTF_8.toString()))));
    assertTrue(Path.of(new URI("file:/c:/temp.fz")).toUri().equals(new URI(URLDecoder.decode("file:///c%3A/temp.fz", StandardCharsets.UTF_8.toString()))));
  }

}
