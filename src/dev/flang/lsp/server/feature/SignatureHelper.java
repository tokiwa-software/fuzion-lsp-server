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

import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureInformation;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Call;
import dev.flang.lsp.server.util.CallTool;
import dev.flang.lsp.server.util.FeatureTool;
import dev.flang.lsp.server.util.FuzionParser;
import dev.flang.lsp.server.util.QueryAST;

public class SignatureHelper
{

  public static SignatureHelp getSignatureHelp(SignatureHelpParams params)
  {
    Optional<Call> call = QueryAST.callAt(params);

    if (call.isEmpty())
      {
        return new SignatureHelp();
      }

    var featureOfCall =
      call.get().target instanceof Call callTarget
                                                   ? Optional.of(callTarget.calledFeature())
                                                   : CallTool.featureOf(call.get());

    if (featureOfCall.isEmpty())
      {
        return new SignatureHelp();
      }

    return getSignatureHelp(call.get(), featureOfCall.get());
  }

  private static SignatureHelp getSignatureHelp(Call call, AbstractFeature featureOfCall)
  {
    var consideredCallTargets_declaredOrInherited = FuzionParser.DeclaredOrInheritedFeatures(featureOfCall);
    var consideredCallTargets_outerFeatures =
      FeatureTool.outerFeatures(featureOfCall).flatMap(f -> FuzionParser.DeclaredFeatures(f));

    var consideredFeatures =
      Stream.concat(consideredCallTargets_declaredOrInherited, consideredCallTargets_outerFeatures);

    var calledFeatures = consideredFeatures
      .filter(f -> featureNameMatchesCallName(f, call));

    // NYI how to "intelligently" sort the signatureinfos?
    return new SignatureHelp(calledFeatures.map(f -> SignatureInformation(f)).collect(Collectors.toList()), 0, 0);
  }

  private static SignatureInformation SignatureInformation(AbstractFeature feature)
  {
    var description = new MarkupContent(MarkupKind.MARKDOWN, FeatureTool.CommentOfInMarkdown(feature));
    return new SignatureInformation(FeatureTool.ToLabel(feature), description,
      ParameterInfo(feature));
  }

  private static boolean featureNameMatchesCallName(AbstractFeature f, Call call)
  {
    return f.featureName().baseName().equals(call.name);
  }

  private static List<ParameterInformation> ParameterInfo(AbstractFeature calledFeature)
  {
    return calledFeature.arguments()
      .stream()
      .map(
        arg -> new ParameterInformation("NYI" + arg.featureName().baseName() + " " + arg.thisType().toString()))
      .collect(Collectors.toList());
  }

}
