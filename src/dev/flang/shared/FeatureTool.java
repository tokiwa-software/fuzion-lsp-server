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
 * Source of class FeatureTool
 *
 *---------------------------------------------------------------------*/


package dev.flang.shared;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Types;
import dev.flang.util.ANY;
import dev.flang.util.FuzionConstants;
import dev.flang.util.SourcePosition;

public class FeatureTool extends ANY
{
  private static final long MAX_TOKENS_TO_INSPECT = 20;

  public static Stream<AbstractFeature> outerFeatures(AbstractFeature feature)
  {
    if (feature.outer() == null)
      {
        return Stream.empty();
      }
    return Stream.concat(Stream.of(feature.outer()), outerFeatures(feature.outer()));
  }

  public static Stream<AbstractFeature> SelfAndDescendants(AbstractFeature feature)
  {
    return Stream.concat(Stream.of(feature),
      ParserTool.DeclaredFeatures(feature).flatMap(f -> SelfAndDescendants(f)));
  }

  public static String CommentOf(AbstractFeature feature)
  {
    var line = feature.pos()._line - 1;
    var commentLines = new ArrayList<String>();
    while (true)
      {
        var pos = new SourcePosition(feature.pos()._sourceFile, line, 0);
        if (line < 1 || !LexerTool.isCommentLine(pos))
          {
            break;
          }
        commentLines.add(SourceText.LineAt(pos));
        line = line - 1;
      }
    Collections.reverse(commentLines);

    var commentsOfRedefinedFeatures = feature
      .redefines()
      .stream()
      .map(f -> System.lineSeparator() + "redefines " + f.qualifiedName() + ":" + System.lineSeparator() + CommentOf(f))
      .collect(Collectors.joining(System.lineSeparator()));

    return commentLines
      .stream()
      .map(l -> l.trim())
      .map(l -> l
        .replaceAll("^#", "")
        .trim())
      .collect(Collectors.joining(System.lineSeparator()))
      + commentsOfRedefinedFeatures;
  }

  public static String AST(AbstractFeature start)
  {
    var ast = ASTWalker.Traverse(start)
      .map(x -> x.getKey())
      .sorted(ASTItem.CompareByLineThenByColumn())
      .reduce("", (a, item) -> {
        var position = item.pos();
        // NYI
        var indent = 0;
        if (position.isBuiltIn())
          {
            return a;
          }
        return a + System.lineSeparator()
          + " ".repeat(indent * 2) + position._line + ":" + position._column + ":"
          + Util.ShortName(item.getClass()) + ":" + ASTItem.ToLabel(item);
      }, String::concat);
    return ast;
  }

  /**
   * @param feature
   * @return the position of the basename
   * examples:
   * infix feature: ==
   * infix ==
   * ------^
   * formArgs feature x2:
   * feat(x1, x2 i32) is
   * ---------^
   */
  public static SourcePosition BaseNamePosition(AbstractFeature feature)
  {
    return LexerTool
      .TokensFrom(feature.pos(), false)
      .limit(MAX_TOKENS_TO_INSPECT)
      .dropWhile(tokenInfo -> !tokenInfo.text().equals(feature.featureName().baseName()))
      .map(tokenInfo -> tokenInfo.start())
      .findFirst()
      .orElse(feature.pos());
  }

  public static boolean IsArgument(AbstractFeature feature)
  {
    if (feature.outer() == null)
      {
        return false;
      }
    return feature.outer()
      .arguments()
      .stream()
      .anyMatch(f -> f.equals(feature));
  }

  public static boolean IsInternal(AbstractFeature f)
  {
    // NYI use f.visibility()
    return f.resultType().equals(Types.t_ADDRESS)
      || f.featureName().baseName().startsWith(FuzionConstants.INTERNAL_NAME_PREFIX)
      || f.featureName().baseName().startsWith("@");
  }

  static Optional<AbstractFeature> TopLevelFeature(AbstractFeature f)
  {
    if (f.isUniverse() || f.outer() == Types.f_ERROR)
      {
        return Optional.empty();
      }
    if (f.outer().isUniverse() || !InSameSourceFile(f, f.outer()))
      {
        return Optional.of(f);
      }
    return TopLevelFeature(f.outer());
  }

  private static boolean InSameSourceFile(AbstractFeature a, AbstractFeature b)
  {
    return a.pos()._sourceFile.toString().equals(b.pos()._sourceFile.toString());
  }

