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
 * Source of class RenameTest
 *
 *---------------------------------------------------------------------*/


package test.flang.lsp.server.feature;

import java.net.URI;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.RenameParams;
import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.feature.Rename;
import dev.flang.shared.SourceText;
import test.flang.lsp.server.ExtendedBaseTest;

public class RenameTest extends ExtendedBaseTest
{

  public static RenameParams Params(URI uri, int line, int character, String newName)
  {
    var cursor = Cursor(uri, line, character);
    return new RenameParams(TextDocument(cursor), Position(cursor), "h");
  }

  @Test
  public void PrepareRename() throws Exception
  {
    var sourceText = """
      factors is
        print(i i32) =>
          yak " $i"
        (1..12) | fun print
            """;

    SourceText.setText(uri1, sourceText);

    assertEquals("print", Rename.getPrepareRenameResult(TextDocumentPosition(Cursor(uri1, 1, 2))).getPlaceholder());
    assertEquals("print", Rename.getPrepareRenameResult(TextDocumentPosition(Cursor(uri1, 3, 16))).getPlaceholder());
    assertEquals("print", Rename.getPrepareRenameResult(TextDocumentPosition(Cursor(uri1, 1, 7))).getPlaceholder());
    assertEquals("i", Rename.getPrepareRenameResult(    TextDocumentPosition(Cursor(uri1, 1, 8))).getPlaceholder());
  }

  @Test
  public void RenameResultTypes()
  {
    // Note that the two whitespaces between next and Towers_Disk are
    // intentional
    var sourceText = """
      ex is
        Towers_Disk(size i32, next  Towers_Disk) ref is
            """;
    SourceText.setText(uri1, sourceText);

    var textEdits =
      Rename.getWorkspaceEdit(Params(uri1, 1, 2, "Tower_Disk"))
        .getChanges()
        .values()
        .stream()
        .flatMap(f -> f.stream())
        .collect(Collectors.toList());

    assertEquals(2, textEdits.size());

    assertTrue(textEdits.stream().anyMatch(edit -> {
      return edit.getRange().getStart().getLine() == 1
        && edit.getRange().getStart().getCharacter() == 2
        && edit.getRange().getEnd().getLine() == 1
        && edit.getRange().getEnd().getCharacter() == 13;
    }));

    assertTrue(textEdits.stream().anyMatch(edit -> {
      return edit.getRange().getStart().getLine() == 1
        && edit.getRange().getStart().getCharacter() == 30
        && edit.getRange().getEnd().getLine() == 1
        && edit.getRange().getEnd().getCharacter() == 41;
    }));
  }

  @Test
  public void RenameChoice() throws Exception
  {
    var sourceText = """
      ex is
        Towers_Disk(size i32, next nil|Towers_Disk) ref is
        b Towers_Disk|nil := nil
            """;
    SourceText.setText(uri1, sourceText);

    var textEdits =
      Rename.getWorkspaceEdit(Params(uri1, 1, 33, "Tower_Disk"))
        .getChanges()
        .values()
        .stream()
        .flatMap(f -> f.stream())
        .collect(Collectors.toList());

    assertEquals(3, textEdits.size());

    assertTrue(textEdits.stream().anyMatch(edit -> {
      return edit.getRange().getStart().getLine() == 1
        && edit.getRange().getStart().getCharacter() == 2
        && edit.getRange().getEnd().getLine() == 1
        && edit.getRange().getEnd().getCharacter() == 13;
    }));

    assertTrue(textEdits.stream().anyMatch(edit -> {
      return edit.getRange().getStart().getLine() == 1
        && edit.getRange().getStart().getCharacter() == 33
        && edit.getRange().getEnd().getLine() == 1
        && edit.getRange().getEnd().getCharacter() == 44;
    }));

    assertTrue(textEdits.stream().anyMatch(edit -> {
      return edit.getRange().getStart().getLine() == 2
        && edit.getRange().getStart().getCharacter() == 4
        && edit.getRange().getEnd().getLine() == 2
        && edit.getRange().getEnd().getCharacter() == 15;
    }));
  }

