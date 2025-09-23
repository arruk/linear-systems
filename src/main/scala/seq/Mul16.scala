package seq

import circt.stage.ChiselStage

object Mul16Gen extends App {
  // 1) UMA ÚNICA SAÍDA .sv (sem split):
  ChiselStage.emitSystemVerilog(
    new Mul16,
    Array.empty[String],
    Array(
      // gera arquivo único no caminho indicado:
      "-o", "build/Mul16.sv",
      "--disable-all-randomization"
    )
  )
}
