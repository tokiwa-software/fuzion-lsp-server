package dev.flang.lsp.server.feature;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InsertTextMode;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

import dev.flang.ast.Feature;
import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.FuzionTextDocumentService;
import dev.flang.lsp.server.Memory;
import dev.flang.lsp.server.Util;

public class Completion
{

  private static CompletionItem buildCompletionItem(String label, String insertText,
    CompletionItemKind completionItemKind)
  {
    return buildCompletionItem(label, insertText, completionItemKind, null);
  }

  private static CompletionItem buildCompletionItem(String label, String insertText,
    CompletionItemKind completionItemKind, String sortText)
  {
    var item = new CompletionItem(label);
    item.setKind(completionItemKind);
    item.setInsertTextFormat(InsertTextFormat.Snippet);
    item.setInsertTextMode(InsertTextMode.AdjustIndentation);
    item.setInsertText(insertText);
    if (sortText != null)
      {
        item.setSortText(sortText);
      }
    return item;
  }

  public static Either<List<CompletionItem>, CompletionList> getCompletions(CompletionParams params)
  {
    var triggerCharacter = params.getContext().getTriggerCharacter();

    if (params.getContext().getTriggerKind() == CompletionTriggerKind.Invoked || ".".equals(triggerCharacter))
      {
        var universe = Stream.of(Memory.Main.universe());

        var sortedFeatures = Stream.of(
          FuzionHelpers.getCalledFeatures(params),
          FuzionHelpers.getParentFeatures(params),
          universe)
          .reduce(Stream::concat)
          .get()
          .flatMap(f -> f.declaredFeatures().values().stream())
          .distinct()
          .filter(f -> !FuzionHelpers.IsAnonymousInnerFeature(f))
          .collect(Collectors.toList());

        var completionItems = IntStream
          .range(0, sortedFeatures.size())
          .mapToObj(
            index -> {
              var feature = sortedFeatures.get(index);
              return buildCompletionItem(
                FuzionHelpers.getLabel(feature),
                getSnippet(feature), CompletionItemKind.Function, String.format("%10d", index));
            });

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

  /**
   * @param feature
   * @return example: array<T>(${1:length}, ${1:init})
   */
  private static String getSnippet(Feature feature)
  {
    if (!FuzionHelpers.IsRoutineOrRoutineDef(feature))
      {
        return feature.featureName().baseName();
      }
    var arguments = "(" + IntStream
      .range(0, feature.arguments.size())
      .mapToObj(index -> {
        var argument = feature.arguments.get(index);
        return "${" + index + ":" + argument.thisType().featureOfType().featureName().baseName() + "}";
      })
      .collect(Collectors.joining(", ")) + ")";
    return feature.featureName().baseName() + feature.generics + arguments;
  }

  private static @NonNull String getWord(TextDocumentPositionParams params)
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
