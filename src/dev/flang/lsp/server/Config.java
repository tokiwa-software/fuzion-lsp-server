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

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;

import com.google.gson.JsonObject;

import dev.flang.lsp.server.enums.Transport;
import dev.flang.shared.ParserTool;

public class Config
{

  public static final boolean ComputeAsync = true;
  public static final long DIAGNOSTICS_DEBOUNCE_DELAY_MS = 1000;
  private static Future<List<Object>> _configuration;
  private static LanguageClient _languageClient;
  private static Transport _transport = Transport.stdio;
  private static ClientCapabilities _capabilities;

  // can be "messages", "off", "verbose"
  private static String _trace = "off";

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
    ParserTool.SetJavaModules(Config.JavaModules());
  }

  private static List<String> JavaModules()
  {
    try
      {
        var modules = ((JsonObject) _configuration.get().get(0))
          .getAsJsonObject()
          .getAsJsonObject("java")
          .getAsJsonArray("modules");
        var result = List.<String>of();
        modules.forEach(jsonElement -> {
          result.add(jsonElement.getAsString());
        });
        return result;
      }
    catch (Exception e)
      {
        return List.of();
      }
  }

  public static void setTrace(String value)
  {
    _trace = value;
  }

  public static String getTrace()
  {
    return _trace;
  }

  public static void setClientCapabilities(ClientCapabilities capabilities)
  {
    _capabilities = capabilities;
  }

  public static ClientCapabilities getClientCapabilities()
  {
    return _capabilities;
  }


}
