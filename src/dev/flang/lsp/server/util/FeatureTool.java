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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Impl.Kind;
import dev.flang.lsp.server.ASTWalker;
import dev.flang.lsp.server.SourceText;
import dev.flang.lsp.server.Util;

public class FeatureTool {

  public static Stream<AbstractFeature> InheritedFeatures(AbstractFeature feature)
  {
    return feature.inherits().stream().flatMap(c -> {
      return Stream.concat(Stream.of(c.calledFeature()), InheritedFeatures(c.calledFeature()));
    });
  }

  public static Stream<AbstractFeature> outerFeatures(AbstractFeature feature)
  {
    if (feature.outer() == null)
      {
        return Stream.of(feature.outer());
      }
    return Stream.concat(Stream.of(feature.outer()), outerFeatures(feature.outer()))
      .filter(f -> f != null);
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
    return commentLines.stream().map(line -> line.trim()).collect(Collectors.joining(System.lineSeparator()));
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
    if (feature.pos().isBuiltIn())
      {
        return false;
      }
    return QueryAST.AllOf(FuzionParser.getUri(feature.pos()), AbstractFeature.class)
      .anyMatch(f -> f.arguments().contains(feature));
  }

  public static boolean IsAnonymousInnerFeature(AbstractFeature f)
  {
    return f.featureName().baseName().startsWith("#");
  }

  public static boolean IsIntrinsic(AbstractFeature feature)
  {
    return feature.implKind() == Kind.Intrinsic;
  }

  public static boolean IsFieldLike(AbstractFeature feature)
  {
    return Util.HashSetOf(Kind.Field, Kind.FieldActual, Kind.FieldDef, Kind.FieldInit, Kind.FieldIter)
      .contains(feature.implKind());
  }

  public static boolean IsRoutineOrRoutineDef(AbstractFeature feature)
  {
    return Util.HashSetOf(Kind.Routine, Kind.RoutineDef).contains(feature.implKind());
  }

}
