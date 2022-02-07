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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.ast.AbstractFeature;
import dev.flang.lsp.server.util.Bridge;
import dev.flang.lsp.server.util.QueryAST;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.FuzionLexer;

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
        return completions(QueryAST.CallCompletionsAt(params));
      }
    var token =
      FuzionLexer.rawTokenAt(Bridge.ToSourcePosition(new TextDocumentPositionParams(params.getTextDocument(),
        new Position(params.getPosition().getLine(), params.getPosition().getCharacter() - 1))));
    // NYI behaviour not satisfying
    if (token.text().equals("for"))
      {
        return Either.forLeft(Arrays.asList(
          buildCompletionItem("for in", "for ${1:i} in ${2:0}..${3:10} do", CompletionItemKind.Keyword),
          buildCompletionItem("for in while", "for ${1:i} in ${2:0}..${3:10} while ${4:} do",
            CompletionItemKind.Keyword),
          buildCompletionItem("for while", "for i:=0, i+1 while ${4:} do", CompletionItemKind.Keyword),
          buildCompletionItem("for until else", "for ${1:i} in ${2:0}..${3:10} do"
            + System.lineSeparator() + "until ${4:}"
            + System.lineSeparator() + "else ${4:}",
            CompletionItemKind.Keyword)));
      }
    // NYI behaviour not satisfying
    if (params.getContext().getTriggerKind().equals(CompletionTriggerKind.Invoked) && token.token().equals(Token.t_ws))
      {
        return completions(QueryAST.CompletionsAt(params));
      }
    return Either.forLeft(List.of());
  }

  private static Either<List<CompletionItem>, CompletionList> completions(Stream<AbstractFeature> features)
  {
    var collectedFeatures = features
      .collect(Collectors.toList());

    var completionItems = IntStream
      .range(0, collectedFeatures.size())
      .mapToObj(
        index -> {
          var feature = collectedFeatures.get(index);
          return buildCompletionItem(
            FeatureTool.ToLabel(feature),
            getInsertText(feature), CompletionItemKind.Function, String.format("%10d", index));
        });

    return Either.forLeft(completionItems.collect(Collectors.toList()));
  }

  /**
   * @param feature
   * @return example: psMap<${4:K -> ordered<psMap.K>}, ${5:V}> ${1:data} ${2:size} ${3:fill})
   */
  private static String getInsertText(AbstractFeature feature)
  {
    if (!feature.isRoutine())
      {
        return feature.featureName().baseName();
      }

    var _generics = getGenerics(feature);

    var generics = genericsSnippet(feature, _generics);

    return feature.featureName().baseName() + generics + getArguments(feature.arguments());
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
   * @return ${1:data} ${2:size} ${3:fill}
   */
  private static String getArguments(List<AbstractFeature> arguments)
  {
    return IntStream
      .range(0, arguments.size())
      .<String>mapToObj(index -> {
        var argument = arguments.get(index).thisType().featureOfType();
        if (!argument.resultType().isFunType())
          {
            return " ${" + (index + 1) + ":" + argument.featureName().baseName() + "}";
          }

        // NYI ${i:□}
        return " (" +
          IntStream.range(1, argument.resultType().generics().size())
            .<String>mapToObj(
              x -> "□")
            .collect(Collectors.joining(", "))
          + " -> " + "□" + ")";

      })
      .collect(Collectors.joining());
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
