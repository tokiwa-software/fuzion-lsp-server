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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.MessageType;

import dev.flang.shared.Concurrency;
import dev.flang.shared.concurrent.MaxExecutionTimeExceededException;

public class Computation
{
  private static final int INTERVALL_CHECK_CANCELLED_MS = 50;

  public static <T> CompletableFuture<T> CancellableComputation(Callable<T> callable, String callee, int maxTimeInMs)
  {
    var result = new CompletableFuture<T>();
    return result.completeAsync(() -> {
      try
        {
          return Concurrency.RunWithPeriodicCancelCheck(callable, () -> {
            if (result.isCancelled())
              {
                throw new CancellationException();
              }
          }, INTERVALL_CHECK_CANCELLED_MS,
            maxTimeInMs).result();
        }
      catch (ExecutionException e)
        {
          Log.message("[" + callee + "] An excecution exception occurred: " + e, MessageType.Error);
        }
      catch (MaxExecutionTimeExceededException e)
        {
          Log.message("[" + callee + "] Max excecution time exceeded: " + e, MessageType.Warning);
        }
      catch (CancellationException e)
        {
          Log.message("[" + callee + "] was cancelled.", MessageType.Info);
        }
      catch (Throwable th)
        {
          Log.message("[" + callee + "] An unexpected error occurred: " + th, MessageType.Error);
        }
      return null;
    });


  }
}
