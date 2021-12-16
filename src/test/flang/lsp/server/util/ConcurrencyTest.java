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
 * Source of class ConcurrencyTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.lsp4j.CompletionContext;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

// NYI remove dependency of dev.flang.lsp and move to dev.flang.shared
import dev.flang.lsp.server.feature.Completion;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.shared.CompletableFutures;
import dev.flang.shared.Concurrency;
import dev.flang.shared.SourceText;
import dev.flang.shared.concurrent.MaxExecutionTimeExceededException;
import dev.flang.shared.records.ComputationPerformance;
import test.flang.lsp.server.ExtendedBaseTest;


public class ConcurrencyTest extends ExtendedBaseTest
{

  private static final int TenMilliseconds = 10;

  @Test
  public void RunWithPeriodicCancelCheck() throws InterruptedException, ExecutionException
  {
    var sourceText = """
      ex1 is
        (1..10).
            """;
    SourceText.setText(uri1, sourceText);

    final ArrayList<Object> results = new ArrayList<>();

    var request1 = createRequest(results, 0, TenMilliseconds);
    var request2 = createRequest(results, 1, 5000);

    // start
    request1.start();
    request2.start();
    // wait for finish
    request1.join();
    request2.join();

    assertTrue(results.get(0) instanceof MaxExecutionTimeExceededException);
    assertTrue(((ComputationPerformance<Either<List<CompletionItem>, CompletionList>>) results.get(1)).result().getLeft().size() > 10);

  }

  private Thread createRequest(final ArrayList<Object> results, int index, int maxExcecutionTime)
  {
    results.add(index, null);
    return new Thread(() -> {
      try
        {
          results.set(index, getCompletion(maxExcecutionTime));
        }
      catch (Exception e)
        {
          results.set(index, e);
        }
    });
  }

  private Object getCompletion(int maxExcecutionTime)
    throws InterruptedException, ExecutionException
  {
    return CompletableFutures.computeAsync(cancelChecker -> {
      try
        {
          var completionParams =
            new CompletionParams(LSP4jUtils.TextDocumentIdentifier(uri1), new Position(1, 11),
              new CompletionContext(CompletionTriggerKind.TriggerCharacter, "."));
          return Concurrency.RunWithPeriodicCancelCheck(cancelChecker,
            () -> Completion.getCompletions(completionParams),
            5, maxExcecutionTime);
        }
      catch (InterruptedException | ExecutionException | TimeoutException | MaxExecutionTimeExceededException e)
        {
          return e;
        }
    })
      .get();
  }

  @Test
  public void SuccessfulRequestAfterSomeMaxExecutionTimeExceededRequests() throws InterruptedException, ExecutionException
  {
    var sourceText = """
      ex2 is
        (1..10).
            """;
    SourceText.setText(uri1, sourceText);

    int requestCount = 10;

    var exectuor = Executors.newFixedThreadPool(8);

    var countDownLatch = new CountDownLatch(requestCount);

    IntStream.range(0, requestCount)
      .mapToObj(index -> {
        return exectuor.submit(() -> getCompletion(TenMilliseconds));
      })
      .collect(Collectors.toList())
      .stream()
      .forEach(future -> {
        assertDoesNotThrow(() -> {
          assertTrue(future.get() instanceof MaxExecutionTimeExceededException);
          countDownLatch.countDown();
        });
      });

    countDownLatch.await();

    assertDoesNotThrow(
      () -> ((ComputationPerformance<Either<List<CompletionItem>, CompletionList>>) exectuor.submit(() -> getCompletion(5000)).get()));
  }

}
