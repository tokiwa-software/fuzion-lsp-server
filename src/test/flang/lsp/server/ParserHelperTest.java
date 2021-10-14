package test.flang.lsp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.flang.lsp.server.FuzionTextDocumentService;
import dev.flang.lsp.server.ParserHelper;
import dev.flang.util.Errors;

public class ParserHelperTest
{
  @Test
  void getMainFeatureTest()
  {
    FuzionTextDocumentService.setText("uri", """
HelloWorld is
  say "Hello World!"
            """);
    var mainFeature = ParserHelper.getMainFeature("uri");
    assertEquals(0, Errors.count());
    assertEquals(true, mainFeature.isPresent());
    assertEquals("HelloWorld", mainFeature.get().featureName().baseName());
    assertEquals("uri", ParserHelper.getUri(mainFeature.get().pos()));
  }
}
