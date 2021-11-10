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
 * Source of class Concurrency
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.util;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;

import dev.flang.lsp.server.Config;
import dev.flang.lsp.server.util.concurrent.MaxExecutionTimeExceededException;

public class Concurrency
{

  private static final int INTERVALL_CHECK_CANCELLED_MS = 50;
  private static final int MAX_EXECUTION_TIME_MS = 500;

  // for now we have to run most things more or less sequentially
  private static ExecutorService executor = Executors.newSingleThreadExecutor();
  private static ExecutorService cachedThreadPoolExecutor = Executors.newCachedThreadPool();


  public static void RunInBackground(Runnable runnable)
  {
    cachedThreadPoolExecutor.submit(runnable);
  }

  /**
   * run callable on single thread executor.
   * periodically check if callable meanwhile has been cancelled
   * and/or maximum execution time has been reached
   * @param <T>
   * @param cancelToken
   * @param callable
   * @param intervallCancelledCheckInMs
   * @param maxExecutionTimeInMs
   * @return
   * @throws InterruptedException
   * @throws ExecutionException
   * @throws TimeoutException
   * @throws MaxExecutionTimeExceededException
   */
  public static <T> T RunWithPeriodicCancelCheck(
    CancelChecker cancelToken, Callable<T> callable, int intervallCancelledCheckInMs, int maxExecutionTimeInMs)
    throws InterruptedException, ExecutionException, TimeoutException, MaxExecutionTimeExceededException
  {
    Future<T> future = executor.submit(callable);
    try
      {
        var timeElapsedInMs = 0;
        var completed = false;
        while (!completed)
          {
            if (cancelToken != null)
              {
                cancelToken.checkCanceled();
              }
            try
              {
                future.get(intervallCancelledCheckInMs, TimeUnit.MILLISECONDS);
                completed = true;
              }
            // when timeout occurs we check
            // if maxExecutionTime has been reached
            // or if cancelToken wants to cancel execution
            catch (TimeoutException e)
              {
                timeElapsedInMs += intervallCancelledCheckInMs;
                if (timeElapsedInMs >= maxExecutionTimeInMs)
                  {
                    throw new MaxExecutionTimeExceededException("max execution time exceeded.", e);
                  }
              }
          }
      } finally
      {
        if (!future.isCancelled() || !future.isDone())
          {
            future.cancel(true);
          }
      }
    return future.get();
  }

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
          return RunWithPeriodicCancelCheck(cancelChecker, callable, INTERVALL_CHECK_CANCELLED_MS,
            MAX_EXECUTION_TIME_MS);
        }
      catch (InterruptedException | ExecutionException | TimeoutException | MaxExecutionTimeExceededException e)
        {
          if (Config.DEBUG())
            {
              ErrorHandling.WriteStackTrace(context);
              ErrorHandling.WriteStackTrace(e);
            }
          return null;
        }
    });
  }

}
