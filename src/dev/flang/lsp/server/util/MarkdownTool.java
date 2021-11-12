package dev.flang.lsp.server.util;

import java.util.Arrays;
import java.util.stream.Collectors;

public class MarkdownTool
{

  public static String Escape(String str)
  {
    return Arrays.stream(new String[]
      {
          "\\", "`", "*", "_", "{", "}", "[", "]", "(", ")", "#", "+", "-", ".", "!"
      })
      .reduce(str, (text, token) -> {
        return text.replaceAll("\\" + token, "\\" + token);
      });
  }

  public static String Italic(String str)
  {
    return Arrays.stream(str.split(System.lineSeparator()))
      .map(l -> "*" + l + "*")
      .collect(Collectors.joining(System.lineSeparator()));
  }

  public static String Blockquote(String str)
  {
    return Arrays.stream(str.split(System.lineSeparator()))
      .map(l -> "> " + l)
      .collect(Collectors.joining(System.lineSeparator()));
  }

  public static String Bold(String str)
  {
    return Arrays.stream(str.split(System.lineSeparator()))
      .map(l -> "**" + l + "**")
      .collect(Collectors.joining(System.lineSeparator()));
  }

}
