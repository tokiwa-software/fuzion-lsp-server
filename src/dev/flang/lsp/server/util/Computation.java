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

import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

import dev.flang.lsp.server.Config;
import dev.flang.shared.Concurrency;
import dev.flang.shared.concurrent.MaxExecutionTimeExceededException;

public class Computation
{
  private static final int INTERVALL_CHECK_CANCELLED_MS = 50;
  private static LocalDateTime lastErrorMessageSent = LocalDateTime.MIN;

  public static <T> CompletableFuture<T> CancellableComputation(Callable<T> callable, String callee, int maxTimeInMs)
  {
    Log.message("[" + callee + "] started computing.", MessageType.Log);

    var result = new CompletableFuture<T>();
    return result.completeAsync(() -> {
      try
        {

          var res =  Concurrency.RunWithPeriodicCancelCheck(callable, () -> {
            if (result.isCancelled())
              {
                throw new CancellationException();
              }
          }, INTERVALL_CHECK_CANCELLED_MS,
            maxTimeInMs);

          var ms = res.nanoSeconds() / 1_000_000;
          Log.message("[" + callee + "] finished in " + ms + "ms", MessageType.Log);

          return res.result();
        }
      catch (ExecutionException e)
        {
          Log.message("[" + callee + "] An excecution exception occurred: " + e, MessageType.Error);
          NotifyUser();
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
          NotifyUser();
        }
      return null;
    });
  }

  private static void NotifyUser()
  {
    if (lastErrorMessageSent.plusMinutes(1).isBefore(LocalDateTime.now()))
      {
        lastErrorMessageSent = LocalDateTime.now();
        Config.languageClient().showMessage(new MessageParams(MessageType.Error, "An error occurred. :-( See logs."));
      }
  }
}
