package dev.flang.lsp.server;
import java.util.Arrays;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InsertTextMode;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class Completion {

  private static CompletionItem buildSnippet(String label, String insertText) {
    var item = new CompletionItem(label);
    item.setKind(CompletionItemKind.Snippet);
    item.setInsertTextFormat(InsertTextFormat.Snippet);
    item.setInsertTextMode(InsertTextMode.AdjustIndentation);
    item.setInsertText(insertText);
    return item;
  }

  public static Either<List<CompletionItem>, CompletionList> getCompletions(CompletionParams params) {
    var word = getWord(params);
    switch (word) {
    case "for":
      return Either.forLeft(Arrays.asList(buildSnippet("for i in start..end do", "for ${1:i} in ${2:0}..${3:10} do")));
    }
    return Either.forLeft(List.of());
  }

  private static @NonNull String getWord(CompletionParams params) {
    var text = FuzionTextDocumentService.getText(params.getTextDocument().getUri());
    var line = text.split("\n")[params.getPosition().getLine()];
    if(line.length() == 0){
      return "";
    }
    var start = params.getPosition().getCharacter();
    do {
      --start;
    } while (start >= 0 && line.charAt(start) != ' ');
    var word = line.substring(start + 1, params.getPosition().getCharacter());
    return word;
  }

}
