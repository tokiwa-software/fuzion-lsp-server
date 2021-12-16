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
 * Source of class Computation
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.util;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.lsp4j.MessageType;

import dev.flang.lsp.server.Config;
import dev.flang.shared.CompletableFutures;
import dev.flang.shared.Concurrency;
import dev.flang.shared.ErrorHandling;
import dev.flang.shared.concurrent.MaxExecutionTimeExceededException;

public class Computation {
  private static final int INTERVALL_CHECK_CANCELLED_MS = 50;
  private static final int MAX_EXECUTION_TIME_MS = 1000;

  public static <T> CompletableFuture<T> Compute(Callable<T> callable)
  {
    if (Config.ComputeAsync)
      {
        return ComputeAsyncWithTimeout(callable);
      }
    try
      {
        return CompletableFuture.completedFuture(callable.call());
      }
    catch (Exception e)
      {
        throw new RuntimeException(e);
      }
  }

  private static <T> CompletableFuture<T> ComputeAsyncWithTimeout(Callable<T> callable)
  {
    // NYI log time of computations
    final Throwable context = Config.DEBUG() ? ErrorHandling.CurrentStacktrace(): null;
    return CompletableFutures.computeAsync(cancelChecker -> {
      try
        {
          var result = Concurrency.RunWithPeriodicCancelCheck(cancelChecker, callable, INTERVALL_CHECK_CANCELLED_MS,
            MAX_EXECUTION_TIME_MS);

          if (Config.DEBUG() && result.nanoSeconds() > 100_000_000)
            {
              Log.message(
                "Computation took " + Math.floor(result.nanoSeconds() / 1_000_000) + "ms: " + System.lineSeparator()
                  + ErrorHandling.toString(context),
                MessageType.Warning);
            }

          return result.result();
        }
      catch (ExecutionException e)
        {
          if (Config.DEBUG())
            {
              ErrorHandling.WriteStackTrace(context);
              ErrorHandling.WriteStackTrace(e);
            }
        }
      catch (InterruptedException | TimeoutException | MaxExecutionTimeExceededException e)
        {
          if (Config.DEBUG() && e instanceof MaxExecutionTimeExceededException)
            {
              Log.message(
                "Time exceeded" + System.lineSeparator() + ErrorHandling.toString(context), MessageType.Warning);
            }
        }
      return null;
    });
  }
}
