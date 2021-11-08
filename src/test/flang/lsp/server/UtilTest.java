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
 * Source of class UtilTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.lsp4j.CompletionContext;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.FuzionTextDocumentService;
import dev.flang.lsp.server.MaxExecutionTimeExceededException;
import dev.flang.lsp.server.Util;
import dev.flang.lsp.server.feature.Completion;

public class UtilTest extends BaseTest
{

  @Test
  void RunWithPeriodicCancelCheck() throws InterruptedException, ExecutionException
  {
    var sourceText = """
      ex is
        (1..10).
            """;
    FuzionTextDocumentService.setText(uri1, sourceText);

    final ArrayList<Object> results = new ArrayList<>(2);
    results.add(0, null);
    results.add(1, null);

    var request1 = new Thread(() -> {
      var one = CompletableFutures.computeAsync(cancelChecker -> {
        try
          {
            var completionParams = new CompletionParams(Util.TextDocumentIdentifier(uri1), new Position(1, 11),
              new CompletionContext(CompletionTriggerKind.TriggerCharacter, "."));
            return Util.RunWithPeriodicCancelCheck(cancelChecker, () -> Completion.getCompletions(completionParams),
              5, 10);
          }
        catch (InterruptedException | ExecutionException | TimeoutException | MaxExecutionTimeExceededException e)
          {
            return e;
          }
      });
      try
        {
          results.set(0, one.get());
        }
      catch (Exception e)
        {
          results.set(0, e);
        }
    });

    var request2 = new Thread(() -> {
      var two = CompletableFutures.computeAsync(cancelChecker -> {
        try
          {
            var completionParams = new CompletionParams(Util.TextDocumentIdentifier(uri1), new Position(1, 11),
              new CompletionContext(CompletionTriggerKind.TriggerCharacter, "."));
            return Util.RunWithPeriodicCancelCheck(cancelChecker, () -> Completion.getCompletions(completionParams),
              5, 5000);
          }
        catch (InterruptedException | ExecutionException | TimeoutException| MaxExecutionTimeExceededException e)
          {
            return null;
          }
      });
      try
        {
          results.set(1, two.get());
        }
      catch (Exception e)
        {
          throw new RuntimeException(e);
        }
    });

    // start
    request1.start();
    request2.start();
    // wait for finish
    request1.join();
    request2.join();

    assertTrue(results.get(0) instanceof Exception);
    assertTrue(((Either<List<CompletionItem>, CompletionList>) results.get(1)).getLeft().size() > 10);

  }

}
