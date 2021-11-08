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
 * Source of class Main
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.ServerSocket;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

import dev.flang.lsp.server.enums.Transport;
import dev.flang.util.Errors;

/**
 * Main Class of Fuzion LSP responsible for starting the language server.
 */
public class Main
{

  public static void main(String[] args) throws Exception
  {

    System.setProperty("FUZION_DISABLE_ANSI_ESCAPES", "true");
    Errors.MAX_ERROR_MESSAGES = Integer.MAX_VALUE;

    Config.setTransport(Arrays.stream(args).map(arg -> arg.trim().toLowerCase()).anyMatch("-tcp"::equals)
                                                                                                          ? Transport.tcp
                                                                                                          : Transport.stdio);

    Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread arg0, Throwable arg1)
      {
        Util.WriteStackTrace(arg1);
      }
    });

    var launcher = getLauncher();
    launcher.startListening();
    Config.setLanguageClient(launcher.getRemoteProxy());
  }

  private static Launcher<LanguageClient> getLauncher() throws InterruptedException, ExecutionException, IOException
  {
    var server = new FuzionLanguageServer();
    switch (Config.transport())
      {
        case stdio :
          return createLauncher(server, System.in, System.out);
        case tcp :
          try (var serverSocket = new ServerSocket(0))
            {
              System.out.println("Property os.name: " + System.getProperty("os.name"));
              System.out.println("socket opened on port: " + serverSocket.getLocalPort());
              var socket = serverSocket.accept();
              return createLauncher(server, socket.getInputStream(), socket.getOutputStream());
            }
        default:
          Util.WriteStackTraceAndExit(1);
          return null;
      }
  }

  private static Launcher<LanguageClient> createLauncher(FuzionLanguageServer server, InputStream in, OutputStream out)
    throws IOException
  {
    return new Launcher.Builder<LanguageClient>()
      .setLocalService(server)
      .setRemoteInterface(LanguageClient.class)
      .setInput(in)
      .setOutput(out)
      .setExceptionHandler((e) -> {
        Util.WriteStackTrace(e);
        return null;
      })
      .create();
  }


}
