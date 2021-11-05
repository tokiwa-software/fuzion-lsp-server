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
 * Source of class FuzionWorkspaceService
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import com.google.gson.JsonPrimitive;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.services.WorkspaceService;

public class FuzionWorkspaceService implements WorkspaceService
{

  @Override
  public void didChangeConfiguration(DidChangeConfigurationParams params)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void didChangeWatchedFiles(DidChangeWatchedFilesParams params)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public CompletableFuture<Object> executeCommand(ExecuteCommandParams params)
  {
    var uri = ((JsonPrimitive) params.getArguments().get(0)).getAsString();

    switch (Commands.valueOf(params.getCommand()))
      {
        case showSyntaxTree :
          Util.RunInBackground(() -> showSyntaxTree(Util.toURI(uri)));
          return CompletableFuture.completedFuture(null);
        case evaluate :
          Util.RunInBackground(() -> evaluate(Util.toURI(uri)));
          return CompletableFuture.completedFuture(null);
        default:
          Util.WriteStackTrace(new Exception("not implemented"));
          return CompletableFuture.completedFuture(null);
      }
  }

  private void evaluate(URI uri)
  {
    try
      {
        var result = FuzionHelpers.Run(uri);
        Config.languageClient().showMessage(result);
      }
    catch (IOException | InterruptedException | ExecutionException | TimeoutException | StackOverflowError e)
      {
        var message = e.getMessage();
        if (message != null)
          {
            Config.languageClient()
              .showMessage(new MessageParams(MessageType.Error, message));
          }
      }

  }

  private void showSyntaxTree(URI uri)
  {
    var feature = FuzionHelpers.baseFeature(uri);
    if (feature.isEmpty())
      {
        CompletableFuture.completedFuture(null);
      }
    var ast = FuzionHelpers.AST(feature.get());
    var file = Util.writeToTempFile(ast, String.valueOf(System.currentTimeMillis()), ".fuzion.ast");
    Config.languageClient().showDocument(new ShowDocumentParams(file.toURI().toString()));
  }

}