  /**
   * @param feature
   * @return example: array<T>(length i32, init Function<array.T, i32>) => array<array.T>
   */
  public static String ToLabel(AbstractFeature feature)
  {
    if (feature.isField())
      {
        return feature.featureName().baseName() + " " + Label(feature.resultType());
      }
    if (feature.isRoutine())
      {
        // NYI if no arguments no parens
        var arguments = "(" + feature.arguments()
          .stream()
          .map(a -> {
            if (IsInternal(a))
              {
                return "_" + " " + Label(a.resultType());
              }
            return a.featureName().baseName() + " " + Label(a.resultType());
          })
          .collect(Collectors.joining(", ")) + ")";
        return feature.featureName().baseName() + feature.generics() + arguments + " => " + Label(feature.resultType())
          + LabelInherited(feature);
      }
    return feature.featureName().baseName() + LabelInherited(feature);
  }

  public static String LabelInherited(AbstractFeature feature)
  {
    if (feature.inherits().isEmpty())
      {
        return "";
      }
    return " : " + feature.inherits()
      .stream()
      .map(c -> c.calledFeature())
      .map(f -> f.featureName().baseName() + f.generics())
      .collect(Collectors.joining(", "));
  }

  // NYI move to TypeTool?
  private static String Label(AbstractType type)
  {
    if (type.isChoice() && type.choiceGenerics() == null)
      {
        return "choice<?>";
      }
    return type.toString();
  }

  public static String CommentOfInMarkdown(AbstractFeature f)
  {
    return MarkdownTool.Italic(MarkdownTool.Escape(CommentOf(f)));
  }

  /**
   * @param feature
   * @return all features which are accessible (callable) when inside of feature
   */
  public static Stream<AbstractFeature> FeaturesInScope(AbstractFeature feature)
  {
    return Stream
      .of(Stream.of(feature), outerFeatures(feature), feature.inherits().stream().map(c -> c.calledFeature()))
      .reduce(Stream::concat)
      .orElseGet(Stream::empty)
      .flatMap(f -> {
        return ParserTool.DeclaredFeatures(f);
      });
  }

  /**
   *
   * @param feature
   * @return true iff there are no other features at same or lesser level after given feature
   */
  static boolean IsOfLastFeature(AbstractFeature feature)
  {
    return !IsFunctionCall(feature) && SelfAndDescendants(TopLevelFeature(feature).get())
      .noneMatch(f -> f.pos()._line > feature.pos()._line
        && f.pos()._column <= feature.pos()._column);
  }

  private static boolean IsFunctionCall(AbstractFeature f)
  {
    return f.redefines().contains(Types.resolved.f_function_call);
  }

  private static Set<AbstractFeature> Callers(AbstractFeature f)
  {
    return CallsTo(f).map(x -> x.getValue()).collect(Collectors.toSet());
  }

  private static Set<AbstractFeature> Callees(AbstractFeature f)
  {
    return ASTWalker.Traverse(f, false)
      .map(e -> e.getKey())
      .filter(obj -> AbstractCall.class.isAssignableFrom(obj.getClass()))
      .<AbstractFeature>map(obj -> ((AbstractCall) obj).calledFeature())
      .collect(Collectors.toSet());
  }

  // NYI add heuristic for depth of call graph and optionally go deeper than
  // just one level
  // NYI better filtering of callers and callees
  public static String CallGraph(AbstractFeature f)
  {
    var sb = new StringBuilder("digraph {" + System.lineSeparator());
    for(AbstractFeature caller : Callers(f))
      {
        sb.append(
          "  " + Quoute(caller.qualifiedName()) + " -> " + Quoute(f.qualifiedName()) + ";" + System.lineSeparator());
      }
    for(AbstractFeature callee : Callees(f))
      {
        sb.append(
          "  " + Quoute(f.qualifiedName()) + " -> " + Quoute(callee.qualifiedName()) + ";" + System.lineSeparator());
      }
    sb.append("}");
    return sb.toString();
  }

  private static String Quoute(String qualifiedName)
  {
    return "\"" + qualifiedName + "\"";
  }

  public static String UniqueIdentifier(AbstractFeature f)
  {
    return f.qualifiedName() + f.arguments().size();
  }

  /**
   * @param feature
   * @return all calls to this feature and the feature those calls are happening in
   */
  public static Stream<SimpleEntry<AbstractCall, AbstractFeature>> CallsTo(AbstractFeature feature)
  {
    return ASTWalker.Calls(feature.universe())
      .filter(entry -> entry.getKey().calledFeature() != null
        && entry.getKey().calledFeature().equals(feature));
  }

}
