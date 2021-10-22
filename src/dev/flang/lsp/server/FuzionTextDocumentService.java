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
 * Source of class FuzionTextDocumentService
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

import dev.flang.lsp.server.feature.CodeLenses;
import dev.flang.lsp.server.feature.Completion;
import dev.flang.lsp.server.feature.Definition;
import dev.flang.lsp.server.feature.Diagnostics;
import dev.flang.lsp.server.feature.DocumentSymbols;
import dev.flang.lsp.server.feature.Hovering;
import dev.flang.lsp.server.feature.References;
import dev.flang.lsp.server.feature.Rename;
import dev.flang.lsp.server.feature.SignatureHelper;

public class FuzionTextDocumentService implements TextDocumentService
{
  /**
   * currently open text documents and their contents
   */
  private static final HashMap<String, String> textDocuments = new HashMap<String, String>();

  public static Optional<String> getText(String uri)
  {
    var text = textDocuments.get(uri);
    return Optional.ofNullable(text);
  }

  public static void setText(String uri, String text)
  {
    if (text == null)
      {
        Util.WriteStackTraceAndExit(1);
      }
    if (!uri.startsWith("file://"))
      {
        Util.WriteStackTraceAndExit(1);
      }
    textDocuments.put(uri, text);
  }

  @Override
  public void didOpen(DidOpenTextDocumentParams params)
  {
    var textDocument = params.getTextDocument();
    var uri = textDocument.getUri();
    var text = textDocument.getText();

    setText(uri, text);
    afterSetText(uri);
  }

  private void afterSetText(String uri)
  {
    // NYI turn this into an event? to run asynchronosly?
    ParserHelper.getMainFeature(uri);
    Diagnostics.publishDiagnostics(uri);
  }

  // taken from apache commons
  public static int ordinalIndexOf(String str, String substr, int n)
  {
    if (n == 0)
      {
        return -1;
      }
    int pos = str.indexOf(substr);
    while (--n > 0 && pos != -1)
      pos = str.indexOf(substr, pos + 1);
    return pos;
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params)
  {
    var uri = Util.getUri(params.getTextDocument());

    synchronized (textDocuments)
      {
        var text = getText(uri).orElseThrow();

        var contentChanges = params.getContentChanges();

        text = applyContentChanges(text, contentChanges);

        setText(uri, text);
        afterSetText(uri);
      }
  }

  /**
   * sort descending by line then descending by character
   *
   * @param contentChanges
   */
  private void reverseSort(List<TextDocumentContentChangeEvent> contentChanges)
  {
    contentChanges.sort((left, right) -> {
      if (right.getRange().getStart().getLine() == left.getRange().getStart().getLine())
        {
          return Integer.compare(right.getRange().getStart().getCharacter(), left.getRange().getStart().getCharacter());
        }
      else
        {
          return Integer.compare(right.getRange().getStart().getLine(), left.getRange().getStart().getLine());
        }
    });
  }

  private String applyContentChanges(String text, List<TextDocumentContentChangeEvent> contentChanges)
  {
    reverseSort(contentChanges);
    return contentChanges.stream().reduce(text, (_text, contentChange) -> {
      var start = ordinalIndexOf(_text, System.lineSeparator(), contentChange.getRange().getStart().getLine()) + 1
        + contentChange.getRange().getStart().getCharacter();
      var end = ordinalIndexOf(_text, System.lineSeparator(), contentChange.getRange().getEnd().getLine()) + 1
        + contentChange.getRange().getEnd().getCharacter();
      return _text.substring(0, start) + contentChange.getText() + _text.substring(end, _text.length());
    }, String::concat);
  }

  @Override
  public void didClose(DidCloseTextDocumentParams params)
  {
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params)
  {

  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position)
  {
    return CompletableFuture.completedFuture(Completion.getCompletions(position));
  }

  @Override
  public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved)
  {
    return CompletableFuture.completedFuture(unresolved);
  }

  @Override
  public CompletableFuture<Hover> hover(HoverParams params)
  {
    return CompletableFuture.completedFuture(Hovering.getHover(params));
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
    DefinitionParams params)
  {

    return CompletableFuture.completedFuture(Definition.getDefinitionLocation(params));
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params)
  {
    return CompletableFuture.completedFuture(References.getReferences(params));
  }

  @Override
  public CompletableFuture<WorkspaceEdit> rename(RenameParams params)
  {
    return CompletableFuture.completedFuture(Rename.getWorkspaceEdit(params));
  }

  @Override
  public CompletableFuture<Either<Range, PrepareRenameResult>> prepareRename(PrepareRenameParams params)
  {
    return CompletableFuture.completedFuture(Either.forRight(Rename.getPrepareRenameResult(params)));
  }

  @Override
  public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params)
  {
    return CompletableFuture.completedFuture(null);
  }


  @Override
  public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params)
  {
    return CompletableFuture.completedFuture(DocumentSymbols.getDocumentSymbols(params));
  }

  @Override
  public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params)
  {
    return CompletableFuture.completedFuture(CodeLenses.getCodeLenses(params));
  }

  @Override
  public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params)
  {
    return CompletableFuture.completedFuture(SignatureHelper.getSignatureHelp(params));
  }

}
