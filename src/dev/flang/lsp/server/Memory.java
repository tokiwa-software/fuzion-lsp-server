package dev.flang.lsp.server;

import java.util.TreeMap;

import dev.flang.ast.Feature;
import dev.flang.util.SourcePosition;

/**
 * Memory is used as a cache for some calculations
 */
public final class Memory
{
  public static final TreeMap<Feature, SourcePosition> EndOfFeature = new TreeMap<>();
}