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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

import dev.flang.lsp.server.enums.Transport;
import dev.flang.lsp.server.util.LSP4jLogger;
import dev.flang.shared.Concurrency;
import dev.flang.shared.Context;
import dev.flang.shared.ErrorHandling;
import dev.flang.shared.IO;
import dev.flang.util.Errors;

/**
 * Main Class of Fuzion LSP responsible for starting the language server.
 */
public class Main
{
  private static String[] arguments;

  public static void main(String[] args) throws Exception
  {
    arguments = args;

    IO.Init(line -> {
      if (Config.languageClient() != null)
        Config.languageClient().logMessage(new MessageParams(MessageType.Log, "out: " + line));
    }, line -> {
      if (Config.languageClient() != null)
        Config.languageClient().logMessage(new MessageParams(MessageType.Error, "err: " + line));
    });

    Context.Logger = new LSP4jLogger();

    System.setProperty("FUZION_DISABLE_ANSI_ESCAPES", "true");
    Errors.MAX_ERROR_MESSAGES = Integer.MAX_VALUE;

    /*
    Servers usually support different communication channels (e.g. stdio, pipes, …). To ease the usage of servers in different clients it is highly recommended that a server implementation supports the following command line arguments to pick the communication channel:

    stdio: uses stdio as the communication channel.
    pipe: use pipes (Windows) or socket files (Linux, Mac) as the communication channel. The pipe / socket file name is passed as the next arg or with --pipe=.
    socket: uses a socket as the communication channel. The port is passed as next arg or with --port=.
    node-ipc: use node IPC communication between the client and the server. This is only support if both client and server run under node.

    https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#implementationConsiderations
     */

    if (HasArg("-stdio"))
      {

      }
    else if (HasArg("-pipe"))
      {
        // NYI
        PrintUsageAndExit();
      }
    else if (HasArg("-socket"))
      {
        Config.setTransport(Transport.socket);
        var port = GetArg("--port");
        if (port.isEmpty())
          {
            PrintUsageAndExit();
            return;
          }
        Config.setServerPort(Integer.parseInt(port.get()));
      }
    else
      {
        PrintUsageAndExit();
      }


    Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread arg0, Throwable arg1)
      {
        ErrorHandling.WriteStackTrace(arg1);
      }
    });

    var launcher = getLauncher();
    launcher.startListening();
    var languageClient = launcher.getRemoteProxy();

    Config.setLanguageClient(languageClient);

  }

  private static void PrintUsageAndExit()
  {
    IO.SYS_ERR.println("usage: [-stdio,-socket --port=]");
    System.exit(1);
  }

  private static boolean HasArg(String string)
  {
    return Arrays.stream(arguments).map(arg -> arg.trim()).anyMatch(string::equals);
  }

  private static Optional<String> GetArg(String string)
  {
    return Arrays.stream(arguments)
      .map(arg -> arg.trim())
      .filter(arg -> arg.startsWith(string))
      .findAny()
      .map(x -> x.split("=")[1]);
  }

  private static Launcher<LanguageClient> getLauncher() throws InterruptedException, ExecutionException, IOException
  {
    var server = new FuzionLanguageServer();
    switch (Config.transport())
      {
      case stdio :
        return createLauncher(server, IO.SYS_IN, IO.SYS_OUT);
      case socket :
        try (var serverSocket = new ServerSocket(Config.getServerPort()))
          {
            IO.SYS_OUT.println("Property os.name: " + System.getProperty("os.name"));
            IO.SYS_OUT.println("socket opened on port: " + serverSocket.getLocalPort());
            var socket = serverSocket.accept();
            return createLauncher(server, socket.getInputStream(), socket.getOutputStream());
          }
      default:
        ErrorHandling.WriteStackTraceAndExit(1);
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
      .setExecutorService(Concurrency.MainExecutor)
      .setExceptionHandler((e) -> {
        ErrorHandling.WriteStackTrace(e);
        return null;
      })
      .create();
  }


}
