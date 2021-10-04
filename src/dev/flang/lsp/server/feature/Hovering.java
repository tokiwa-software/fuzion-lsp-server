package dev.flang.lsp.server.feature;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

import dev.flang.lsp.server.Converters;
import dev.flang.lsp.server.FuzionHelpers;

/**
 * on hover returns signature of call
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_hover
 */
public class Hovering
{

  public static Hover getHover(HoverParams params)
  {
    var range = Converters.ToRange(params);

    var feature = FuzionHelpers.getFeatureAt(params);
    if (feature.isEmpty())
      {
        return null;
      }

    var markupContent = new MarkupContent(MarkupKind.MARKDOWN, Converters.ToLabel(feature.get()));
    return new Hover(markupContent, range);
  }

}
