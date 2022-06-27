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

import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

import dev.flang.lsp.server.feature.CodeActions;
import dev.flang.lsp.server.feature.CodeLenses;
import dev.flang.lsp.server.feature.Completion;
import dev.flang.lsp.server.feature.Definition;
import dev.flang.lsp.server.feature.Diagnostics;
import dev.flang.lsp.server.feature.DocumentSymbols;
import dev.flang.lsp.server.feature.Highlight;
import dev.flang.lsp.server.feature.Hovering;
import dev.flang.lsp.server.feature.InlayHints;
import dev.flang.lsp.server.feature.References;
import dev.flang.lsp.server.feature.Rename;
import dev.flang.lsp.server.feature.SemanticToken;
import dev.flang.lsp.server.feature.SignatureHelper;
import dev.flang.lsp.server.util.Computation;
import dev.flang.lsp.server.util.LSP4jUtils;
import dev.flang.shared.Debouncer;
import dev.flang.shared.SourceText;
import dev.flang.shared.Util;

public class FuzionTextDocumentService implements TextDocumentService
{
  @Override
  public void didOpen(DidOpenTextDocumentParams params)
  {
    var textDocument = params.getTextDocument();
    var uri = Util.toURI(textDocument.getUri());
    var text = textDocument.getText();

    SourceText.setText(uri, text);
    afterSetText(uri);
  }

  final Debouncer debouncer = new Debouncer();

  private void afterSetText(URI uri)
  {
    debouncer.debounce(uri, new Runnable() {
      @Override
      public void run()
      {
        Diagnostics.publishDiagnostics(uri);
      }
    }, Config.DIAGNOSTICS_DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
  }

  @Override
  public void didChange(DidChangeTextDocumentParams params)
  {
    var uri = LSP4jUtils.getUri(params.getTextDocument());
    var text = SyncKindFull(params);
    SourceText.setText(uri, text);
    afterSetText(uri);

  }

  private String SyncKindFull(DidChangeTextDocumentParams params)
  {
    var contentChanges = params.getContentChanges();
    var text = contentChanges.get(0).getText();
    return text;
  }


  @Override
  public void didClose(DidCloseTextDocumentParams params)
  {
  }

  @Override
  public void didSave(DidSaveTextDocumentParams params)
  {

  }

  private <T> CompletableFuture<T> AbortAfterOneSecond(Callable<T> c)
  {
    return Computation.Compute(c, 1000);
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position)
  {
    return AbortAfterOneSecond(() -> Completion.getCompletions(position));

  }

  @Override
  public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved)
  {
    return AbortAfterOneSecond(() -> unresolved);
  }

  @Override
  public CompletableFuture<Hover> hover(HoverParams params)
  {
    return AbortAfterOneSecond(() -> Hovering.getHover(params));
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
    DefinitionParams params)
  {
    return AbortAfterOneSecond(() -> Definition.getDefinitionLocation(params));
  }

  @Override
  public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params)
  {
    return AbortAfterOneSecond(() -> Highlight.getHightlights(params));
  }

  @Override
  public CompletableFuture<List<? extends Location>> references(ReferenceParams params)
  {
    return AbortAfterOneSecond(() -> References.getReferences(params));
  }

  @Override
  public CompletableFuture<WorkspaceEdit> rename(RenameParams params)
  {
    return AbortAfterOneSecond(() -> Rename.getWorkspaceEdit(params));
  }

  @Override
  public CompletableFuture<Either<Range, PrepareRenameResult>> prepareRename(PrepareRenameParams params)
  {
    return AbortAfterOneSecond(() -> Either.forRight(Rename.getPrepareRenameResult(params)));
  }

  @Override
  public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params)
  {
    return AbortAfterOneSecond(() -> CodeActions.getCodeActions(params));
  }

  @Override
  public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params)
  {
    return AbortAfterOneSecond(() -> DocumentSymbols.getDocumentSymbols(params));
  }

  @Override
  public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params)
  {
    return AbortAfterOneSecond(() -> InlayHints.getInlayHints(params));
  }

  @Override
  public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params)
  {
    return AbortAfterOneSecond(() -> CodeLenses.getCodeLenses(params));
  }

  @Override
  public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params)
  {
    return AbortAfterOneSecond(() -> SignatureHelper.getSignatureHelp(params));
  }

  @Override
  public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params)
  {
    return AbortAfterOneSecond(() -> SemanticToken.getSemanticTokens(params));
  }

}
