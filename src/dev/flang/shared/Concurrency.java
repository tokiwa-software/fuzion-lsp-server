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

package dev.flang.shared;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dev.flang.shared.concurrent.MaxExecutionTimeExceededException;
import dev.flang.shared.records.ComputationPerformance;

public class Concurrency
{

  // NYI for now we have to run most things more or less sequentially
  private static ExecutorService executor = Executors.newSingleThreadExecutor();
  private static ExecutorService cachedThreadPoolExecutor = Executors.newCachedThreadPool();


  public static void RunInBackground(Runnable runnable)
  {
    cachedThreadPoolExecutor.submit(runnable);
  }

  public static <T> Future<T> RunInBackground(Callable<T> callable)
  {
    return cachedThreadPoolExecutor.submit(callable);
  }

  /**
   * run callable on single thread executor.
   * periodically check if callable meanwhile has been cancelled
   * and/or maximum execution time has been reached
   * @param <T>
   * @param callable
   * @param checkCancelled
   * @param intervallCancelledCheckInMs
   * @param maxExecutionTimeInMs
   * @return
   * @throws InterruptedException
   * @throws ExecutionException
   * @throws TimeoutException
   * @throws MaxExecutionTimeExceededException
   * @throws CancellationException
   */
  public static <T> ComputationPerformance<T> RunWithPeriodicCancelCheck(
    Callable<T> callable, Runnable checkCancelled, int intervallCancelledCheckInMs, int maxExecutionTimeInMs)
    throws InterruptedException, ExecutionException, TimeoutException, MaxExecutionTimeExceededException,
    CancellationException
  {

    Future<ComputationPerformance<T>> future = executor.submit(() -> {
      long startTime = System.nanoTime();
      var result = callable.call();
      long stopTime = System.nanoTime();
      return new ComputationPerformance<T>(result, stopTime - startTime);
    });

    try
      {
        var timeElapsedInMs = 0;
        var completed = false;
        while (!completed)
          {
            checkCancelled.run();
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

}
