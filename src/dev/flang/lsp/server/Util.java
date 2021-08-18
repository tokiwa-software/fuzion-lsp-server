package dev.flang.lsp.server;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Random;

public class Util {
  static final PrintStream DEV_NULL = new PrintStream(OutputStream.nullOutputStream());

  static byte[] getBytes(String text) {
    byte[] byteArray = new byte[0];
    try {
      byteArray = text.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      System.exit(1);
    }
    return byteArray;
  }

  static File writeToTempFile(String text) {
    try {
      File tempFile = File.createTempFile(Util.randomString(), ".fz");
      tempFile.deleteOnExit();

      FileWriter writer = new FileWriter(tempFile);
      writer.write(text);
      writer.close();
      return tempFile;
    } catch (IOException e) {
      System.exit(1);
      return null;
    }
  }

  // https://www.baeldung.com/java-random-string
  static String randomString() {
    int leftLimit = 97; // letter 'a'
    int rightLimit = 122; // letter 'z'
    int targetStringLength = 10;
    Random random = new Random();

    return random.ints(leftLimit, rightLimit + 1).limit(targetStringLength)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
  }

  static void WithRedirectedStdOut(Runnable runnable) {
    var out = System.out;
    try {
      System.setOut(DEV_NULL);
      runnable.run();
    } finally {
      System.setOut(out);
    }
  }

  private static void WithTextInputStream(String text, Runnable runnable) {
    byte[] byteArray = getBytes(text);

    InputStream testInput = new ByteArrayInputStream(byteArray);
    InputStream old = System.in;
    try {
      System.setIn(testInput);
      runnable.run();
    } finally {
      System.setIn(old);
    }
  }


}
