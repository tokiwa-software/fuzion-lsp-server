package dev.flang.lsp.server.feature;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceParams;

import dev.flang.lsp.server.Converters;
import dev.flang.lsp.server.FuzionHelpers;
import dev.flang.lsp.server.Util;

/**
 * return list of references for feature at cursor position
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_references
 */
public class References
{

  public static List<? extends Location> getReferences(ReferenceParams params)
  {
    var feature = FuzionHelpers.featureAt(params);
    if (feature.isEmpty())
      {
        return List.of();
      }
    return FuzionHelpers.callsTo(Util.getUri(params), feature.get())
      .map(call -> Converters.ToLocation(call.pos()))
      .collect(Collectors.toList());
  }

}
