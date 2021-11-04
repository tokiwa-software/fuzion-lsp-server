/*

This file is part of the Fuzion language server protocol implementation.

The Fuzion language server protocol implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language server protocol implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa Software GmbH, Germany
 *
 * Source of class Completion
 *
 *---------------------------------------------------------------------*/

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
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.ast.AbstractFeature;
import dev.flang.lsp.server.Converters;
import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.LexerUtil;
import dev.flang.lsp.server.ParserHelper;

/**
 * tries offering completions
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_completion
 */
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

    if (".".equals(triggerCharacter))
      {
        return completions(FuzionHelpers.featuresIncludingInheritedFeatures(params));
      }
    if (params.getContext().getTriggerKind() == CompletionTriggerKind.Invoked)
      {
        // NYI can we do better here?
        return completions(Stream.of(ParserHelper.universe(params)));
      }

    // NYI FIXME we need to move the cursor one step back
    // before getting next token
    var tokenText = LexerUtil.rawTokenAt(params).text();
    switch (tokenText)
      {
        case "for" :
          return Either.forLeft(Arrays.asList(buildCompletionItem("for i in start..end do",
            "for ${1:i} in ${2:0}..${3:10} do", CompletionItemKind.Snippet)));
      }
    return Either.forLeft(List.of());
  }

  private static Either<List<CompletionItem>, CompletionList> completions(Stream<AbstractFeature> features)
  {
    var sortedFeatures = features
      .flatMap(f -> ParserHelper.DeclaredFeatures(f))
      .distinct()
      .filter(f -> !FuzionHelpers.IsAnonymousInnerFeature(f))
      .collect(Collectors.toList());

    var completionItems = IntStream
      .range(0, sortedFeatures.size())
      .mapToObj(
        index -> {
          var feature = sortedFeatures.get(index);
          return buildCompletionItem(
            Converters.ToLabel(feature),
            getInsertText(feature), CompletionItemKind.Function, String.format("%10d", index));
        });

    return Either.forLeft(completionItems.collect(Collectors.toList()));
  }

  /**
   * @param feature
   * @return example: psMap<${4:K -> ordered<psMap.K>}, ${5:V}>(${1:data}, ${2:size}, ${3:fill})
   */
  private static String getInsertText(AbstractFeature feature)
  {
    if (!FuzionHelpers.IsRoutineOrRoutineDef(feature))
      {
        return feature.featureName().baseName();
      }

    var arguments = "(" + getArguments(feature.arguments()) + ")";

    var _generics = getGenerics(feature);

    var generics = genericsSnippet(feature, _generics);

    return feature.featureName().baseName() + generics + arguments;
  }

  /**
   * @return ${4:K -> ordered<psMap.K>}, ${5:V}
   */
  private static String getGenerics(AbstractFeature feature)
  {
    var _generics = IntStream
      .range(0, feature.generics().list.size())
      .mapToObj(index -> {
        return "${" + (index + 1 + feature.arguments().size()) + ":" + feature.generics().list.get(index).toString()
          + "}";
      })
      .collect(Collectors.joining(", "));
    return _generics;
  }

  /**
   * @param arguments
   * @return ${1:data}, ${2:size}, ${3:fill}
   */
  private static String getArguments(List<AbstractFeature> arguments)
  {
    return IntStream
      .range(0, arguments.size())
      .<String>mapToObj(index -> {
        var argument = arguments.get(index).thisType().featureOfType();
        if (argument.thisType().featureOfType().resultType().name != "Function")
          {
            return "${" + (index + 1) + ":" + argument.featureName().baseName() + "}";
          }
        return "${" + (index + 1) + ":} ->";
      })
      .collect(Collectors.joining(", "));
  }

  /**
   *
   * @param feature
   * @param _generics
   * @return <${4:K -> ordered<psMap.K>}, ${5:V}>
   */
  private static String genericsSnippet(AbstractFeature feature, String _generics)
  {
    if (!feature.generics().isOpen() && feature.generics().list.isEmpty())
      {
        return "";
      }
    return "<" + _generics
      + (feature.generics().isOpen() ? "...": "")
      + ">";
  }

}
