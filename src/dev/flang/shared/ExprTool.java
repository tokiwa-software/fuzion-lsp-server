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
 * Source of class ExprTool
 *
 *---------------------------------------------------------------------*/


package dev.flang.shared;

import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import dev.flang.ast.AbstractBlock;
import dev.flang.ast.AbstractCall;
import dev.flang.ast.Expr;
import dev.flang.util.ANY;
import dev.flang.util.FuzionConstants;
import dev.flang.util.SourcePosition;

public class ExprTool extends ANY
{

  public static SourcePosition EndOfExpr(Expr expr)
  {
    var lastPos = expr.pos();
    if (expr instanceof AbstractBlock ab)
      {
        Expr resExpr = ab.resultExpression();
        if (resExpr != null)
          {
            lastPos = resExpr.pos();
          }
      }
    if (expr instanceof AbstractCall ac)
      {
        lastPos = ac.actuals()
          .stream()
          .map(expression -> EndOfExpr(expression))
          .sorted(Comparator.reverseOrder())
          // filter e.g. numeric literals
          .filter(x -> !x.isBuiltIn())
          .findFirst()
          .orElse(ac.pos());
      }
    return lastPos.isBuiltIn()
                               ? lastPos: LexerTool.TokensAt(lastPos)
                                 .right()
                                 .end();
  }

  public static boolean IsLamdaCall(Expr expr)
  {
    return expr instanceof AbstractCall ac
      && ac.calledFeature().featureName().baseName().startsWith(FuzionConstants.LAMBDA_PREFIX);
  }

  // NYI parser should give us this info
  static Optional<SourcePosition> LambdaOpeningBracePosition(Expr expr)
  {
    if (PRECONDITIONS)
      require(IsLamdaCall(expr));

    var tokens = LexerTool.TokensFrom(new SourcePosition(expr.pos()._sourceFile, expr.pos()._line, 1))
      .takeWhile(x -> x.start().compareTo(expr.pos()) <= 0)
      .collect(Collectors.toList());

    var count = new AtomicInteger(1);
    Collections.reverse(tokens);
    return tokens
      .stream()
      .dropWhile(x -> {
        if (LexerTool.LeftBrackets.contains(x.token()))
          {
            count.decrementAndGet();
          }
        if (LexerTool.RightBrackets.contains(x.token()))
          {
            count.incrementAndGet();
          }

        return count.get() != 0;
      })
      .findFirst()
      .map(x -> x.start());
  }

}
