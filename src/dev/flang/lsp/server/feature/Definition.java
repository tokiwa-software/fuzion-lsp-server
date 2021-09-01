package dev.flang.lsp.server.feature;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.lsp.server.Util;
import dev.flang.ast.*;

public class Definition
{
  public static Either<List<? extends Location>, List<? extends LocationLink>> getDefinitionLocation(
    DefinitionParams params)
  {
    var closestFeature = Util.getClosestFeature(params);
    if(closestFeature.isEmpty()){
      return null;
    }
    var visitedCalls = new ArrayList<Call>();

    closestFeature.get().visit(new FeatureVisitor() {
      @Override
      public Expr action(Call c, Feature outer)
      {
        visitedCalls.add(c);
        return c;
      }
    });

    var closestCall = visitedCalls.stream().sorted(Comparator.comparing(statement -> {
      if (Math.abs(statement.pos()._line - 1 - params.getPosition().getLine()) > 0)
        {
          return Integer.MAX_VALUE;
        }
      return Math.abs(statement.pos()._column - 1 - params.getPosition().getCharacter());
    })).findFirst();

    if(closestCall.isEmpty()){
      return null;
    }

    Location location = Util.ToLocation(closestCall.get().calledFeature());

    return Either.forLeft(Arrays.asList(location));
  }

}