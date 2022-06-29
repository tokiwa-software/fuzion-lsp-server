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
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Consts;
import dev.flang.ast.Types;
import dev.flang.util.ANY;
import dev.flang.util.FuzionConstants;
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

  public static String AST(URI uri)
  {
    var ast = ASTWalker.Traverse(uri)
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
   * @return the position of the name excluding prefix/infix/postfix etc.
   *
   * Examples:
   *
   * infix feature: ==
   * infix ==
   * ------^
   * formArgs feature x2:
   * feat(x1, x2 i32) is
   * ---------^
   * lambda arg i:
   * a := array<fraction<i32>> (n+1) (i -> 1 ⁄ 1)
   * ---------------------------------^
   */
  // NYI we should probably extend the parser to have save these position during
  // parsing
  public static SourcePosition BareNamePosition(AbstractFeature feature)
  {
    if (feature.featureName().baseName().contains(" "))
      {
        var baseNameParts = feature.featureName().baseName().split(" ", 2);
        return LexerTool
          .TokensFrom(feature.pos(), false)
          .dropWhile(tokenInfo -> !(baseNameParts[1].startsWith(tokenInfo.text())))
          .map(tokenInfo -> tokenInfo.start())
          .findFirst()
          .get();
      }
    var start = LexerTool
      .TokensFrom(feature.pos(), false)
      .map(x -> x.text().equals("->") || x.text().equals(":="))
      .findFirst()
      .orElse(false) ?
    // NYI HACK start lexing at start of line since
    // pos of lambda arg is the pos of the lambda arrow (->).
    // and destructed pos is pos of caching operator :=
                     new SourcePosition(feature.pos()._sourceFile, feature.pos()._line, 1)
                     : feature.pos();

    return LexerTool
      .TokensFrom(start, false)
      .dropWhile(tokenInfo -> !tokenInfo.text().equals(feature.featureName().baseName()))
      .map(tokenInfo -> tokenInfo.start())
      .findFirst()
      .get();
  }

  /**
   * strips leading infix/prefix/postfix etc.
   * @param f
   * @return
   */
  public static String BareName(AbstractFeature f)
  {
    if (f.featureName().baseName().contains(" "))
      {
        return f.featureName().baseName().substring(f.featureName().baseName().indexOf(" ") + 1);
      }
    return f.featureName().baseName();
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
    // NYI this is a hack!
    return f.resultType().equals(Types.t_ADDRESS)
      || f.featureName().baseName().startsWith("@")
      || f.featureName().baseName().equals("result")
      || f.featureName()
        .baseName()
        .contains(FuzionConstants.INTERNAL_NAME_PREFIX) // Confusingly # is not
                                                        // just used as prefix
      || f.visibility().toString().contains(Consts.VISIBILITY_INVISIBLE.toString())
      || (f.featureName().baseName().equals("call") && IsInternal(f.outer())) // lambda
      // NYI hack
      || (f.featureName().baseName().matches("a\\d+")
        && f.outer().featureName().baseName().equals("call")) // autogenerated
                                                              // lambda args
    ;
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
    return Util.ConcatStreams(
      Stream.of(feature),
      outerFeatures(feature),
      feature.inherits().stream().map(c -> c.calledFeature()))
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
      .filter(obj -> obj instanceof AbstractCall)
      .map(obj -> ((AbstractCall) obj).calledFeature())
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
          "  " + Quote(caller.qualifiedName()) + " -> " + Quote(f.qualifiedName()) + ";" + System.lineSeparator());
      }
    for(AbstractFeature callee : Callees(f))
      {
        sb.append(
          "  " + Quote(f.qualifiedName()) + " -> " + Quote(callee.qualifiedName()) + ";" + System.lineSeparator());
      }
    sb.append("}");
    return sb.toString();
  }

  private static String Quote(String qualifiedName)
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

  public static boolean IsUsedInChoice(AbstractFeature af)
  {
    var uri = ParserTool.getUri(af.pos());
    var result = ASTWalker.Traverse(uri)
      .anyMatch(x -> x.getKey() instanceof AbstractFeature f &&
        (FeatureIsChoiceMember(f.thisType(), af)
          ||
          f.hasResultField() && FeatureIsChoiceMember(f.resultType(), af)));
    return result;
  }

  private static boolean FeatureIsChoiceMember(AbstractType at, AbstractFeature af)
  {
    return at.isChoice()
      && at.choiceGenerics()
        .stream()
        .anyMatch(t -> t.equals(af.thisType()));
  }

  public static boolean DoesInherit(AbstractFeature af)
  {
    return af.inherits().stream().anyMatch(x -> !x.calledFeature().qualifiedName().equals("Object"));
  }



}
