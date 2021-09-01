package dev.flang.lsp.server.feature;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public class Hovering {

  public static Hover getHover(HoverParams params) {
    var line = params.getPosition().getLine();
    var character_position = params.getPosition().getCharacter();
    var range = new Range(new Position(line,character_position), new Position(line,character_position + 3));

    var markupContent = new MarkupContent(MarkupKind.MARKDOWN, """
    ## Hello from Fuzion LSP
    - one
    - two
    """);
    return new Hover(markupContent, range);
  }

}
