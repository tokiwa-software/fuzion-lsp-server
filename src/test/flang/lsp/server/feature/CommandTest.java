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
 * Source of class CommandTest
 *
 *---------------------------------------------------------------------*/


package test.flang.lsp.server.feature;

import java.util.List;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonPrimitive;

import dev.flang.lsp.server.feature.Commands;
import dev.flang.shared.ParserTool;
import dev.flang.shared.SourceText;
import dev.flang.shared.concurrent.MaxExecutionTimeExceededException;
import test.flang.shared.BaseTest;

public class CommandTest extends BaseTest
{

  @Test
  public void GenerateMatchCases() throws Throwable
  {
    SourceText.setText(uri1, """
      ex =>
        a is
          b choice String bool i32 i64 is
            "string"
        match a.b
        """);

    var result = (WorkspaceEdit) Commands
      .Execute(
        new ExecuteCommandParams("codeActionGenerateMatchCases",
          List.of(new JsonPrimitive(uri1.toString()), new JsonPrimitive(4), new JsonPrimitive(3))))
      .get();

    assertEquals(
      System.lineSeparator() +
        "    string String =>" + System.lineSeparator() +
        "    bool bool =>" + System.lineSeparator() +
        "    i32 i32 =>" + System.lineSeparator() +
        "    i64 i64 =>",
      result.getChanges().values().stream().findFirst().get().get(0).getNewText());

  }

}
