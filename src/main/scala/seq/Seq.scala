package seq

import chisel3._
import chisel3.util._


class Mul16 extends Module {
  val io = IO(new Bundle {
    val a      = Input(UInt(16.W))
    val b      = Input(UInt(16.W))
    val lower  = Output(UInt(16.W))
    val upper  = Output(UInt(16.W))
  })

  // multiplicação normal em Scala/Chisel, usando operador '*'
  val product = io.a * io.b  // 32 bits

  // separar 16 bits inferiores e 16 bits superiores
  io.lower := product(15, 0)
  io.upper := product(31, 16)
}

class ShiftAddMul16 extends Module {
  val io = IO(new Bundle {
    val a     = Input(UInt(16.W))
    val b     = Input(UInt(16.W))
    val lower = Output(UInt(16.W))
    val upper = Output(UInt(16.W))
  })

  // Zero-extend 'a' para 32 bits antes de shiftar (evita growth de largura a cada shift)
  val a32 = io.a.pad(32) // UInt(32.W) em Chisel 6.x

  // Soma combinacional dos parciais: para cada bit i de b, soma (a32 << i) se b(i)=1
  val partials = (0 until 16).map { i =>
    Mux(io.b(i), a32 << i, 0.U(32.W))
  }

  // Use +& para preservar carry; ao final, pegue apenas os 32 LSBs do produto
  val productFull = partials.reduce(_ +& _)
  val product     = productFull(31, 0) // UInt(32.W)

  io.lower := product(15, 0)
  io.upper := product(31, 16)
}

class ShiftAddIterMul(val width: Int, val unroll: Int) extends Module {
  require(width == 32 || width == 64)
  require(unroll >= 1 && unroll <= width,
    s"unroll must be in [1, $width], got $unroll")

  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new Bundle {
      val a = UInt(width.W)
      val b = UInt(width.W)
    }))
    val out = Decoupled(new Bundle {
      val lower = UInt(width.W)
      val upper = UInt(width.W)
    })
    val busy = Output(Bool()) // opcional: indica operação em andamento
  })

  // Helpers
  private def ceilDiv(x: Int, y: Int) = (x + y - 1) / y
  private val steps      = ceilDiv(width, unroll)
  private val stepBits   = log2Ceil(steps + 1).max(1)

  // Registers de estado
  val acc     = RegInit(0.U((2*width).W))  // acumulador (produto parcial)
  val mcand   = RegInit(0.U((2*width).W))  // multiplicando alinhado em 2*width
  val mplier  = RegInit(0.U(width.W))      // multiplicador (vai sendo shiftado)
  val stepCnt = RegInit(0.U(stepBits.W))   // passos restantes
  val busy    = RegInit(false.B)           // FSM: idle/busy
  io.busy := busy

  // Pronto para aceitar nova req quando não ocupado
  io.in.ready := !busy

  // Soma combinacional dos parciais deste ciclo:
  // chunk = unroll LSBs do multiplicador; soma (mcand << j) se chunk(j)=1
  val chunk   = mplier(unroll-1, 0)
  val partialTerms = (0 until unroll).map { j =>
    val term = mcand << j
    Mux(chunk(j), term, 0.U(term.getWidth.W))
  }
  val partialSum = partialTerms.foldLeft(0.U((2*width).W))(_ +& _)

  // Saída pronta apenas quando terminamos todos os passos
  val done = busy && (stepCnt === 1.U)

  // Default de out
  io.out.valid       := done
  io.out.bits.lower  := (acc +& partialSum)(width-1, 0)           // LSBs do produto final
  io.out.bits.upper  := (acc +& partialSum)((2*width)-1, width)   // MSBs do produto final

  // FSM
  when (!busy) {
    // Estado IDLE: aceita nova operação
    when (io.in.fire) {
      acc     := 0.U
      mcand   := io.in.bits.a.asUInt.pad(2*width)
      mplier  := io.in.bits.b
      stepCnt := steps.U
      busy    := true.B
    }
  } .otherwise {
    // Estado BUSY: executa um passo por ciclo
    when (!done) {
      // passo intermediário: acumula parciais, shift de mcand e mplier, decrementa
      acc     := acc +& partialSum
      mcand   := mcand << unroll
      mplier  := mplier >> unroll
      stepCnt := stepCnt - 1.U
    } .otherwise {
      // passo final: o produto final já está exposto em io.out (acc + partialSum)
      when (io.out.fire) {
        busy := false.B
      }
    }
  }
}


