package dev.flang.lsp.server.records;
import dev.flang.fe.FrontEndOptions;
import dev.flang.mir.MIR;

public record ParserCacheRecord(MIR mir, FrontEndOptions frontEndOptions) {

}
