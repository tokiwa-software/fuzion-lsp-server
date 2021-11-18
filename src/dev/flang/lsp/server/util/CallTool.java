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
 * Source of class CallTool
 *
 *---------------------------------------------------------------------*/


package dev.flang.lsp.server.util;

import java.util.Comparator;
import java.util.Optional;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Call;
import dev.flang.util.SourcePosition;

public class CallTool
{
  /**
   * @param call
   * @return
   */
  public static Optional<AbstractFeature> featureOf(Call call)
  {
    return QueryAST.InFeature(Bridge.ToTextDocumentPosition(call.pos()));
  }

  /**
   * tries to figure out the end of a call in terms of a sourceposition
   * @param call
   * @return
  */
  public static SourcePosition endOfCall(Call call)
  {
    var result = call._actuals
      .stream()
      .map(expression -> expression.pos())
      .sorted(Comparator.reverseOrder())
      .findFirst();
    if (result.isEmpty())
      {
        return call.pos();
      }

    return new SourcePosition(result.get()._sourceFile, result.get()._line, result.get()._column + 1);
  }


}
