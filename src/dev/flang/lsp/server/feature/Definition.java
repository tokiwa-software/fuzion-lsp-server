/*

This file is part of the Fuzion language server protocol implementation.

The Fuzion language server protocol implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language server protocol implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa Software GmbH, Germany
 *
 * Source of class Definition
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.feature;

import java.util.Arrays;
import java.util.List;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.ast.Feature;
import dev.flang.lsp.server.Converters;
import dev.flang.lsp.server.FuzionHelpers;

/**
 * tries to provide the definition of a call
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_definition
 */
public class Definition
{
  public static Either<List<? extends Location>, List<? extends LocationLink>> getDefinitionLocation(
      DefinitionParams params)
  {
    var feature = FuzionHelpers.featureAt(params);
    if(feature.isEmpty()){
      return null;
    }
    return getDefinition(feature.get());
  }

  private static Either<List<? extends Location>, List<? extends LocationLink>> getDefinition(Feature obj)
	{
    // NYI find better way
    if(obj.toString().startsWith("INVISIBLE")){
      return getDefinition(obj.outer());
    }
    Location location = Converters.ToLocation(obj.pos());
    return Either.forLeft(Arrays.asList(location));
	}

}