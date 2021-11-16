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
 * Source of class SourceText
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.server;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import dev.flang.lsp.server.util.ErrorHandling;
import dev.flang.lsp.server.util.IO;
import dev.flang.lsp.server.util.LSP4jUtils;

public class SourceText
{
  /**
   * currently open text documents and their contents
   */
  private static final ConcurrentHashMap<URI, String> textDocuments = new ConcurrentHashMap<URI, String>();

  public static void setText(URI uri, String text)
  {
    if (text == null)
      {
        ErrorHandling.WriteStackTraceAndExit(1);
      }
    textDocuments.put(uri, text);
  }

  public static Optional<String> getText(URI uri)
  {
    var text = textDocuments.get(uri);
    if (text != null)
      {
        return Optional.of(text);
      }
    return ReadFromDisk(uri);
  }

  public static String allTexts()
  {
    return textDocuments
      .entrySet()
      .stream()
      .map(e -> e.getKey().toString() + System.lineSeparator() + e.getValue())
      .collect(Collectors.joining(System.lineSeparator()));
  }

  public static Optional<String> getText(TextDocumentPositionParams params)
  {
    return getText(LSP4jUtils.getUri(params));
  }

  private static Optional<String> ReadFromDisk(URI uri)
  {
    var path = IO.PathOf(uri);
    try
      {
        var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        return Optional.ofNullable(String.join(System.lineSeparator(), lines));
      }
    catch (IOException e)
      {
        return Optional.empty();
      }
  }

  public static String LineAt(TextDocumentPositionParams param)
  {
    return SourceText.getText(param)
      .get()
      .split("\n")[param.getPosition().getLine()];
  }

  public static String RestOfLine(TextDocumentPositionParams param)
  {
    var start = param.getPosition();
    var line = LineAt(param);
    return line.length() <= start.getCharacter() ? "": line.substring(start.getCharacter());
  }

  /**
   * extract range of source
   * @param uri
   * @param range
   * @return
   */
  public static String getText(URI uri, Range range)
  {
    var lines = getText(uri)
      .get()
      .lines()
      .skip(range.getStart().getLine())
      .limit(range.getEnd().getLine() - range.getStart().getLine() + 1)
      .toList();
    if (lines.size() == 1)
      {
        return lines.get(0).substring(range.getStart().getCharacter(), range.getEnd().getCharacter());
      }
    var result = "";
    for(int i = 0; i < lines.size(); i++)
      {
        // first line
        if (i == 0)
          {
            result += lines.get(i).substring(range.getStart().getCharacter()) + System.lineSeparator();
          }
        // last line
        else if (i + 1 == lines.size())
          {
            result += lines.get(i).substring(0, range.getEnd().getCharacter());
          }
        // middle line
        else
          {
            result += lines.get(i) + System.lineSeparator();
          }
      }
    return result;
  }


}
