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
import dev.flang.lsp.server.util.Bridge;
import dev.flang.lsp.server.util.QueryAST;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.LexerTool;
import dev.flang.shared.Util;

/**
 * tries offering completions
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_completion
 */
public class Completion
{
  private static final Either<List<CompletionItem>, CompletionList> NoCompletions = Either.forLeft(List.of());

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
    if (QueryAST.InString(params))
      {
        return NoCompletions;
      }

    var triggerCharacter = params.getContext().getTriggerCharacter();

    if (".".equals(triggerCharacter))
      {
        // not offering completion for number
        if (LexerTool
          .TokensAt(LexerTool.GoBackInLine(Bridge.ToSourcePosition(params), 1), false)
          .left()
          .token() == Token.t_numliteral)
          {
            return NoCompletions;
          }
        return completions(QueryAST.CallCompletionsAt(params));
      }
    if (" ".equals(triggerCharacter))
      {
        var tokenBeforeTriggerCharacter =
          LexerTool.TokensAt(LexerTool
            .GoBackInLine(Bridge.ToSourcePosition(params), 1), false)
            .left()
            .token();
        if (tokenBeforeTriggerCharacter.equals(Token.t_for))
          {
            return Either.forLeft(Arrays.asList(
              buildCompletionItem("for in", "${1:i} in ${2:0}..${3:10} do", CompletionItemKind.Keyword),
              buildCompletionItem("for in while", "${1:i} in ${2:0}..${3:10} while ${4:} do",
                CompletionItemKind.Keyword),
              buildCompletionItem("for while", "i:=0, i+1 while ${4:} do", CompletionItemKind.Keyword),
              buildCompletionItem("for until else", "${1:i} in ${2:0}..${3:10} do"
                + System.lineSeparator() + "until ${4:}"
                + System.lineSeparator() + "else ${4:}",
                CompletionItemKind.Keyword)));
          }


        var validTokens = new Token[]
          {
              Token.t_ident,
              Token.t_numliteral,
              Token.t_rbrace,
              Token.t_rcrochet,
              Token.t_rparen,
              Token.t_stringQQ,
              Token.t_StringDQ,
              Token.t_stringBQ
          };
        var set = Util.ArrayToSet(validTokens);
        if (set.contains(tokenBeforeTriggerCharacter))
          {
            return completions(QueryAST.InfixPostfixCompletionsAt(params));
          }
      }

    // Invoked: ctrl+space
    if (params.getContext().getTriggerKind().equals(CompletionTriggerKind.Invoked)
      && params.getContext().getTriggerCharacter() == null)
      {
        return completions(QueryAST.CompletionsAt(params));
      }
    return NoCompletions;
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
    // NYI postfix return additional text edit
    var baseNameReduced = feature
      .featureName()
      .baseName()
      .replaceFirst("^.*\\s", "");
    if (!feature.isRoutine())
      {
        return baseNameReduced;
      }

    var _generics = getGenerics(feature);

    var generics = genericsSnippet(feature, _generics);

    return baseNameReduced + generics + getArguments(feature.arguments());
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
        var argument = arguments.get(index);
        if (!argument.resultType().isFunType())
          {
            return " ${" + (index + 1) + ":" + argument.featureName().baseName() + "}";
          }

        return " (" +
          IntStream.range(1, argument.resultType().generics().size())
            .<String>mapToObj(
              x -> "${" + ((index + 1) * 100 + x - 1) + ":" + argument.resultType().generics().get(x).name() + "}")
            .collect(Collectors.joining(", "))
          + " -> ${" + ((index + 1) * 100 + argument.resultType().generics().size() - 1) + ":" + "r" + "})";

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
