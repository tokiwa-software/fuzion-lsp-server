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
import java.util.stream.Collectors;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;

import com.google.gson.JsonObject;

import dev.flang.lsp.server.enums.Transport;
import dev.flang.shared.Context;
import dev.flang.shared.ErrorHandling;
import dev.flang.shared.ParserTool;
import dev.flang.shared.Util;
import dev.flang.util.FuzionOptions;

public class Config
{

  public static final boolean ComputeAsync = true;
  public static final long DIAGNOSTICS_DEBOUNCE_DELAY_MS = 1000;
  private static LanguageClient _languageClient;
  private static Transport _transport;
  private static ClientCapabilities _capabilities;

  // can be "messages", "off", "verbose"
  private static String _trace = "off";
  private static int serverPort;

  /**
   * @return the serverPort
   */
  public static int getServerPort()
  {
    return serverPort;
  }

  /**
   * @param serverPort the serverPort to set
   */
  public static void setServerPort(int serverPort)
  {
    Config.serverPort = serverPort;
  }

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

  public static void setConfiguration(List<Object> configuration)
  {
    ParserTool.SetJavaModules(Config.JavaModules(configuration));
    SetFuzionOptions(configuration);
  }

  private static void SetFuzionOptions(List<Object> configuration)
  {
    try
      {
        var options = ((JsonObject) configuration.get(0))
          .getAsJsonObject("options");

        Context.FuzionOptions = new FuzionOptions(
          ErrorHandling.ResultOrDefault(() -> options.get("verbosity").getAsInt(), 0),
          ErrorHandling.ResultOrDefault(() -> options.get("debugLevel").getAsInt(), 0),
          ErrorHandling.ResultOrDefault(() -> options.get("safety").getAsBoolean(), true));

        Context.Logger.Log("[Config] FuzionOptions: verbosity(" + Context.FuzionOptions.verbose() + "), debugLevel("
          + Context.FuzionOptions.fuzionDebugLevel() + "), safety(" + Context.FuzionOptions.fuzionSafety() + ").");
      }
    catch (Exception e)
      {
        Context.Logger.Error("[Config] parsing of fuzion options failed.");
      }
  }

  private static List<String> JavaModules(List<Object> configuration)
  {
    try
      {
        var modules = ((JsonObject) configuration.get(0))
          .getAsJsonObject("java")
          .getAsJsonArray("modules");

        var result = Util.StreamOf(modules.iterator())
          .map(x -> x.getAsString())
          .collect(Collectors.toUnmodifiableList());

        Context.Logger.Log("[Config] Java modules: " + result.stream().collect(Collectors.joining(", ")));
        return result;
      }
    catch (Exception e)
      {
        Context.Logger.Error("[Config] parsing of java modules failed.");
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
