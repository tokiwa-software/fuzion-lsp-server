package dev.flang.lsp.server.feature;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceParams;

import dev.flang.lsp.server.FuzionHelpers;

public class References
{

  public static List<? extends Location> getReferences(ReferenceParams params)
  {
    var optionalFeature = FuzionHelpers.getFeaturesDesc(params).findFirst();
    if (optionalFeature.isEmpty())
      {
        return List.of();
      }
    return FuzionHelpers.callsTo(optionalFeature.get())
      .map(call -> FuzionHelpers.ToLocation(call.pos()))
      .collect(Collectors.toList());
  }

}
