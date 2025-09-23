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


