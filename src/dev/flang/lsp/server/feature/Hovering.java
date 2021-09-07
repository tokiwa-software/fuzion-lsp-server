package dev.flang.lsp.server.feature;

import java.util.stream.Collectors;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.Util;

public class Hovering
{

  public static Hover getHover(HoverParams params)
  {
    var astItems = FuzionHelpers.getSuitableASTItems(params).stream().collect(Collectors.toList());

    // NYI make more exact
    var range = Util.toRange(params.getPosition());

    var result = astItems.stream().map(astItem -> astItem.getClass().getName() + ": " + astItem).map(str -> "- " + str)
        .collect(Collectors.joining(System.lineSeparator()));
    var markupContent = new MarkupContent(MarkupKind.MARKDOWN, result);
    return new Hover(markupContent, range);
  }

}
