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
 * Source of class Command
 *
 *---------------------------------------------------------------------*/


package dev.flang.lsp.server.feature;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonPrimitive;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowDocumentParams;

import dev.flang.lsp.server.Config;
import dev.flang.lsp.server.Util;
import dev.flang.lsp.server.enums.Commands;
import dev.flang.lsp.server.util.Concurrency;
import dev.flang.lsp.server.util.ErrorHandling;
import dev.flang.lsp.server.util.FeatureTool;
import dev.flang.lsp.server.util.FuzionParser;
import dev.flang.lsp.server.util.IO;

public class Command
{
  public static CompletableFuture<Object> Execute(ExecuteCommandParams params)
  {
    var uri = ((JsonPrimitive) params.getArguments().get(0)).getAsString();

    switch (Commands.valueOf(params.getCommand()))
      {
        case showSyntaxTree :
          Concurrency.RunInBackground(() -> showSyntaxTree(Util.toURI(uri)));
          return Concurrency.Compute(() -> null);
        case evaluate :
          Concurrency.RunInBackground(() -> evaluate(Util.toURI(uri)));
          return Concurrency.Compute(() -> null);
        default:
          ErrorHandling.WriteStackTrace(new Exception("not implemented"));
          return Concurrency.Compute(() -> null);
      }
  }

  private static void evaluate(URI uri)
  {
    try
      {
        var result = FuzionParser.Run(uri);
        Config.languageClient().showMessage(result);
      }
    catch (Exception e)
      {
        var message = e.getMessage();
        if (message != null)
          {
            Config.languageClient()
              .showMessage(new MessageParams(MessageType.Error, message));
          }
      }
  }

  private static void showSyntaxTree(URI uri)
  {
    var feature = FuzionParser.main(uri);
    if (feature.isEmpty())
      {
        Concurrency.Compute(() -> null);
      }
    var ast = FeatureTool.AST(feature.get());
    var file = IO.writeToTempFile(ast, String.valueOf(System.currentTimeMillis()), ".fuzion.ast");
    Config.languageClient().showDocument(new ShowDocumentParams(file.toURI().toString()));
  }

}
