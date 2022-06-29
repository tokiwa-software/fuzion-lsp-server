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

package dev.flang.shared;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.flang.util.ANY;
import dev.flang.util.FuzionConstants;
import dev.flang.util.SourcePosition;

public class SourceText extends ANY
{
  /**
   * currently open text documents and their contents
   */
  private static final TreeMap<URI, String> textDocuments = new TreeMap<URI, String>();

  public static final Path FuzionHome = Path.of(System.getProperty("fuzion.home"));

  public static void setText(URI uri, String text)
  {
    if (PRECONDITIONS)
      require(text != null);

    textDocuments.put(uri, AddReplacementCharacterAfterNoneFullStopDots(text));
  }

  public static String getText(URI uri)
  {
    return textDocuments.computeIfAbsent(uri, u -> ReadFromDisk(u));
  }

  public static String allTexts()
  {
    return textDocuments
      .entrySet()
      .stream()
      .map(e -> e.getKey().toString() + System.lineSeparator() + e.getValue())
      .collect(Collectors.joining(System.lineSeparator()));
  }

  public static String getText(SourcePosition params)
  {
    return getText(UriOf(params));
  }

  private static String ReadFromDisk(URI uri)
  {
    var path = Path.of(uri);
    try
      {
        var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        return String.join(System.lineSeparator(), lines);
      }
    catch (IOException e)
      {
        ErrorHandling.WriteStackTraceAndExit(1, e);
        return null;
      }
  }

  private static final Pattern DotAtEOL = Pattern.compile("(^.+\\.)(\\s*$)", Pattern.MULTILINE);

  private static String AddReplacementCharacterAfterNoneFullStopDots(String text)
  {
    return DotAtEOL
      .matcher(text.replaceAll("\\$", "MAGIC_STRING_DOLLAR"))
      .replaceAll(x -> {
        String group1 = x.group(1);
        String group2 = x.group(2);
        String line = group1 + group2;
        // NYI right now this is just a hack...
        var isChoiceOf = Pattern.compile(".*choice\\s+of.*", Pattern.DOTALL);
        var isComment = Pattern.compile("\\s*#.*", Pattern.DOTALL);
        if (isChoiceOf.matcher(line).matches()
          || isComment.matcher(line).matches())
          {
            return line;
          }
        return group1 + "�" + group2;
      })
      .replaceAll("MAGIC_STRING_DOLLAR", "\\&");
  }

  public static String LineAt(SourcePosition param)
  {
    return SourceText.getText(param)
      .split("\n")[param._line - 1];
  }

  public static URI UriOf(SourcePosition sourcePosition)
  {
    return Path.of(sourcePosition._sourceFile._fileName.toString()
      .replace(FuzionConstants.SYMBOLIC_FUZION_HOME.toString(), FuzionHome.toString())).toUri();
  }

}
