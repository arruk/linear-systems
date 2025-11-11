package seq

import circt.stage.ChiselStage

object DividerGen extends App {
  ChiselStage.emitSystemVerilog(
    new Divider(width = 32, unroll = 8),
    Array.empty[String],
    Array("-o", "build/Divider.sv", "--disable-all-randomization")
  )
}


