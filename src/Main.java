import java.util.concurrent.ExecutionException;
import java.io.IOException;
import java.net.ServerSocket;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

public class Main {

  public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {

    try (var serverSocket = new ServerSocket(0)) {

      System.out.println("socket opened on port: " + serverSocket.getLocalPort());
      var socket = serverSocket.accept();

      FuzionLanguageServer server = new FuzionLanguageServer();
      var launcher = Launcher.createLauncher(server, LanguageClient.class, socket.getInputStream(),
          socket.getOutputStream());
      server.setClient(launcher.getRemoteProxy());

      launcher.startListening().get();

    }

  }
}
