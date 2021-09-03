package dev.flang.lsp.server.feature;

import java.util.Arrays;
import java.util.List;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.ast.Stmnt;
import dev.flang.ast.Call;
import dev.flang.lsp.server.Util;

public class Definition
{
  public static Either<List<? extends Location>, List<? extends LocationLink>> getDefinitionLocation(
      DefinitionParams params)
  {

    var stmnt = Util.getClosestStmnt(params);

    if (stmnt.isEmpty())
      {
        return null;
      }

    return getDefinition(params, stmnt.get());
  }

  private static Either<List<? extends Location>, List<? extends LocationLink>> getDefinition(DefinitionParams params,
      Stmnt stmnt)
  {
    if(stmnt instanceof Call){
      return getDefinition((Call) stmnt);
    }
    return null;
  }

  private static Either<List<? extends Location>, List<? extends LocationLink>> getDefinition(Call call)
  {
    Location location = Util.ToLocation(call.calledFeature());
    return Either.forLeft(Arrays.asList(location));
  }

}