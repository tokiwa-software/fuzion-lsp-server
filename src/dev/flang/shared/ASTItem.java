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

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Assign;
import dev.flang.ast.Block;
import dev.flang.ast.If;
import dev.flang.ast.Types;
import dev.flang.util.HasSourcePosition;

public class ASTItem
{

  public static String ToLabel(HasSourcePosition item)
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
            return FeatureTool.Label(af);
          }
        return item.toString();
      }
    catch (Exception e)
      {
        return "";
      }
  }

  public static Predicate<? super Entry<HasSourcePosition, AbstractFeature>> IsItemInFile(URI uri)
  {
    return (entry) -> {
      var sourcePositionOption = entry.getKey().pos();
      if (sourcePositionOption.isBuiltIn())
        {
          return false;
        }
      return uri.equals(ParserTool.getUri(sourcePositionOption));
    };
  }

  public static Comparator<? super HasSourcePosition> CompareByLineThenByColumn()
  {
    return (a, b) -> {
      var position1 = a.pos();
      var position2 = b.pos();
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
