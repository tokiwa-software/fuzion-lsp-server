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
import dev.flang.util.SourcePosition;

public class FeatureTool extends ANY
{
  public static Stream<AbstractFeature> outerFeatures(AbstractFeature feature)
  {
    if (feature.outer() == null)
      {
        return Stream.empty();
      }
    return Stream.concat(Stream.of(feature.outer()), outerFeatures(feature.outer()));
  }

  public static Stream<AbstractFeature> DeclaredFeaturesRecursive(AbstractFeature feature)
  {
    return Stream.concat(Stream.of(feature),
      FuzionParser.DeclaredFeatures(feature).flatMap(f -> DeclaredFeaturesRecursive(f)));
  }

  public static String CommentOf(AbstractFeature feature)
  {
    var line = feature.pos()._line - 1;
    var commentLines = new ArrayList<String>();
    while (true)
      {
        var pos = new SourcePosition(feature.pos()._sourceFile, line, 0);
        if (line < 1 || !FuzionLexer.isCommentLine(pos))
          {
            break;
          }
        commentLines.add(SourceText.LineAt(pos));
        line = line - 1;
      }
    Collections.reverse(commentLines);
    return commentLines
      .stream()
      .map(l -> l.trim())
      .map(l -> l
        .replaceAll("^#", "")
        .trim())
      .collect(Collectors.joining(System.lineSeparator()));
  }

  public static String AST(AbstractFeature start)
  {
    var ast = ASTWalker.Traverse(start)
      .map(x -> x.getKey())
      .sorted(ASTItem.CompareByLineThenByColumn())
      .reduce("", (a, item) -> {
        var position = ASTItem.sourcePosition(item);
        // NYI
        var indent = 0;
        if (position.isEmpty())
          {
            return a;
          }
        return a + System.lineSeparator()
          + " ".repeat(indent * 2) + position.get()._line + ":" + position.get()._column + ":"
          + Util.ShortName(item.getClass()) + ":" + ASTItem.ToLabel(item);
      }, String::concat);
    return ast;
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

  public static boolean IsAnonymousInnerFeature(AbstractFeature f)
  {
    // NYI use f.visibility()
    return f.featureName().baseName().startsWith("#");
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
    if (feature.resultType().isChoice())
      {
        if (feature.resultType()
          .choiceGenerics() == null)
          {
            return "choice<NYI>";
          }
      }
    if (feature.isField())
      {
        return feature.featureName().baseName() + " " + Label(feature.resultType());
      }
    if (feature.isRoutine())
      {
        // NYI if no arguments no parens
        var arguments = "(" + feature.arguments()
          .stream()
          .map(a -> a.thisType().featureOfType().featureName().baseName() + " " + Label(a.resultType()))
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
  public static String Label(AbstractType type)
  {
    // NYI don't rely on astType
    return type.astType().toString();
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
        return FuzionParser.DeclaredFeatures(f);
      });
  }

  /**
   *
   * @param feature
   * @return true iff there are no other features at same or lesser level after given feature
   */
  static boolean IsOfLastFeature(AbstractFeature feature)
  {
    return !IsInternal(feature) && DeclaredFeaturesRecursive(TopLevelFeature(feature).get())
      .noneMatch(f -> f.pos()._line > feature.pos()._line
        && f.pos()._column <= feature.pos()._column);
  }

  public static boolean IsInternal(AbstractFeature f)
  {
    // NYI maybe there is a better way?
    return Util.HashSetOf("Object", "Function", "call", "result").contains(f.featureName().baseName());
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
