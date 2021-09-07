package dev.flang.lsp.server.feature;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.ast.Call;
import dev.flang.ast.Type;
import dev.flang.ast.Feature;
import dev.flang.lsp.server.FuzionHelpers;

public class Definition
{
  public static Either<List<? extends Location>, List<? extends LocationLink>> getDefinitionLocation(
      DefinitionParams params)
  {
    var astItems = FuzionHelpers.getSuitableASTItems(params).stream().filter(x ->  x instanceof Feature || x instanceof Call || x instanceof Type).collect(Collectors.toList());

    if (astItems.isEmpty())
      {
        return null;
      }
    astItems.sort(Comparator.comparing(obj -> obj, (obj1, obj2) -> {
      if(obj1 instanceof Feature){
        return 1;
      }
      if(obj2 instanceof Feature){
        return -1;
      }
      if(obj1 instanceof Call){
        return 1;
      }
      if(obj2 instanceof Call){
        return -1;
      }
      return 0;
    }).reversed());

    return getDefinition(params, astItems.get(0));
  }

  private static Either<List<? extends Location>, List<? extends LocationLink>> getDefinition(DefinitionParams params,
      Object obj)
  {
    if(obj instanceof Feature){
      return getDefinition((Feature) obj);
    }
    if(obj instanceof Call){
      return getDefinition((Call) obj);
    }
    if(obj instanceof Type){
      return getDefinition((Type) obj);
    }
    System.err.println("not implemented Definition.getDefinition " + obj.getClass());
    System.exit(1);
    return null;
  }

	private static Either<List<? extends Location>, List<? extends LocationLink>> getDefinition(Feature obj)
	{
    // NYI find better way
    if(obj.toString().startsWith("INVISIBLE")){
      return getDefinition(obj.outer());
    }
    Location location = FuzionHelpers.ToLocation(obj.pos());
    return Either.forLeft(Arrays.asList(location));
	}

	private static Either<List<? extends Location>, List<? extends LocationLink>> getDefinition(Type type)
  {
    Location location = FuzionHelpers.ToLocation(type.featureOfType().pos);
    return Either.forLeft(Arrays.asList(location));
  }

  private static Either<List<? extends Location>, List<? extends LocationLink>> getDefinition(Call call)
  {
    Location location = FuzionHelpers.ToLocation(call.calledFeature().pos());
    return Either.forLeft(Arrays.asList(location));
  }

}