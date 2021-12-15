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

package dev.flang.lsp.server.util;

import java.net.URI;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Assign;
import dev.flang.ast.Block;
import dev.flang.ast.Call;
import dev.flang.ast.Case;
import dev.flang.ast.Cond;
import dev.flang.ast.Contract;
import dev.flang.ast.Expr;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.Generic;
import dev.flang.ast.If;
import dev.flang.ast.Impl;
import dev.flang.ast.InlineArray;
import dev.flang.ast.ReturnType;
import dev.flang.ast.Stmnt;
import dev.flang.lsp.server.Util;
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
        if (item instanceof Call c)
          {
            if (c.calledFeature_ != null)
              {
                return c.calledFeature().qualifiedName();
              }
            return "called feature not know.";
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
  public static Optional<SourcePosition> sourcePosition(Object entry)
  {
    if (entry instanceof AbstractFeature e)
      {
        return Optional.of(e.pos());
      }
    if (entry instanceof AbstractType t)
      {
        return Optional.ofNullable(t.pos());
      }
    if (entry instanceof Stmnt)
      {
        return Optional.ofNullable(((Stmnt) entry).pos());
      }
    if (entry instanceof Impl)
      {
        return Optional.ofNullable(((Impl) entry).pos);
      }
    if (entry instanceof Generic)
      {
        return Optional.ofNullable(((Generic) entry)._pos);
      }
    if (entry instanceof Case)
      {
        return Optional.ofNullable(((Case) entry).pos);
      }
    if (entry instanceof InlineArray)
      {
        return Optional.ofNullable(((InlineArray) entry).pos());
      }
    if (entry instanceof Expr)
      {
        return Optional.ofNullable(((Expr) entry).pos());
      }
    if (entry instanceof ReturnType)
      {
        return Optional.empty();
      }
    if (entry instanceof Cond)
      {
        return Optional.empty();
      }
    if (entry instanceof FormalGenerics)
      {
        return Optional.empty();
      }
    if (entry instanceof Contract)
      {
        return Optional.empty();
      }

    var errorMessage = "sourcePosition(), missing implementation for: " + entry.getClass();
    IO.SYS_ERR.println(errorMessage);
    ErrorHandling.WriteStackTrace(new Exception(errorMessage));
    return Optional.empty();
  }

  private static final SourcePosition None =
    new SourcePosition(FuzionLexer.ToSourceFile(Util.toURI("file:///--none--")), 0, 0) {
      public boolean isBuiltIn()
      {
        return true;
      }
    };

  public static SourcePosition sourcePositionOrNone(Object obj)
  {
    return ASTItem.sourcePosition(obj).orElse(None);
  }

  public static boolean IsAbstractFeature(Object o)
  {
    return AbstractFeature.class.isAssignableFrom(o.getClass());
  }

  static Predicate<? super Entry<Object, AbstractFeature>> IsItemInFile(URI uri)
  {
    return (entry) -> {
      var sourcePositionOption = sourcePosition(entry.getKey());
      if (sourcePositionOption.isEmpty())
        {
          return false;
        }
      return uri.equals(FuzionParser.getUri(sourcePositionOption.get()));
    };
  }

  static Comparator<? super Object> CompareByLineThenByColumn()
  {
    return (a, b) -> {
      var position1 = sourcePosition(a);
      var position2 = sourcePosition(b);
      if (position1.isEmpty())
        {
          return -1;
        }
      if (position2.isEmpty())
        {
          return +1;
        }
      if (position1.get()._line == position2.get()._line)
        {
          return position1.get()._column - position2.get()._column;
        }
      return position1.get()._line - position2.get()._line;
    };
  }
}
