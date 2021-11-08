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
 * Source of class Converters
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server;

import java.net.URI;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Assign;
import dev.flang.ast.Block;
import dev.flang.ast.Call;
import dev.flang.ast.If;
import dev.flang.lsp.server.util.FuzionLexer;

/**
 * collection of static methods converting dev.flang objects to lsp4j
 */
public final class Converters
{

  /**
   * @param feature
   * @return example: array<T>(length i32, init Function<array.T, i32>) => array<array.T>
   */
  public static String ToLabel(AbstractFeature feature)
  {
    if (!FuzionHelpers.IsRoutineOrRoutineDef(feature))
      {
        return feature.featureName().baseName();
      }
    var arguments = "(" + feature.arguments()
      .stream()
      .map(a -> a.thisType().featureOfType().featureName().baseName() + " " + a.thisType().featureOfType().resultType())
      .collect(Collectors.joining(", ")) + ")";
    return feature.featureName().baseName() + feature.generics() + arguments + " => " + feature.resultType();
  }

  public static String ToLabel(Object item)
  {
    try
      {
        if (item instanceof If || item instanceof Block)
          {
            return "";
          }
        if (item instanceof Call c)
          {
            return c.calledFeature().qualifiedName();
          }
        if (item instanceof Assign a)
          {
            return a._assignedField.qualifiedName();
          }
        if (item instanceof AbstractFeature af)
          {
            return ToLabel(af);
          }
        return item.toString();
      }
    catch (Exception e)
      {
        return "";
      }
  }

  public static TextDocumentIdentifier TextDocumentIdentifier(URI uri)
  {
    return new TextDocumentIdentifier(uri.toString());
  }

  public static TextDocumentPositionParams TextDocumentPositionParams(URI uri, Position position)
  {
    return new TextDocumentPositionParams(TextDocumentIdentifier(uri), position);
  }

  public static TextDocumentPositionParams TextDocumentPositionParams(URI uri, int line, int character)
  {
    return TextDocumentPositionParams(uri, new Position(line, character));
  }

}