class ShiftAddIterDiv(val width: Int, val unroll: Int) extends Module {
  require(width >= 1, s"width must be >= 1, got $width")
  require(unroll >= 1 && unroll <= width, s"unroll must be in [1,$width], got $unroll")

  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new Bundle {
      val dividend = UInt(width.W)
      val divisor  = UInt(width.W)
    }))
    val out = Decoupled(new Bundle {
      val quot = UInt(width.W)
      val rem  = UInt(width.W)
    })
    val busy = Output(Bool())
  })

  // --------- Helpers ----------
  private def ceilDiv(x: Int, y: Int) = (x + y - 1) / y
  private val steps      = ceilDiv(width, unroll)
  private val stepBits   = log2Ceil(steps + 1).max(1)
  private val lastIters  = if (width % unroll == 0) unroll else width % unroll // iterações ativas no último ciclo

  // --------- Estado ----------
  val remReg     = RegInit(0.U((width + 1).W))  // resto parcial (width+1 p/ evitar overflow no shift)
  val dvdReg     = RegInit(0.U(width.W))        // dividend (será "consumido" MSB-first via shift-left)
  val divReg     = RegInit(0.U(width.W))        // divisor (constante durante a divisão)
  val quotReg    = RegInit(0.U(width.W))        // quociente sendo formado (MSB-first, via shifts)
  val stepCnt    = RegInit(0.U(stepBits.W))     // passos restantes (ciclos)
  val busyReg    = RegInit(false.B)             // FSM: idle/busy
  val div0Reg    = RegInit(false.B)             // flag: divisão por zero para o request atual

  io.busy := busyReg

  // Pronto para aceitar nova divisão somente quando não ocupado
  io.in.ready := !busyReg

  // ---- Caminho combinacional de um ciclo (até `unroll` sub-passos) ----
  // quantas iterações deste ciclo estarão ativas?
  val activeIters = Wire(UInt(log2Ceil(unroll + 1).W))
  activeIters := Mux(stepCnt === 1.U, lastIters.U, unroll.U)

  // variáveis temporárias (scala vars, conectando nós chisel)
  var remNext  = remReg
  var dvdNext  = dvdReg
  var quotNext = quotReg

  // executa até `unroll` passos do algoritmo "restoring":
  // para j = 0 .. activeIters-1:
  //   rem  = (rem << 1) | next_bit_from_dividend
  //   if (rem >= divisor) { rem -= divisor; quot = (quot<<1)|1 } else { quot = (quot<<1) }
  for (j <- 0 until unroll) {
    // só executa este sub-passo se ainda houver iteração ativa
    val doStep = (j.U < activeIters)

    val nextBit = Mux(doStep, dvdNext(width - 1), 0.U(1.W))
    val remCand = Cat(remNext(width - 1, 0), nextBit) // (rem << 1) | nextBit
    val ge      = remCand >= divReg
    val remUpd  = Mux(doStep && ge, (remCand - divReg), Mux(doStep, remCand, remNext))
    val quotUpd = Mux(doStep, Cat(quotNext(width - 2, 0), ge.asUInt), quotNext) // shift-in do bit

    // shift do dividend (consumindo MSB-first) somente se ativo
    val dvdUpd  = Mux(doStep, dvdNext << 1, dvdNext)

    // avança os "regs temporários" do loop
    remNext  = remUpd
    quotNext = quotUpd
    dvdNext  = dvdUpd
  }

  val lastCycle = busyReg && (stepCnt === 1.U)
  val doneNow   = lastCycle || (busyReg && div0Reg) // div0 sai em 1 ciclo

  // Saída
  io.out.valid    := doneNow
  io.out.bits.quot := Mux(div0Reg, Fill(width, 1.U(1.W)), quotNext)
  io.out.bits.rem  := Mux(div0Reg, dvdReg,                 remNext(width - 1, 0))

  // --------- FSM ----------
  when (!busyReg) {
    // IDLE: aceita novo request
    when (io.in.fire) {
      val isDiv0 = io.in.bits.divisor === 0.U
      div0Reg := isDiv0

      // inicialização (mesmo para div0; os regs servem para expor resultado)
      remReg  := 0.U
      dvdReg  := io.in.bits.dividend
      divReg  := io.in.bits.divisor
      quotReg := 0.U
      stepCnt := steps.U
      busyReg := true.B
    }
  } .otherwise {
    // BUSY
    when (div0Reg) {
      // divisão por zero: resultado pronto imediatamente
      when (io.out.fire) {
        busyReg := false.B
        div0Reg := false.B
      }
    } .otherwise {
      // caso normal: ainda existem ciclos?
      when (!lastCycle) {
        // ciclo intermediário: efetiva os próximos valores e decrementa o contador
        remReg  := remNext
        dvdReg  := dvdNext
        quotReg := quotNext
        stepCnt := stepCnt - 1.U
      } .otherwise {
        // ciclo final: expõe resultado; libera quando consumidor aceitar
        when (io.out.fire) {
          busyReg := false.B
        } .otherwise {
          // travado esperando o consumidor -> fixe os regs no valor final
          remReg  := remNext
          dvdReg  := dvdNext
          quotReg := quotNext
        }
      }
    }
  }
}

