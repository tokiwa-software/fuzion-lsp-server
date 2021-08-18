package dev.flang.lsp.server;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.io.IOException;
import java.net.ServerSocket;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

public class Main {

  enum Transport {
    stdio, tcp
  }

  public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {

    System.setProperty("FUZION_DISABLE_ANSI_ESCAPES", "true");

    var transport = Arrays.stream(args).map(arg -> arg.trim().toLowerCase()).anyMatch("-tcp"::equals) ? Transport.tcp
        : Transport.stdio;

    switch (transport) {
    case stdio:
      startStdIo();
      break;
    case tcp:
      startTcpServer();
      break;
    }
  }

  private static void startTcpServer() throws IOException, InterruptedException, ExecutionException {
    try (var serverSocket = new ServerSocket(0)) {

      System.out.println("socket opened on port: " + serverSocket.getLocalPort());
      var socket = serverSocket.accept();

      var server = new FuzionLanguageServer();
      var launcher = Launcher.createLauncher(server, LanguageClient.class, socket.getInputStream(),
          socket.getOutputStream());
      server.setClient(launcher.getRemoteProxy());
      launcher.startListening().get();
    }
  }

  private static void startStdIo() throws InterruptedException, ExecutionException {
    var server = new FuzionLanguageServer();
    var launcher = Launcher.createLauncher(server, LanguageClient.class, System.in, System.out);
    server.setClient(launcher.getRemoteProxy());

    launcher.startListening().get();
  }
}
