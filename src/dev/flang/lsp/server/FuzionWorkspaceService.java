package dev.flang.lsp.server;

import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonPrimitive;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.services.WorkspaceService;

import dev.flang.be.interpreter.Interpreter;

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
    switch (Commands.valueOf(params.getCommand()))
      {
        case showSyntaxTree :
          showSyntaxTree(params);
          return CompletableFuture.completedFuture(null);
        case evaluate :
          evaluate(params);
          return CompletableFuture.completedFuture(null);
        default:
          Util.WriteStackTrace(new Exception("not implemented"));
          return CompletableFuture.completedFuture(null);
      }
  }

  private void evaluate(ExecuteCommandParams params)
  {
    var uri = ((JsonPrimitive) params.getArguments().get(0)).getAsString();
    var result = Util.WithCapturedStdOutErr(() ->{
      var interpreter = new Interpreter(ParserHelper.FUIR(uri));
      interpreter.run();
    }, 10000);
    Main.getLanguageClient().showMessage(result);
  }

  private void showSyntaxTree(ExecuteCommandParams params)
  {
    var uri = ((JsonPrimitive) params.getArguments().get(0)).getAsString();
    var feature = FuzionHelpers.baseFeature(uri);
    if (feature.isEmpty())
      {
        CompletableFuture.completedFuture(null);
      }
    var ast = ASTPrinter.getAST(feature.get());
    var file = Util.writeToTempFile(ast, Util.randomString(), ".fuzion.ast");
    Main.getLanguageClient().showDocument(new ShowDocumentParams(file.toURI().toString()));
  }

}
