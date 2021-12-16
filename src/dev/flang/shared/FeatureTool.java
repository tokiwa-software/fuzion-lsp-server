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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    return f.featureName().baseName().startsWith("#");
  }

  public static Optional<AbstractFeature> universe(AbstractFeature f)
  {
    if (f == Types.f_ERROR)
      {
        return Optional.empty();
      }
    if (f.isUniverse())
      {
        return Optional.of(f);
      }
    return universe(f.outer());
  }

  static Optional<AbstractFeature> Main(AbstractFeature f)
  {
    if (f.outer() == Types.f_ERROR)
      {
        return Optional.empty();
      }
    if (f.outer().isUniverse())
      {
        return Optional.of(f);
      }
    return Main(f.outer());
  }

  /**
   * @param feature
   * @return example: array<T>(length i32, init Function<array.T, i32>) => array<array.T>
   */
  public static String ToLabel(AbstractFeature feature)
  {
    if (feature.resultType().isChoice())
      {
        return "choice" + "<"
          + feature.resultType()
            .choiceGenerics()
            .stream()
            .map(t -> t.featureOfType().featureName().baseName())
            .collect(Collectors.joining(", "))
          + ">";
      }
    if (feature.isField())
      {
        return Label(feature.resultType()) + feature.featureName().baseName();
      }
    if (feature.isRoutine())
      {
        var arguments = "(" + feature.arguments()
          .stream()
          .map(a -> a.thisType().featureOfType().featureName().baseName() + " " + Label(a.resultType()))
          .collect(Collectors.joining(", ")) + ")";
        return feature.featureName().baseName() + feature.generics() + arguments + " => " + Label(feature.resultType());
      }
    return feature.featureName().baseName();
  }

  private static String Label(AbstractType type)
  {
    // NYI don't rely on astType
    return type.astType().toString();
  }

  public static String CommentOfInMarkdown(AbstractFeature f)
  {
    if (PRECONDITIONS)
      require(!f.pos().isBuiltIn());
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

    return !IsInternal(feature) && DeclaredFeaturesRecursive(Main(feature).get())
      .noneMatch(f -> f.pos()._line > feature.pos()._line
        && f.pos()._column <= feature.pos()._column);
  }

  public static boolean IsInternal(AbstractFeature f)
  {
    // NYI maybe there is a better way?
    return Util.HashSetOf("Object", "Function", "call").contains(f.featureName().baseName());
  }


}