class MultiplierReq(dataBits: Int) extends Bundle {
  val in1      = UInt(dataBits.W)
  val in2      = UInt(dataBits.W)
  val isSigned = Bool()
}

class MultiplierResp(dataBits: Int) extends Bundle {
  val data      = UInt(dataBits.W)
  val full_data = UInt((2*dataBits).W)
}

class MultiplierIO(val dataBits: Int) extends Bundle {
  val req  = Flipped(Decoupled(new MultiplierReq(dataBits)))
  val resp = Decoupled(new MultiplierResp(dataBits))
}

class Multiplier(val width: Int, val unroll: Int) extends Module {
  require(width == 32 || width == 64, s"width must be 32 or 64, got $width")
  require(unroll >= 1 && unroll <= width, s"unroll must be in [1,$width], got $unroll")

  val io = IO(new MultiplierIO(dataBits = width))

  private def ceilDiv(x: Int, y: Int) = (x + y - 1) / y
  private val steps    = ceilDiv(width, unroll)
  private val stepBits = log2Ceil(steps + 1).max(1)

  val acc     = RegInit(0.U((2*width).W))
  val mcand   = RegInit(0.U((2*width).W))
  val mplier  = RegInit(0.U(width.W))
  val stepCnt = RegInit(0.U(stepBits.W))
  val busy    = RegInit(false.B)
  val resNeg  = RegInit(false.B)

  io.req.ready  := !busy
  io.resp.valid := false.B
  io.resp.bits  := 0.U.asTypeOf(io.resp.bits)

  val in1      = io.req.bits.in1
  val in2      = io.req.bits.in2
  val isSigned = io.req.bits.isSigned

  val aNeg = isSigned && in1(width-1)
  val bNeg = isSigned && in2(width-1)

  def absTC(x: UInt): UInt = Mux(x(width-1), (~x).asUInt + 1.U, x)

  val aMag = Mux(isSigned && aNeg, absTC(in1), in1)
  val bMag = Mux(isSigned && bNeg, absTC(in2), in2)

  val chunk = mplier(unroll-1, 0)
  val partialTerms = (0 until unroll).map { j =>
    val term = mcand << j
    Mux(chunk(j), term, 0.U((2*width).W))
  }
  val partialSum = partialTerms.reduceOption(_ +& _).getOrElse(0.U((2*width).W))

  val willFinishThisCycle = busy && (stepCnt === 1.U)
  val productUnsigned     = acc +& partialSum
  val loU                 = productUnsigned(width-1, 0)
  val hiU                 = productUnsigned((2*width)-1, width)
  val fullU               = Cat(hiU, loU)

  val fullS = Mux(resNeg, (~fullU).asUInt + 1.U, fullU)
  val loS   = fullS(width-1, 0)

  when (!busy) {
    when (io.req.fire) {
      acc     := 0.U
      mcand   := aMag.pad(2*width)
      mplier  := bMag
      stepCnt := steps.U
      busy    := true.B
      resNeg  := isSigned && (aNeg ^ bNeg)
    }
  } .otherwise {
    when (!willFinishThisCycle) {
      acc     := acc +& partialSum
      mcand   := mcand << unroll
      mplier  := mplier >> unroll
      stepCnt := stepCnt - 1.U
    } .otherwise {
      io.resp.valid          := true.B
      io.resp.bits.full_data := fullS
      io.resp.bits.data      := loS
      
      when (io.resp.ready) {
        busy := false.B
      }
    }
  }
}

class Divider(val width: Int, val unroll: Int) extends Module {
  require(width == 32 || width == 64, s"width must be 32 or 64, got $width")
  require(unroll >= 1 && unroll <= width, s"unroll must be in [1,$width], got $unroll")

  val io = IO(new MultiplierIO(dataBits = width))

  private def ceilDiv(x: Int, y: Int) = (x + y - 1) / y
  private val steps     = ceilDiv(width, unroll)
  private val stepBits  = log2Ceil(steps + 1).max(1)
  private val minIntU   = (BigInt(1) << (width - 1)).U(width.W)
  private val negOneU   = ((BigInt(1) << width) - 1).U(width.W)
  private val zeroU     = 0.U(width.W)

  val remReg   = RegInit(0.U((width + 1).W))
  val dvdReg   = RegInit(0.U(width.W))
  val divReg   = RegInit(0.U(width.W))
  val quotReg  = RegInit(0.U(width.W))
  val stepCnt  = RegInit(0.U(stepBits.W))
  val busy     = RegInit(false.B)

  val r_dividendRaw = RegInit(0.U(width.W))
  val r_isSigned = RegInit(false.B)
  val r_div0     = RegInit(false.B)
  val r_overflow = RegInit(false.B)

  io.req.ready  := !busy
  io.resp.valid := false.B
  io.resp.bits  := 0.U.asTypeOf(io.resp.bits)

