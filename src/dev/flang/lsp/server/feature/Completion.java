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
import dev.flang.lsp.server.Log;
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
        Stream<Feature> features = getFeatures(params, triggerCharacter);

        var sortedFeatures = features
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
                getInsertText(feature), CompletionItemKind.Function, String.format("%10d", index));
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

  private static Stream<Feature> getFeatures(CompletionParams params, String triggerCharacter)
  {
    var universe = Stream.of(Memory.getMain().universe());

    Stream<Feature> features;
    if (".".equals(triggerCharacter))
      {
        var feature = FuzionHelpers.calledFeaturesSortedDesc(params)
          .map(x -> {
            return x.resultType().featureOfType();
          })
          .findFirst();

        if (feature.isEmpty())
          {
            Log.message("no feature to complete");
            return Stream.empty();
          }

        Log.message("completing for: " + feature.get().qualifiedName());

        features = Stream.concat(Stream.of(feature.get()), getInheritedFeatures(feature.get()));
      }
    else
      {
        features = Stream.of(
          FuzionHelpers.getParentFeatures(params),
          universe).reduce(Stream::concat).get();
      }
    return features;
  }

  private static Stream<Feature> getInheritedFeatures(Feature feature)
  {
    return feature.inherits.stream().flatMap(c -> {
      return Stream.concat(Stream.of(c.calledFeature()), getInheritedFeatures(c.calledFeature()));
    });
  }

  /**
   * @param feature
   * @return example: psMap<${4:K -> ordered<psMap.K>}, ${5:V}>(${1:data}, ${2:size}, ${3:fill})
   */
  private static String getInsertText(Feature feature)
  {
    if (!FuzionHelpers.IsRoutineOrRoutineDef(feature))
      {
        return feature.featureName().baseName();
      }

    // ${1:data}, ${2:size}, ${3:fill}
    var arguments = "(" + getArguments(feature.arguments) + ")";

    // ${4:K -> ordered<psMap.K>}, ${5:V}
    var _generics = getGenerics(feature);

    // <${4:K -> ordered<psMap.K>}, ${5:V}>
    var generics = genericsSnippet(feature, _generics);

    Log.message(feature.featureName().baseName() + generics + arguments);
    return feature.featureName().baseName() + generics + arguments;
  }

  private static String getGenerics(Feature feature)
  {
    var _generics = IntStream
      .range(0, feature.generics.list.size())
      .mapToObj(index -> {
        return "${" + (index + 1 + feature.arguments.size()) + ":" + feature.generics.list.get(index).toString() + "}";
      })
      .collect(Collectors.joining(", "));
    return _generics;
  }

  private static String getArguments(List<Feature> arguments)
  {
    return IntStream
      .range(0, arguments.size())
      .<String>mapToObj(index -> {
        var argument = arguments.get(index).thisType().featureOfType();
        if(argument.thisType().featureOfType().resultType().name != "Function"){
          return "${" + (index + 1) + ":" + argument.featureName().baseName() + "}";
        }
        return "fun (${" + (index + 1) + ":}) =>";
      })
      .collect(Collectors.joining(", "));
  }

  private static String genericsSnippet(Feature feature, String _generics)
  {
    if (!feature.generics.isOpen() && feature.generics.list.isEmpty())
      {
        return "";
      }
    return "<" + _generics
      + (feature.generics.isOpen() ? "...": "")
      + ">";
  }

  private static @NonNull String getWord(TextDocumentPositionParams params)
  {
    var text = FuzionTextDocumentService.getText(Util.getUri(params));
    var line = text.split("\\R", -1)[params.getPosition().getLine()];
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
