package dev.flang.lsp.server.records;
import dev.flang.fe.FrontEndOptions;
import dev.flang.mir.MIR;

public record ParserCache(MIR mir, FrontEndOptions frontEndOptions) {

}
