package dev.flang.lsp.server.feature;

import java.util.stream.Collectors;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

import dev.flang.ast.Feature;
import dev.flang.ast.Call;
import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.Util;

public class Hovering
{

  public static Hover getHover(HoverParams params)
  {
    // NYI make more exact
    var range = Util.toRange(params.getPosition());

    var result = FuzionHelpers.getASTItemsOnLine(params)
      .stream()
      .map(astItem -> {
        if (astItem instanceof Feature)
          {
            return (Feature) astItem;
          }
        if (astItem instanceof Call)
          {
            return ((Call) astItem).calledFeature();
          }
        return null;
      })
      .filter(f -> f != null)
      .filter(f -> FuzionHelpers.IsRoutineOrRoutineDef(f))
      .filter(f -> !FuzionHelpers.IsAnonymousInnerFeature(f))
      .map(f -> FuzionHelpers.getLabel(f))
      .map(str -> "- " + str)
      .collect(Collectors.joining(System.lineSeparator()));
    var markupContent = new MarkupContent(MarkupKind.MARKDOWN, result);
    return new Hover(markupContent, range);
  }

}
