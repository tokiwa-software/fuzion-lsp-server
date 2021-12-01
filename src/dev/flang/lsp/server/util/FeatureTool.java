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


package dev.flang.lsp.server.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Types;
import dev.flang.lsp.server.ASTWalker;
import dev.flang.lsp.server.SourceText;
import dev.flang.lsp.server.Util;

public class FeatureTool
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
    var textDocumentPosition = Bridge.ToTextDocumentPosition(feature.pos());
    var commentLines = new ArrayList<String>();
    while (true)
      {
        if (textDocumentPosition.getPosition().getLine() != 0)
          {
            var position = textDocumentPosition.getPosition();
            position.setLine(textDocumentPosition.getPosition().getLine() - 1);
            textDocumentPosition.setPosition(position);
          }
        else
          {
            break;
          }
        if (FuzionLexer.isCommentLine(textDocumentPosition))
          {
            commentLines.add(SourceText.LineAt(textDocumentPosition));
          }
        else
          {
            break;
          }
      }
    Collections.reverse(commentLines);
    return commentLines
      .stream()
      .map(line -> line.trim())
      .map(line -> line
        .replaceAll("^#", "")
        .trim())
      .collect(Collectors.joining(System.lineSeparator()));
  }

  public static String AST(AbstractFeature start)
  {
    var ast = ASTWalker.Traverse(start)
      .reduce("", (a, b) -> {
        var item = b.getKey();
        var position = ASTItem.sourcePosition(item);
        // NYI
        var indent = 0;
        if (position.isEmpty())
          {
            return a;
          }
        return a + System.lineSeparator()
          + " ".repeat(indent * 2) + position.get()._line + ":" + position.get()._column + ":"
          + item.getClass().getSimpleName() + ":" + ASTItem.ToLabel(item);
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

  static Optional<AbstractFeature> universe(AbstractFeature f)
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
    if (feature.isField())
      {
        return feature.featureName().baseName() + ": " + Label(feature.resultType());
      }
    if (!feature.isRoutine())
      {
        return feature.featureName().baseName();
      }
    var arguments = "(" + feature.arguments()
      .stream()
      .map(a -> a.thisType().featureOfType().featureName().baseName() + " " + Label(a.resultType()))
      .collect(Collectors.joining(", ")) + ")";
    return feature.featureName().baseName() + feature.generics() + arguments + " => " + Label(feature.resultType());
  }

  private static String Label(AbstractType type)
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
  static Stream<AbstractFeature> FeaturesInScope(AbstractFeature feature)
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

  static boolean IsInternal(AbstractFeature f)
  {
    // NYI maybe there is a better way?
    return Util.HashSetOf("Object", "Function", "call").contains(f.featureName().baseName());
  }


}
