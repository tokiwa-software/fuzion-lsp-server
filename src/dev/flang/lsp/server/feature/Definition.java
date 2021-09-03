package dev.flang.lsp.server.feature;

import java.util.Arrays;
import java.util.List;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.ast.Call;
import dev.flang.ast.Type;
import dev.flang.lsp.server.Util;

public class Definition
{
  public static Either<List<? extends Location>, List<? extends LocationLink>> getDefinitionLocation(
      DefinitionParams params)
  {
    var astItems = Util.getPossibleASTItems(params, x -> x.getValue() instanceof Call || x.getValue() instanceof Type);

    if (astItems.isEmpty())
      {
        return null;
      }

    return getDefinition(params, astItems.get(0).getValue());
  }

  private static Either<List<? extends Location>, List<? extends LocationLink>> getDefinition(DefinitionParams params,
      Object obj)
  {
    if(obj instanceof Call){
      return getDefinition((Call) obj);
    }
    if(obj instanceof Type){
      return getDefinition((Type) obj);
    }
    return null;
  }

  private static Either<List<? extends Location>, List<? extends LocationLink>> getDefinition(Type type)
  {
    Location location = Util.ToLocation(type.pos);
    return Either.forLeft(Arrays.asList(location));
  }

  private static Either<List<? extends Location>, List<? extends LocationLink>> getDefinition(Call call)
  {
    Location location = Util.ToLocation(call.calledFeature().pos());
    return Either.forLeft(Arrays.asList(location));
  }

}