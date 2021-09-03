package dev.flang.lsp.server.feature;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Range;

import dev.flang.ast.Assign;
import dev.flang.ast.Call;
import dev.flang.ast.Stmnt;
import dev.flang.ast.Function;
import dev.flang.lsp.server.Util;

public class Hovering
{

  public static Hover getHover(HoverParams params)
  {
    var stmnt = Util.getClosestStmnt(params);
    if (stmnt.isEmpty())
      {
        return null;
      }
    return getHover(params, stmnt.get());
  }

  private static Hover getHover(HoverParams params, Stmnt stmnt)
  {
    // NYI make more exact
    var range = Util.toRange(params.getPosition());
    if (stmnt instanceof Call)
      {
        return getHover(range, (Call) stmnt);
      }
    if (stmnt instanceof Assign)
      {
        return getHover(range, (Assign) stmnt);
      }
    if (stmnt instanceof Function)
      {
        return getHover(range, (Function) stmnt);
      }
    return null;
  }

  private static Hover getHover(Range range, Function function)
  {
    var markupContent = new MarkupContent(MarkupKind.MARKDOWN, "Function: " + function.toString());
    return new Hover(markupContent, range);
  }

  private static Hover getHover(Range range, Assign assign)
  {
    var markupContent = new MarkupContent(MarkupKind.MARKDOWN, "Assignment: " + assign.toString());
    return new Hover(markupContent, range);
  }

  private static Hover getHover(Range range, Call call)
  {
    var markupContent = new MarkupContent(MarkupKind.MARKDOWN, "Call: " + call.calledFeature().toString());
    return new Hover(markupContent, range);
  }

}
