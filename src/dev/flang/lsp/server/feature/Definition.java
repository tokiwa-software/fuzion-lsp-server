package dev.flang.lsp.server.feature;

import java.util.Arrays;
import java.util.List;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.lsp.server.Util;

public class Definition
{
  public static Either<List<? extends Location>, List<? extends LocationLink>> getDefinitionLocation(
    DefinitionParams params)
  {

    var closestCall = Util.getClosestCall(params);

    if(closestCall.isEmpty()){
      return null;
    }

    Location location = Util.ToLocation(closestCall.get().calledFeature());

    return Either.forLeft(Arrays.asList(location));
  }

}