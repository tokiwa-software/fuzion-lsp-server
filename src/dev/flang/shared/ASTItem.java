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
 * Source of class ASTItem
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared;

import java.net.URI;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.function.Predicate;

import dev.flang.ast.AbstractAssign;
import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractConstant;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Assign;
import dev.flang.ast.Block;
import dev.flang.ast.If;
import dev.flang.ast.Types;
import dev.flang.util.SourcePosition;

public class ASTItem
{

  public static String ToLabel(Object item)
  {
    try
      {
        if (item instanceof If || item instanceof Block)
          {
            return "";
          }
        if (item instanceof AbstractCall c)
          {
            if (Types.t_ERROR.compareTo(c.type()) == 0)
              {
                return "called feature unknown";
              }
            return c.calledFeature().qualifiedName();
          }
        if (item instanceof Assign a)
          {
            return a._assignedField.qualifiedName();
          }
        if (item instanceof AbstractFeature af)
          {
            return FeatureTool.ToLabel(af);
          }
        return item.toString();
      }
    catch (Exception e)
      {
        return "";
      }
  }

  // NYI remove once we have ISourcePosition interface
  /**
   * getPosition of ASTItem
   * @param entry
   * @return
   */
  public static SourcePosition sourcePosition(Object entry)
  {
    if (entry instanceof AbstractFeature e)
      {
        return e.pos();
      }
    if (entry instanceof AbstractAssign a)
      {
        return a.pos();
      }
    if (entry instanceof AbstractConstant c)
      {
        return c.pos();
      }
    if (entry instanceof AbstractCall c)
      {
        return c.pos();
      }
    if (entry instanceof AbstractType t)
      {
        throw new IllegalArgumentException("Not applicable. Type can have multiple source positions.");
      }

    var errorMessage = "sourcePosition(), missing implementation for: " + entry.getClass();
    IO.SYS_ERR.println(errorMessage);
    ErrorHandling.WriteStackTrace(new Exception(errorMessage));
    return SourcePosition.notAvailable;
  }

  public static Predicate<? super Entry<Object, AbstractFeature>> IsItemInFile(URI uri)
  {
    return (entry) -> {
      var sourcePositionOption = sourcePosition(entry.getKey());
      if (sourcePositionOption.isBuiltIn())
        {
          return false;
        }
      return uri.equals(FuzionParser.getUri(sourcePositionOption));
    };
  }

  public static Comparator<? super Object> CompareByLineThenByColumn()
  {
    return (a, b) -> {
      var position1 = sourcePosition(a);
      var position2 = sourcePosition(b);
      if (position1.isBuiltIn())
        {
          return -1;
        }
      if (position2.isBuiltIn())
        {
          return +1;
        }
      if (position1._line == position2._line)
        {
          return position1._column - position2._column;
        }
      return position1._line - position2._line;
    };
  }
}
