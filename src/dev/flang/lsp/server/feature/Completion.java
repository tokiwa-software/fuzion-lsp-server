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
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InsertTextMode;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.lsp.server.util.Bridge;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.LexerTool;
import dev.flang.shared.ParserTool;
import dev.flang.shared.QueryAST;
import dev.flang.shared.Util;

/**
 * tries offering completions
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_completion
 */
public class Completion
{
  public enum TriggerCharacters
  {
    Dot("."), // calls
    Space(" "); // infix, postfix, types

    private final String triggerChar;

    private TriggerCharacters(String s)
    {
      triggerChar = s;
    }

    public String toString()
    {
      return this.triggerChar;
    }
  }

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
    var pos = Bridge.ToSourcePosition(params);
    if (QueryAST.InString(pos))
      {
        return NoCompletions;
      }

    var triggerCharacter = params.getContext().getTriggerCharacter();

    if (".".equals(triggerCharacter))
      {
        // not offering completion for number
        if (LexerTool
          .TokensAt(LexerTool.GoLeft(pos))
          .left()
          .token() == Token.t_numliteral)
          {
            return NoCompletions;
          }
        return completions(QueryAST.DotCallCompletionsAt(pos));
      }
    if (" ".equals(triggerCharacter))
      {
        var tokenBeforeTriggerCharacter =
          LexerTool.TokensAt(LexerTool
            .GoLeft(pos))
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
            // NYI better heuristic to check if we should offer infix/postfix
            // completion or types or keywords or nothing
            var result = NoCompletions;
            // no errors in line before pos?
            if (!ParserTool.Errors(ParserTool.getUri(pos))
              .anyMatch(x -> x.pos._line == pos._line && x.pos._column <= pos._column))
              {
                result = completions(QueryAST
                  .InfixPostfixCompletionsAt(
                    pos));
              }
            if (result.getLeft().isEmpty() && tokenBeforeTriggerCharacter.equals(Token.t_ident))
              {
                var types = QueryAST
                  .FeaturesInScope(pos)
                  .filter(af -> af.isConstructor() || af.isChoice())
                  .filter(af -> !af.featureName().baseName().contains(" "))
                  // NYI consider generics
                  .map(af -> af.thisType().name())
                  .distinct()
                  .map(name -> buildCompletionItem(name, name, CompletionItemKind.TypeParameter));

                var keywords = Stream.of(buildCompletionItem("is", "is", CompletionItemKind.Keyword));

                result = Either.forLeft(Stream.concat(keywords, types)
                  .collect(Collectors.toList()));
              }
            return result;
          }
      }

    // // Invoked: ctrl+space
    // if
    // (params.getContext().getTriggerKind().equals(CompletionTriggerKind.Invoked)
    // && params.getContext().getTriggerCharacter() == null)
    // {
    // return completions(QueryAST.CompletionsAt(pos));
    // }
    return NoCompletions;
  }

  private static Either<List<CompletionItem>, CompletionList> completions(Stream<AbstractFeature> features)
  {
    var collectedFeatures = features
      .distinct()
      .collect(Collectors.toList());

    var completionItems = IntStream
      .range(0, collectedFeatures.size())
      .mapToObj(
        index -> {
          var feature = collectedFeatures.get(index);
          return buildCompletionItem(
            FeatureTool.Label(feature),
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

    return baseNameReduced + getArguments(feature.valueArguments());
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
        return getFunArgument((index + 1) * 100, argument.resultType().generics());
      })
      .collect(Collectors.joining());
  }

  /**
   * @param offset
   * @param funArgs
   * @return example: (${101:H} -> ${102:B})
   */
  private static String getFunArgument(int offset, List<AbstractType> funArgs)
  {
    return " (" +
      IntStream.range(1, funArgs.size())
        .<String>mapToObj(
          x -> {
            return argPlaceholder(funArgs.get(x), x, offset);
          })
        .collect(Collectors.joining(", "))
      // result
      + " -> " + argPlaceholder(funArgs.get(0), funArgs.size(), offset) + ")";
  }

  /**
   * @param arg
   * @param x
   * @param offset
   * @return if arg not funType returns something like ${1:x}
   */
  private static String argPlaceholder(AbstractType arg, int x, int offset)
  {
    if (arg.isFunType())
      {
        return getFunArgument(offset * 100, arg.generics());
      }
    return "${" + (offset + x) + ":" + arg.name() + "}";
  }

}
