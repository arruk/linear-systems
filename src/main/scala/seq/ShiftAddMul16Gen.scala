package seq

import circt.stage.ChiselStage

object ShiftAddMul16Gen extends App {
  // Gera um ÚNICO arquivo SystemVerilog:
  ChiselStage.emitSystemVerilog(
    new ShiftAddMul16,           // gerador do módulo
    Array.empty[String],         // chisel args (não usados aqui)
    Array(                       // firtool options (CIRCT 1.62.1)
      "-o", "build/ShiftAddMul16.sv",   // saída única (use par "-o", caminho)
      "--disable-all-randomization"
    )
  )
}

