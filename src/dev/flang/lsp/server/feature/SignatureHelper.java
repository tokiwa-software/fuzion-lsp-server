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
 * Source of class SignatureHelper
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server.feature;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureInformation;

import dev.flang.ast.Call;
import dev.flang.ast.Feature;
import dev.flang.lsp.server.Converters;
import dev.flang.lsp.server.FuzionHelpers;

public class SignatureHelper
{

  public static SignatureHelp getSignatureHelp(SignatureHelpParams params)
  {
    Optional<Call> call = FuzionHelpers.callAt(params);

    if (call.isEmpty())
      {
        return new SignatureHelp();
      }

    var featureOfCall =
      call.get().target instanceof Call callTarget ? callTarget.calledFeature(): FuzionHelpers.featureOf(call.get());

    var consideredCallTargets_declaredOrInherited = featureOfCall.declaredOrInheritedFeatures().values().stream();
    var consideredCallTargets_outerFeatures =
      FuzionHelpers.outerFeatures(featureOfCall).flatMap(f -> f.declaredFeatures().values().stream());

    var consideredFeatures =
      Stream.concat(consideredCallTargets_declaredOrInherited, consideredCallTargets_outerFeatures);

    var calledFeatures = consideredFeatures
      .filter(f -> featureNameMatchesCallName(f, call.get()));

    // NYI how to "intelligently" sort the signatureinfos?
    return new SignatureHelp(calledFeatures.map(f -> SignatureInformation(f)).collect(Collectors.toList()), 0, 0);
  }

  private static SignatureInformation SignatureInformation(Feature feature)
  {
    String description = FuzionHelpers.CommentOf(feature);
    return new SignatureInformation(Converters.ToLabel(feature), description,
      ParameterInfo(feature));
  }

  private static boolean featureNameMatchesCallName(Feature f, Call call)
  {
    return f.featureName().baseName().equals(call.name);
  }

  private static List<ParameterInformation> ParameterInfo(Feature calledFeature)
  {
    return calledFeature.arguments.stream()
      .map(
        arg -> new ParameterInformation("NYI" + arg.featureName().baseName() + " " + arg.thisType().toString()))
      .collect(Collectors.toList());
  }

}
