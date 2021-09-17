package dev.flang.lsp.server;

import java.util.TreeMap;

import dev.flang.ast.Feature;
import dev.flang.util.SourcePosition;

public final class Memory
{
  // NYI make threadsafe
  private static Feature Main = new Feature();

  public static Feature getMain()
  {
    return Main;
  }

  public static void setMain(Feature main)
  {
    Main = main;
    EndOfFeature.clear();
  }

  public static TreeMap<Feature, SourcePosition> EndOfFeature = new TreeMap<>();
}