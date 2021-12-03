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
 * Source of class Util
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.lsp.server.util.ErrorHandling;

/**
 * utils which are independent of fuzion
 */
public class Util
{
  public static URI toURI(String uri)
  {
    try
      {
        return new URI(URLDecoder.decode(uri, StandardCharsets.UTF_8.toString()));
      }
    catch (Exception e)
      {
        ErrorHandling.WriteStackTraceAndExit(1, e);
        return null;
      }
  }

  public static <T> HashSet<T> HashSetOf(T... values)
  {
    return Stream.of(values).collect(Collectors.toCollection(HashSet::new));
  }
}
