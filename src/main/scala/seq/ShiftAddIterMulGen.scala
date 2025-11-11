package seq

import circt.stage.ChiselStage

object ShiftAddIterMulGen extends App {
  // Ex.: 16 bits, unroll = 4
  ChiselStage.emitSystemVerilog(
    new ShiftAddIterMul(width = 16, unroll = 4),
    Array.empty[String],
    Array("-o", "build/ShiftAddIterMul_16x4.sv", "--disable-all-randomization")
  )
}

