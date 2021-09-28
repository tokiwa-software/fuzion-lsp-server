package dev.flang.lsp.server;

import java.util.TreeMap;

import dev.flang.ast.Feature;
import dev.flang.util.SourcePosition;

public final class Memory
{
  public static TreeMap<Feature, SourcePosition> EndOfFeature = new TreeMap<>();
}