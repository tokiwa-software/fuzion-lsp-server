package dev.flang.lsp.server.feature;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import dev.flang.lsp.server.Util;

public class Hovering {

  public static Hover getHover(HoverParams params) {
    var call = Util.getClosestCall(params);
    if(call.isEmpty()){
      return null;
    }
    var line = params.getPosition().getLine();
    var character_position = params.getPosition().getCharacter();
    var range = new Range(new Position(line,character_position), new Position(line, character_position));

    var markupContent = new MarkupContent(MarkupKind.MARKDOWN, call.get().calledFeature().toString());
    return new Hover(markupContent, range);
  }

}
