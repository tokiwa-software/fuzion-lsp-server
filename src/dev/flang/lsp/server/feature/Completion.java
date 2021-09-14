package dev.flang.lsp.server.feature;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InsertTextMode;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

import dev.flang.ast.Call;
import dev.flang.ast.Feature;
import dev.flang.ast.Type;
import dev.flang.ast.Types;
import dev.flang.ast.Feature;
import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.FuzionTextDocumentService;
import dev.flang.lsp.server.Log;
import dev.flang.lsp.server.Memory;
import dev.flang.lsp.server.Util;

public class Completion
{

  private static CompletionItem buildCompletionItem(String label, String insertText,
    CompletionItemKind completionItemKind)
  {
    var item = new CompletionItem(label);
    item.setKind(completionItemKind);
    item.setInsertTextFormat(InsertTextFormat.Snippet);
    item.setInsertTextMode(InsertTextMode.AdjustIndentation);
    item.setInsertText(insertText);
    return item;
  }

  public static Either<List<CompletionItem>, CompletionList> getCompletions(CompletionParams params)
  {
    var triggerCharacter = params.getContext().getTriggerCharacter();
    Log.write("completion triggered (" + triggerCharacter + ")");

    if (params.getContext().getTriggerKind() == CompletionTriggerKind.Invoked || ".".equals(triggerCharacter))
      {
        // NYI what do we actually want to/can do here?
        var existingASTItems = FuzionHelpers.getSuitableASTItems(params);
        Stream<CompletionItem> completionItems = (existingASTItems.isEmpty() ? Stream.of(Memory.Main) : existingASTItems.stream())
          .map(astItem -> {
            if (astItem instanceof Call)
              {
                var calledFeature = ((Call) astItem).calledFeature();
                if(calledFeature != Types.f_ERROR){
                  return calledFeature;
                }
                return Memory.Main;
              }
            if ((astItem instanceof Type && !((Type) astItem).isGenericArgument()))
              {
                return ((Type) astItem).featureOfType();
              }
            if ((astItem instanceof Feature))
              {
                return ((Feature) astItem);
              }
            return null;
          })
          .filter(o -> o != null)
          .flatMap(
            f -> {
              if (f.outer() == null)
                {
                  return f.declaredFeatures().values().stream();
                }
              return Stream.concat(f.declaredFeatures().values().stream(),
                f.outer().declaredFeatures().values().stream());
            })
          .distinct()
          // NYI use feature.isAnonymousInnerFeature() once it exists
          .filter(f -> !f.featureName().baseName().startsWith("#"))
          .map(
            feature -> buildCompletionItem(
              feature.toString(),
              feature.featureName().baseName(), CompletionItemKind.Class));
        return Either.forLeft(completionItems.collect(Collectors.toList()));
      }

    var word = getWord(params);
    switch (word)
      {
      case "for" :
        return Either.forLeft(Arrays.asList(buildCompletionItem("for i in start..end do",
          "for ${1:i} in ${2:0}..${3:10} do", CompletionItemKind.Snippet)));
      }
    return Either.forLeft(List.of());
  }

  private static @NonNull String getWord(CompletionParams params)
  {
    var text = FuzionTextDocumentService.getText(Util.getUri(params));
    var line = text.split("\n")[params.getPosition().getLine()];
    if (line.length() == 0)
      {
        return "";
      }
    var start = params.getPosition().getCharacter();
    do
      {
        --start;
      }
    while (start >= 0 && line.charAt(start) != ' ');
    var word = line.substring(start + 1, params.getPosition().getCharacter());
    return word;
  }

}
