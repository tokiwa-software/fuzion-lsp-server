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
 * Source of class Commands
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.feature;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowDocumentParams;

import com.google.gson.JsonPrimitive;

import dev.flang.lsp.server.Config;
import dev.flang.lsp.server.FuzionLanguageClient;
import dev.flang.lsp.server.util.Computation;
import dev.flang.shared.Concurrency;
import dev.flang.shared.ErrorHandling;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.IO;
import dev.flang.shared.ParserTool;
import dev.flang.shared.Util;

public enum Commands
{
  showSyntaxTree,
  run, callGraph;

  public String toString()
  {
    switch (this)
      {
      case showSyntaxTree :
        return "Show Syntax Tree";
      case run :
        return "Run";
      case callGraph :
        return "Call Graph";
      default:
        return "not implemented";
      }
  }

  private static CompletableFuture<Object> completedFuture = CompletableFuture.completedFuture(null);

  public static CompletableFuture<Object> Execute(ExecuteCommandParams params)
  {
    var arg0 = ((JsonPrimitive) params.getArguments().get(0)).getAsString();

    switch (Commands.valueOf(params.getCommand()))
      {
      case showSyntaxTree :
        Concurrency.RunInBackground(() -> showSyntaxTree(Util.toURI(arg0)));
        return completedFuture;
      case run :
        Concurrency.RunInBackground(() -> evaluate(Util.toURI(arg0)));
        return completedFuture;
      case callGraph :
        var arg1 = ((JsonPrimitive) params.getArguments().get(1)).getAsString();
        Concurrency.RunInBackground(() -> CallGraph(arg0, arg1));
        return completedFuture;
      default:
        ErrorHandling.WriteStackTrace(new Exception("not implemented"));
        return completedFuture;
      }
  }

  private static void CallGraph(String arg0, String arg1)
  {
    // NYI go to correct feature via more direct way
    var feature = FeatureTool.SelfAndDescendants(ParserTool.Universe(Util.toURI(arg0)))
      .filter(f -> FeatureTool.UniqueIdentifier(f).equals(arg1))
      .findFirst()
      .get();
    var callGraph = FeatureTool.CallGraph(feature);
    var file = IO.writeToTempFile(callGraph, String.valueOf(System.currentTimeMillis()), ".fuzion.dot");
    try
      {
        // generate png
        (new ProcessBuilder(("dot -Tpng -o output.png " + file.toString()).split(" ")))
          .directory(file.getParentFile())
          .start()
          .waitFor();
        try
          {
            // first try: image magick
            (new ProcessBuilder("display output.png".split(" ")))
              .directory(file.getParentFile())
              .start();
          }
        catch (Exception e)
          {
            // try again with xdg-open
            (new ProcessBuilder("xdg-open output.png".split(" ")))
              .directory(file.getParentFile())
              .start();
          }
      }
    catch (Exception e)
      {
        Config.languageClient()
          .showMessage(new MessageParams(MessageType.Warning,
            "Display of call graph failed. Do you have graphviz and imagemagick installed?"));
        Config.languageClient().showDocument(new ShowDocumentParams(file.toURI().toString()));
      }
  }

  private static void evaluate(URI uri)
  {
    var token = FuzionLanguageClient.StartProgress("running", uri.toString());
    try
      {
        var result = ParserTool.Run(uri);
        var file = IO.writeToTempFile(result, String.valueOf(System.currentTimeMillis()), ".result");
        Config.languageClient().showDocument(new ShowDocumentParams(file.toURI().toString()));
      }
    catch (Exception e)
      {
        var message = e.getMessage();
        if (message != null)
          {
            Config.languageClient()
              .showMessage(new MessageParams(MessageType.Error, message));
          }
      } finally
      {
        FuzionLanguageClient.EndProgress(token);
      }
  }

  private static void showSyntaxTree(URI uri)
  {
    var ast = ParserTool
      .TopLevelFeatures(uri)
      .map(f -> FeatureTool.AST(f))
      .collect(Collectors.joining(System.lineSeparator() + "===" + System.lineSeparator()));
    var file = IO.writeToTempFile(ast, String.valueOf(System.currentTimeMillis()), ".fuzion.ast");
    Config.languageClient().showDocument(new ShowDocumentParams(file.toURI().toString()));
  }
}
