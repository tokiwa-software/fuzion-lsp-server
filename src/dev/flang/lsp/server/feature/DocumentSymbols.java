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
 * Source of class DocumentSymbols
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.feature;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.ast.AbstractFeature;
import dev.flang.lsp.server.util.Bridge;
import dev.flang.lsp.server.util.FeatureTool;
import dev.flang.lsp.server.util.FuzionParser;
import dev.flang.lsp.server.util.LSP4jUtils;

public class DocumentSymbols
{
  public static List<Either<SymbolInformation, DocumentSymbol>> getDocumentSymbols(DocumentSymbolParams params)
  {
    var mainFeature = FuzionParser.main(LSP4jUtils.getUri(params.getTextDocument()));

    var rootSymbol = DocumentSymbols.DocumentSymbolTree(mainFeature);

    return List.of(Either.forRight(rootSymbol));
  }

  public static DocumentSymbol DocumentSymbolTree(AbstractFeature feature)
  {
    var documentSymbol = Bridge.ToDocumentSymbol(feature);
    var children = FuzionParser.DeclaredFeatures(feature)
      .filter(f -> !FeatureTool.IsFieldLike(f))
      .map(f -> {
        return DocumentSymbolTree(f);
      })
      .collect(Collectors.toList());
    documentSymbol.setChildren(children);
    return documentSymbol;
  }

}