  @Test
  public void RenameSetted()
  {
    var sourceText = """
      ex =>
        a := 0
        set a := 2
            """;

    SourceText.setText(uri1, sourceText);

    var textEdits =
      Rename.getWorkspaceEdit(Params(uri1, 1, 2, "b"))
        .getChanges()
        .values()
        .stream()
        .flatMap(f -> f.stream())
        .collect(Collectors.toList());

    assertEquals(2, textEdits.size());

    assertTrue(textEdits.stream().anyMatch(edit -> {
      return edit.getRange().getStart().getLine() == 2
        && edit.getRange().getStart().getCharacter() == 6
        && edit.getRange().getEnd().getLine() == 2
        && edit.getRange().getEnd().getCharacter() == 7;
    }));
  }

  @Test
  public void RenameFun() throws Exception
  {
    var sourceText = """
      factors is
        print(i i32) =>
          yak " $i"
        (1..12) | fun print
            """;

    SourceText.setText(uri1, sourceText);

    var values = Rename.getWorkspaceEdit(Params(uri1, 1, 2, "write"))
      .getChanges()
      .values();
    assertEquals(1, values.size());
    var textEdits = values.stream().findFirst().get();
    assertEquals(2, textEdits.size());
    assertTrue(textEdits.stream().anyMatch(edit -> {
      return edit.getRange().getStart().getLine() == 3
        && edit.getRange().getStart().getCharacter() == 16
        && edit.getRange().getEnd().getLine() == 3
        && edit.getRange().getEnd().getCharacter() == 21;
    }));

  }

  @Test
  public void RenameArg()
  {
    var sourceText = """
      ex =>
        a is
        b (a a) is
          say a
          """;
    SourceText.setText(uri1, sourceText);
    var textEdits = Rename.getWorkspaceEdit(Params(uri1, 3, 8, "c"))
      .getChanges()
      .values()
      .stream()
      .findFirst()
      .get();

    assertEquals(2, textEdits.size());

    assertTrue(textEdits.stream().anyMatch(edit -> {
      return edit.getRange().getStart().getLine() == 2
        && edit.getRange().getStart().getCharacter() == 5
        && edit.getRange().getEnd().getLine() == 2
        && edit.getRange().getEnd().getCharacter() == 6;
    }));

    assertTrue(textEdits.stream().anyMatch(edit -> {
      return edit.getRange().getStart().getLine() == 3
        && edit.getRange().getStart().getCharacter() == 8
        && edit.getRange().getEnd().getLine() == 3
        && edit.getRange().getEnd().getCharacter() == 9;
    }));

  }

  @Test
  public void RegressionRenameMandelbrotImage()
  {
    SourceText.setText(uri1, Mandelbrot);
    var atMandelbrotImage = Params(uri1, 7, 10, "mandelbrot_image");
    assertEquals(2, Rename.getWorkspaceEdit(atMandelbrotImage).getChanges().values().stream().findFirst().get().size());
  }

  @Test
  public void RegressionRenameBounce()
  {
    SourceText.setText(uri1, Read("test_data/bounce.fz"));
    var atMandelbrotImage = Params(uri1, 2, 4, "rand");
    assertEquals(3, Rename.getWorkspaceEdit(atMandelbrotImage).getChanges().values().stream().findFirst().get().size());
  }

  @Test
  public void RenameLamdaArg()
  {
    var sourceText = """
      a =>
        (1..10).reduce<string> "" ((r,x) ->
          c := x.asString
          r.asString
          )
          """;
    SourceText.setText(uri1, sourceText);

    var textEdits = Rename.getWorkspaceEdit(Params(uri1, 2, 9, "h"))
      .getChanges()
      .values()
      .stream()
      .findFirst()
      .get();

    assertEquals(2, textEdits.size());

    assertTrue(textEdits.stream().anyMatch(edit -> {
      return edit.getRange().getStart().getLine() == 2
        && edit.getRange().getStart().getCharacter() == 9
        && edit.getRange().getEnd().getLine() == 2
        && edit.getRange().getEnd().getCharacter() == 10;
    }));

    assertTrue(textEdits.stream().anyMatch(edit -> {
      return edit.getRange().getStart().getLine() == 1
        && edit.getRange().getStart().getCharacter() == 32
        && edit.getRange().getEnd().getLine() == 1
        && edit.getRange().getEnd().getCharacter() == 33;
    }));

  }

}
