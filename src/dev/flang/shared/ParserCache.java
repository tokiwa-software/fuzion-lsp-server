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
 * Source of class ParserCache
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import dev.flang.ast.AbstractFeature;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.SourceModule;
import dev.flang.lsp.server.util.Log;
import dev.flang.shared.records.ParserCacheRecord;
import dev.flang.util.ANY;

public class ParserCache extends ANY
{
  private int PARSER_CACHE_MAX_SIZE = 10;

  // LRU-Cache holding the most recent results of parser
  private Map<String, ParserCacheRecord> sourceText2ParserCache =
    Collections
      .synchronizedMap(new LinkedHashMap<String, ParserCacheRecord>(PARSER_CACHE_MAX_SIZE + 1, .75F, true) {

        public boolean removeEldestEntry(Map.Entry<String, ParserCacheRecord> eldest)
        {
          var removeEldestEntry = size() > PARSER_CACHE_MAX_SIZE;
          if (removeEldestEntry)
            {
              var frontEnd = universe2FrontEndMap.remove(eldest.getValue().mir().universe());
              check(frontEnd != null, universe2FrontEndMap.size() <= PARSER_CACHE_MAX_SIZE);
            }
          return removeEldestEntry;
        }
      });
  private HashMap<AbstractFeature, FrontEnd> universe2FrontEndMap = new HashMap<>();

  public ParserCacheRecord computeIfAbsent(String sourceText, Function<String, ParserCacheRecord> mappingFunction)
  {
    return Log.taskExceedsMaxTime(() -> {
      var result = sourceText2ParserCache.computeIfAbsent(sourceText, mappingFunction);
      universe2FrontEndMap.put(result.mir().universe(), result.frontEnd());
      return result;
    }, Duration.ofSeconds(1), "parse: " + sourceText);
  }


  /**
   * get the SourceModule the Feature belongs to
   * @param f
   * @return
   */
  public SourceModule SourceModule(AbstractFeature f)
  {
    return universe2FrontEndMap.get(f.universe()).module();
  }
}
