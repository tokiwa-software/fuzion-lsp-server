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

  /**
   * test if we can run more than one program
   * successfully and thus statically held stuff does not
   * get in the way.
   * @throws Exception
   */
  @Test @Disabled // too slow
  public void RunMultiple() throws Throwable
  {
    SourceText.setText(uri1, HelloWorld);
    SourceText.setText(uri2, PythagoreanTriple);

    ParserTool.Run(uri1);
    ParserTool.Run(uri2);
    var message = ParserTool.Run(uri1);

    assertEquals("Hello World!" + "\n", message);
  }

  @Test @Disabled // too slow
  public void RunSuccessfulAfterRunWithTimeoutException() throws Throwable
  {
    SourceText.setText(uri1, ManOrBoy);
    SourceText.setText(uri2, HelloWorld);
    SourceText.setText(uri3, PythagoreanTriple);

    // NYI this will not throw once fuzion gets faster, how to test properly?
    assertThrows(MaxExecutionTimeExceededException.class, () -> ParserTool.Run(uri1, 100));
    assertThrows(MaxExecutionTimeExceededException.class, () -> ParserTool.Run(uri3, 50));

    assertEquals("Hello World!" + "\n", ParserTool.Run(uri2));
  }

  @Test
  public void GenerateMatchCases() throws Throwable
  {
    SourceText.setText(uri1, """
      ex =>
        a is
          b choice string bool i32 i64 is
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
        "    string string =>" + System.lineSeparator() +
        "    bool bool =>" + System.lineSeparator() +
        "    i32 i32 =>" + System.lineSeparator() +
        "    i64 i64 =>",
      result.getChanges().values().stream().findFirst().get().get(0).getNewText());

  }

}
