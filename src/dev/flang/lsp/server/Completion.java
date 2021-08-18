package dev.flang.lsp.server;
import java.util.Arrays;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class Completion {

  private static CompletionItem createTextCompletionItem(String label, Object data) {
    var item = new CompletionItem(label);
    item.setKind(CompletionItemKind.Text);
    item.setData(data);
    return item;
  }

  public static Either<List<CompletionItem>, CompletionList> getCompletions(CompletionParams position) {
    List<CompletionItem> completionItems = Arrays.asList(createTextCompletionItem("test1", 1));
    return Either.forLeft(completionItems);
  }

}
