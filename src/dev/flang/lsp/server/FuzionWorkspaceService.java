package dev.flang.lsp.server;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import com.google.gson.JsonPrimitive;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.services.WorkspaceService;

public class FuzionWorkspaceService implements WorkspaceService
{

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public CompletableFuture<Object> executeCommand(ExecuteCommandParams params)
  {
    var uri = ((JsonPrimitive) params.getArguments().get(0)).getAsString();

    switch (Commands.valueOf(params.getCommand()))
      {
      case showSyntaxTree :
        Util.RunInBackground(() -> showSyntaxTree(uri));
        return CompletableFuture.completedFuture(null);
      case evaluate :
        Util.RunInBackground(() -> evaluate(uri));
        return CompletableFuture.completedFuture(null);
      default:
        Util.WriteStackTrace(new Exception("not implemented"));
        return CompletableFuture.completedFuture(null);
      }
  }

  private void evaluate(String uri)
  {
    try
      {
        var result = FuzionHelpers.Run(uri);
        Config.languageClient().showMessage(result);
      }
    catch (IOException | InterruptedException | ExecutionException | TimeoutException | StackOverflowError e)
      {
        var message = e.getMessage();
        if (message != null)
          {
            Config.languageClient()
              .showMessage(new MessageParams(MessageType.Error, message));
          }
      }

  }

  private void showSyntaxTree(String uri)
  {
    var feature = FuzionHelpers.baseFeature(uri);
    if (feature.isEmpty())
      {
        CompletableFuture.completedFuture(null);
      }
    var ast = ASTPrinter.getAST(feature.get());
    var file = Util.writeToTempFile(ast, String.valueOf(System.currentTimeMillis()), ".fuzion.ast");
    Config.languageClient().showDocument(new ShowDocumentParams(file.toURI().toString()));
  }

}
