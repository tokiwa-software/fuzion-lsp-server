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
 * Source of class TypeTool
 *
 *---------------------------------------------------------------------*/


package dev.flang.shared;

import java.util.stream.Collectors;

import dev.flang.ast.AbstractType;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.Type;
import dev.flang.ast.Types;
import dev.flang.util.ANY;

public class TypeTool extends ANY
{
  /**
   * human readalbe label for type
   * @param type
   * @return
   */
  public static String Label(AbstractType type)
  {

    if (ContainsError(type)
      || type.containsUndefined(false))
      {
        return type.name();
      }
    if (!type.isGenericArgument() && type.generics() != Type.NONE)
      {
        return LabelNoErrorOrUndefined(type) + " "
          + type.generics().stream().map(g -> Util.AddParens(Label(g))).collect(Collectors.joining(" "));
      }
    return LabelNoErrorOrUndefined(type);
  }

  // NYI DUCKTAPE! ensure condition sometimes fails on containsError()
  // unable to reproduce unfortunately
  public static boolean ContainsError(AbstractType type)
  {
    return ErrorHandling.ResultOrDefault(() -> type.containsError(), true);
  }

  /**
   * human readable label for formal generics.
   * @param generics
   * @param brief
   * @return
   */
  public static String Label(FormalGenerics generics, boolean brief)
  {
    if (!generics.isOpen() && generics.list.isEmpty())
      {
        return "";
      }
    if (brief)
      {
        return "<>";
      }
    return " " + generics.list + (generics.isOpen() ? "... ": "");
  }

  private static String LabelNoErrorOrUndefined(AbstractType type)
  {
    if (PRECONDITIONS)
      require(!ContainsError(type), !type.containsUndefined(false));

    if (type.isGenericArgument())
      {
        return type.name() + (type.isRef() ? " (boxed)": "");
      }
    else if (type.outer() != null)
      {
        return (type.isRef() && (type.featureOfType() == null || !type.featureOfType().isThisRef()) ? "ref "
                    : !type.isRef() && type.featureOfType() != null && type.featureOfType().isThisRef() ? "value "
                    : "")
          + (type.featureOfType() == null
                                          ? type.name()
                                          : type.featureOfType().featureName().baseName());
      }
    else if (type.featureOfType() == null || type.featureOfType() == Types.f_ERROR)
      {
        return type.name();
      }
    else
      {
        return type.featureOfType().featureName().baseName();
      }
  }
}
