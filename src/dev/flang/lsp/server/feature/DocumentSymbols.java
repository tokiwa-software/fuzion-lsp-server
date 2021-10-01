package dev.flang.lsp.server.feature;

import java.util.List;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.Util;

public class DocumentSymbols
{
  public static List<Either<SymbolInformation, DocumentSymbol>> getDocumentSymbols(DocumentSymbolParams params)
  {
    var baseFeature = FuzionHelpers.getBaseFeature(Util.getUri(params.getTextDocument()));
    if (baseFeature.isEmpty())
      {
        return List.of();
      }

    var feature = baseFeature.get();

    var rootSymbol = FuzionHelpers.ToDocumentSymbol(feature);

    var visitor =  new DocumentSymbolVisitor(rootSymbol);

    feature.visit(visitor, feature.outer());
    return List.of(Either.forRight(rootSymbol));
  }

}
