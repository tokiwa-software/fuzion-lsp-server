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
 * Source of class DocumentSymbolVisitor
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.feature;

import java.util.ArrayList;
import java.util.Stack;

import org.eclipse.lsp4j.DocumentSymbol;

import dev.flang.ast.Feature;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.Stmnt;
import dev.flang.lsp.server.Converters;
import dev.flang.lsp.server.FuzionHelpers;


public class DocumentSymbolVisitor extends FeatureVisitor
{
  private Stack<DocumentSymbol> stack = new Stack<>();

  DocumentSymbolVisitor(DocumentSymbol root){
    this.stack.push(root);
  }

  @Override
  public Stmnt action(Feature f, Feature outer)
  {
    if(FuzionHelpers.IsAnonymousInnerFeature(f) || FuzionHelpers.IsFieldLike(f)){
      return f;
    }
    var parent = stack.peek();
    var documentSymbol = Converters.ToDocumentSymbol(f);
    if(parent.getChildren() == null){
      parent.setChildren(new ArrayList<>());
    }
    parent.getChildren().add(documentSymbol);

    stack.push(documentSymbol);
    f.declaredFeatures().forEach((fn, feature) -> {
      this.action(feature, feature.outer());
      feature.visit(this);
    });
    stack.pop();

    return f;
  }

}
