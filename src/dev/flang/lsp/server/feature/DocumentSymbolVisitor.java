package dev.flang.lsp.server.feature;

import java.util.ArrayList;
import java.util.Stack;

import org.eclipse.lsp4j.DocumentSymbol;

import dev.flang.ast.Feature;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.Stmnt;
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
    var documentSymbol = FuzionHelpers.ToDocumentSymbol(f);
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
