package dev.flang.lsp.server;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

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

    System.setSecurityManager(new LSPSecurityManager());

    Config.setTransport(Arrays.stream(args).map(arg -> arg.trim().toLowerCase()).anyMatch("-tcp"::equals)
                                                                                                          ? Transport.tcp
                                                                                                          : Transport.stdio);
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