  def absTC(x: UInt): UInt = Mux(x(width-1), (~x).asUInt + 1.U, x)

  val activeIters = Wire(UInt(log2Ceil(unroll + 1).W))
  val lastIters   = (if (width % unroll == 0) unroll else width % unroll).U

  val lastCycle = busy && (stepCnt === 1.U)
  activeIters := Mux(lastCycle, lastIters, unroll.U)

  var remNext  = remReg
  var dvdNext  = dvdReg
  var quotNext = quotReg

  // rem  = (rem << 1) | nextBit
  // if (rem >= div) { rem -= div; quot = (quot<<1)|1 } else { quot = (quot<<1) }
  for (j <- 0 until unroll) {
    val doStep  = (j.U < activeIters)
    val nextBit = Mux(doStep, dvdNext(width - 1), 0.U(1.W))
    val remCand = Cat(remNext(width - 1, 0), nextBit)  // (rem<<1)|bit
    val ge      = remCand >= divReg
    val remUpd  = Mux(doStep && ge, remCand - divReg, Mux(doStep, remCand, remNext))
    val quotUpd = Mux(doStep, Cat(quotNext(width - 2, 0), ge.asUInt), quotNext)
    val dvdUpd  = Mux(doStep, dvdNext << 1, dvdNext)

    remNext  = remUpd
    quotNext = quotUpd
    dvdNext  = dvdUpd
  }

  val quotU = quotNext
  val remU  = remNext(width - 1, 0)

  val quotOut = Wire(UInt(width.W))
  val remOut  = Wire(UInt(width.W))
  quotOut := quotU
  remOut  := remU

  when (r_div0) {
    quotOut := negOneU
    remOut  := dvdReg
  } .elsewhen (r_overflow) {
    quotOut := minIntU
    remOut  := zeroU
  } .elsewhen (r_isSigned) {
    val aNeg = io.req.bits.in1(width-1)
    val bNeg = io.req.bits.in2(width-1)

    val qNeg   = RegNext(false.B, init=false.B)
    
    def negW(x: UInt): UInt = (~x).asUInt + 1.U

    val qSigned = Wire(UInt(width.W))
    val rSigned = Wire(UInt(width.W))

    qSigned := Mux(RegNext(false.B, init=false.B), quotU, quotU)
    rSigned := remU
  }

  val r_aNeg = RegInit(false.B)
  val r_bNeg = RegInit(false.B)

  val quotFinal = Wire(UInt(width.W))
  val remFinal  = Wire(UInt(width.W))
  def negW(x: UInt): UInt = (~x).asUInt + 1.U

  when (r_div0) {
    quotFinal := negOneU
    remFinal  := r_dividendRaw   // <-- usar o dividendo ORIGINAL, não dvdReg
  } .elsewhen (r_overflow) {
    quotFinal := minIntU
    remFinal  := zeroU
  } .elsewhen (r_isSigned) {
    val qNeg = r_aNeg ^ r_bNeg
    val rNeg = r_aNeg
    quotFinal := Mux(qNeg, negW(quotU), quotU)
    remFinal  := Mux(rNeg, negW(remU), remU)
  } .otherwise {
    quotFinal := quotU
    remFinal  := remU
  }

  // ---------- FSM ----------
  when (!busy) {
    when (io.req.fire) {
      val in1      = io.req.bits.in1
      val in2      = io.req.bits.in2
      val isSigned = io.req.bits.isSigned

      r_dividendRaw := in1  

      val aNeg     = isSigned && in1(width-1)
      val bNeg     = isSigned && in2(width-1)
      val aMag     = Mux(isSigned && aNeg, (~in1).asUInt + 1.U, in1)
      val bMag     = Mux(isSigned && bNeg, (~in2).asUInt + 1.U, in2)

      val div0     = in2 === 0.U
      val overflow = isSigned && (in1 === minIntU) && (in2 === negOneU)

      remReg   := 0.U
      dvdReg   := aMag
      divReg   := bMag
      quotReg  := 0.U
      stepCnt  := steps.U
      busy     := true.B

      r_isSigned := isSigned
      r_div0     := div0
      r_overflow := overflow
      r_aNeg     := aNeg
      r_bNeg     := bNeg
    }
  } .otherwise {
    when (!lastCycle) {
      remReg  := remNext
      dvdReg  := dvdNext
      quotReg := quotNext
      stepCnt := stepCnt - 1.U
    } .otherwise {
      io.resp.valid          := true.B
      io.resp.bits.data      := quotFinal
      io.resp.bits.full_data := Cat(remFinal, quotFinal)

      when (io.resp.ready) {
        busy := false.B
      } .otherwise {
        remReg  := remNext
        dvdReg  := dvdNext
        quotReg := quotNext
      }
    }
  }
}

