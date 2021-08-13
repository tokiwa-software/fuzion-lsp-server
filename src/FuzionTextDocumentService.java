import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

public class FuzionTextDocumentService implements TextDocumentService {

  @Override
  public void didOpen(DidOpenTextDocumentParams params) {
    System.out.println("didOpen");
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params) {
    System.out.println("didChange");

  }

  @Override
  public void didClose(DidCloseTextDocumentParams params) {
    System.out.println("didClose");
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params) {
    // TODO Auto-generated method stub

  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
    System.out.println("completion line: " + position.getPosition().getLine());
		return CompletableFuture.supplyAsync(() -> null);
	}

}
