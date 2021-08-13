import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.io.IOException;
import java.net.ServerSocket;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

public class Main {
  public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {
    FuzionLanguageServer server = new FuzionLanguageServer();
    var port = 5678;
    var serverSocket = new ServerSocket(port);
    System.out.println("socket opened " + port);
    var socket = serverSocket.accept();
    Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, socket.getInputStream(), socket.getOutputStream());
    Future<?> startListening = launcher.startListening();
    startListening.get();
    serverSocket.close();
  }
}
