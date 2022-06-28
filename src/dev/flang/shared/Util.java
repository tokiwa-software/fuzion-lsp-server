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

package dev.flang.shared;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * utils which are independent of fuzion
 */
public class Util
{
  public static URI toURI(String uri)
  {
    try
      {
        // https://docs.oracle.com/javase/7/docs/api/java/net/URI.html
        // RFC 2396 allows most characters except spaces
        return new URI(URLDecoder.decode(uri, StandardCharsets.UTF_8.toString()).replace(" ", "%20"));
      }
    catch (Exception e)
      {
        ErrorHandling.WriteStackTraceAndExit(1, e);
        return null;
      }
  }

  public static <T> Set<T> ArrayToSet(T[] arr)
  {
    return Arrays.stream(arr).collect(Collectors.toSet());
  }

  public static String ShortName(Class<?> clazz)
  {
    if (clazz.isAnonymousClass())
      {
        return ShortName(clazz.getSuperclass());
      }
    return clazz.getSimpleName();
  }

  /**
  * @param uri
  * @return
  */
  public static boolean IsStdLib(URI uri)
  {
    return uri.toString().startsWith(SourceText.FuzionHome.toUri().toString());
  }


  /**
   * map of least recently used
   * @param <T>
   * @param <U>
   * @param maxSize the maximum size this LRU holds
   * @param c called when an item is removed
   * @return
   */
  public static <T, U> Map<T, U> ThreadSafeLRUMap(int maxSize, Consumer<Map.Entry<T, U>> c)
  {
    return Collections
      .synchronizedMap(new LinkedHashMap<T, U>(maxSize + 1, .75F, true) {
        public boolean removeEldestEntry(Map.Entry<T, U> eldest)
        {
          var removeEldestEntry = size() > maxSize;
          if (c != null && removeEldestEntry)
            {
              c.accept(eldest);
            }
          return removeEldestEntry;
        }
      });
  }

  public static <T> Stream<T> ConcatStreams(Stream<T>... streams)
  {
    return Stream.of(streams)
      .reduce(Stream::concat)
      .orElseGet(Stream::empty);
  }

}
