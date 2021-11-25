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
 * Source of class Config
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server;

import java.util.List;
import java.util.concurrent.Future;

import com.google.gson.JsonObject;

import org.eclipse.lsp4j.services.LanguageClient;

import dev.flang.lsp.server.enums.Transport;

public class Config
{

  public static final boolean ComputeAsync = true;
  private static Future<List<Object>> _configuration;
  private static LanguageClient _languageClient;
  private static Transport _transport = Transport.stdio;

  public static LanguageClient languageClient()
  {
    return _languageClient;
  }

  public static void setLanguageClient(LanguageClient languageClient)
  {
    _languageClient = languageClient;
  }

  public static Transport transport()
  {
    return _transport;
  }

  public static void setTransport(Transport transport)
  {
    _transport = transport;
  }

  public static boolean DEBUG()
  {
    var debug = System.getenv("DEBUG");
    if (debug == null)
      {
        return false;
      }
    return debug.toLowerCase().equals("true");
  }

  public static void setConfiguration(Future<List<Object>> configuration)
  {
    _configuration = configuration;
  }

  public static dev.flang.util.List<String> JavaModules()
  {
    try
      {
        var modules = ((JsonObject) _configuration.get().get(0))
          .getAsJsonObject()
          .getAsJsonObject("java")
          .getAsJsonArray("modules");
        var result = new dev.flang.util.List<String>();
        modules.forEach(jsonElement -> {
          result.add(jsonElement.getAsString());
        });
        return result;
      }
    catch (Exception e)
      {
        return new dev.flang.util.List();
      }
  }


}
