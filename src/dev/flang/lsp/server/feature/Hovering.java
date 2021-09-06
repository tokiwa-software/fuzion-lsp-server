package dev.flang.lsp.server.feature;

import java.util.stream.Collectors;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Range;

import dev.flang.ast.Assign;
import dev.flang.ast.Call;
import dev.flang.ast.Type;
import dev.flang.ast.Function;
import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.Util;

public class Hovering
{

  public static Hover getHover(HoverParams params)
  {
    var astItems = FuzionHelpers.getSuitableASTItems(params).stream()
        .filter(x -> x instanceof Call || x instanceof Assign || x instanceof Function || x instanceof Type)
        .collect(Collectors.toList());

    if (astItems.isEmpty())
      {
        return null;
      }
    return getHover(params, astItems.get(0));
  }

  private static Hover getHover(HoverParams params, Object astItem)
  {
    // NYI make more exact
    var range = Util.toRange(params.getPosition());
    if (astItem instanceof Call)
      {
        return getHover(range, (Call) astItem);
      }
    if (astItem instanceof Assign)
      {
        return getHover(range, (Assign) astItem);
      }
    if (astItem instanceof Function)
      {
        return getHover(range, (Function) astItem);
      }
    if (astItem instanceof Type)
      {
        return getHover(range, (Type) astItem);
      }
    return null;
  }

  private static Hover getHover(Range range, Type type)
  {
    var markupContent = new MarkupContent(MarkupKind.MARKDOWN, "Type: " + type);
    return new Hover(markupContent, range);
  }

  private static Hover getHover(Range range, Function function)
  {
    var markupContent = new MarkupContent(MarkupKind.MARKDOWN, "Function: " + function);
    return new Hover(markupContent, range);
  }

  private static Hover getHover(Range range, Assign assign)
  {
    var markupContent = new MarkupContent(MarkupKind.MARKDOWN, "Assignment: " + assign);
    return new Hover(markupContent, range);
  }

  private static Hover getHover(Range range, Call call)
  {
    var markupContent = new MarkupContent(MarkupKind.MARKDOWN, "Call: " + call.calledFeature());
    return new Hover(markupContent, range);
  }

}
