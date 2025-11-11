package seq

import circt.stage.ChiselStage

object MultiplierGen extends App {
  ChiselStage.emitSystemVerilog(
    new Multiplier(width = 32, unroll = 8),
    Array.empty[String],
    Array("-o", "build/Multiplier.sv", "--disable-all-randomization")
  )
}


