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
 * Source of class DiagnosticsTest
 *
 *---------------------------------------------------------------------*/

package test.flang.lsp.server.feature;

import java.util.stream.Collectors;

import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.feature.Diagnostics;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class DiagnosticsTest extends ExtendedBaseTest
{
  @Test
  @Disabled // failing
  public void DiagnosticsShowAirErrors()
  {
    var sourceText = """
      t is
        node(parent node|nil) is
        arr := array<node> 1 (i -> (node nil))""";
    SourceText.setText(uri1, sourceText);
    var diagnostics = Diagnostics.getDiagnostics(uri1);
    assertEquals(1, diagnostics.count());
  }

  @Test
  public void Unused()
  {
    var sourceText = """
      ex is
        a := 0
        b := "asdf"
        say b
      """;
    SourceText.setText(uri1, sourceText);
    var diagnostics = Diagnostics.getDiagnostics(uri1).collect(Collectors.toList());
    assertEquals(1, diagnostics.size());
    assertTrue(diagnostics.get(0).getTags().get(0).equals(DiagnosticTag.Unnecessary));
  }

  @Test
  public void UnusedArgument()
  {
    var sourceText = """
      storage =>
        store(data (array store)|(array nil)) is
        say "asdf"
                  """;
    SourceText.setText(uri1, sourceText);
    var diagnostics = Diagnostics.getDiagnostics(uri1).collect(Collectors.toList());
    assertEquals(2, diagnostics.size());
    assertEquals(1, diagnostics.get(0).getRange().getStart().getLine());
    assertEquals(2, diagnostics.get(0).getRange().getStart().getCharacter());
    assertEquals(1, diagnostics.get(0).getRange().getEnd().getLine());
    assertEquals(7, diagnostics.get(0).getRange().getEnd().getCharacter());
  }

  @Test
  public void BadFeatureName()
  {
    var sourceText = """
      storage =>
        badName =>
          "hello"
        bad_name ref is

        # get rid of unused diagnostics
        say badName
        say bad_name
                  """;
    SourceText.setText(uri1, sourceText);
    var diagnostics = Diagnostics.getDiagnostics(uri1).collect(Collectors.toList());
    assertEquals(2, diagnostics.size());
    assertEquals(1, diagnostics.get(0).getRange().getStart().getLine());
    assertEquals(2, diagnostics.get(0).getRange().getStart().getCharacter());
    assertEquals(1, diagnostics.get(0).getRange().getEnd().getLine());
    assertEquals(9, diagnostics.get(0).getRange().getEnd().getCharacter());
    assertEquals(3, diagnostics.get(1).getRange().getStart().getLine());
    assertEquals(2, diagnostics.get(1).getRange().getStart().getCharacter());
    assertEquals(3, diagnostics.get(1).getRange().getEnd().getLine());
    assertEquals(10, diagnostics.get(1).getRange().getEnd().getCharacter());
  }

  @Test
  public void BadRefName()
  {
    var sourceText = """
      storage =>
        bad_name ref is
          unit
        Bad_name ref is
          unit
        Bad_NaMe ref is
          unit
        Good_Name ref is
          unit
        say bad_name
        say Bad_name
        say Good_Name
        say Bad_NaMe
                  """;
    SourceText.setText(uri1, sourceText);
    var diagnostics = Diagnostics.getDiagnostics(uri1).collect(Collectors.toList());
    assertEquals(3, diagnostics.size());
    assertEquals(1, diagnostics.get(0).getRange().getStart().getLine());
    assertEquals(2, diagnostics.get(0).getRange().getStart().getCharacter());
    assertEquals(1, diagnostics.get(0).getRange().getEnd().getLine());
    assertEquals(10, diagnostics.get(0).getRange().getEnd().getCharacter());
    assertEquals(3, diagnostics.get(1).getRange().getStart().getLine());
    assertEquals(5, diagnostics.get(2).getRange().getStart().getLine());
  }

  @Test
  public void ErrorAtEOF()
  {
    var sourceText = """
      a is

        reducer(T, U type, t T, f U -> T) is
        transducer(T, U, V, W type, X type : reducer T U, Y type : reducer V W, r X) Y is
        filter(T, U, V, W type, X type : reducer T U, Y type : reducer V W, r X) Y is


          """;
    SourceText.setText(uri1, sourceText);
    var diagnostics = Diagnostics.getDiagnostics(uri1);
    assertTrue(diagnostics.anyMatch(x -> x.getRange().getStart().getLine() == 6));
  }


  @Test
  public void RedefError()
  {
    var sourceText = """
      ex is
        redef not_redefing is
                      """;
    SourceText.setText(uri1, sourceText);
    var diagnostics = Diagnostics.getDiagnostics(uri1)
      .filter(x -> x.getSeverity().equals(DiagnosticSeverity.Error))
      .collect(Collectors.toList());
    assertEquals(1, diagnostics.size());
    assertEquals(2, diagnostics.get(0).getRange().getStart().getCharacter());
    assertEquals(7, diagnostics.get(0).getRange().getEnd().getCharacter());
  }

  @Test
  @Disabled // failing
  public void IfElseBranchNoRedefInfo()
  {
    var sourceText = """
      ex =>
        if(true)
          a := 0
        else
          a := 1
          """;
    SourceText.setText(uri1, sourceText);
    var diagnostics = Diagnostics.getDiagnostics(uri1)
      .filter(x -> x.getSeverity().equals(DiagnosticSeverity.Information))
      .collect(Collectors.toList());
    assertEquals(0, diagnostics.size());
  }

}
