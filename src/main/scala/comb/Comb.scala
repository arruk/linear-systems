package comb

import chisel3._
import chisel3.util._
import chisel3._

class CombParamSInt(val nBits: Int = 32) extends Module {
  val io = IO(new Bundle {
    val a            = Input(SInt(nBits.W))
    val b            = Input(SInt(nBits.W))
    val OutSum       = Output(SInt(nBits.W))
    val OutDif       = Output(SInt(nBits.W))
    val addOverflow  = Output(Bool())
    val subOverflow  = Output(Bool())
  })

  val aExt = io.a.pad(nBits + 1) // SInt(nBits+1)
  val bExt = io.b.pad(nBits + 1) // SInt(nBits+1)
  
  val sumWide = Wire(SInt((nBits + 1).W))
  sumWide := aExt +& bExt
  io.OutSum      := sumWide(nBits - 1, 0).asSInt
  io.addOverflow := (sumWide(nBits) ^ sumWide(nBits - 1)).asBool

  val diffWide = Wire(SInt((nBits + 1).W))
  diffWide := aExt -& bExt 
  io.OutDif      := diffWide(nBits - 1, 0).asSInt
  io.subOverflow := (diffWide(nBits) ^ diffWide(nBits - 1)).asBool
}

// Classe Comb parametrizável
class CombParam(val nBits: Int = 32) extends Module {
  val io = IO(new Bundle {
    val a      = Input(UInt(nBits.W))
    val b      = Input(UInt(nBits.W))
    val OutSum = Output(UInt(nBits.W))
    val OutDif = Output(UInt(nBits.W))
  })

  val adder = Module(new Adder(nBits))
  adder.io.a   := io.a
  adder.io.b   := io.b
  adder.io.cin := false.B

  io.OutSum := adder.io.sum
  io.OutDif := io.a - io.b
}

class Comb extends Module {
  val io = IO(new Bundle{
    val a = Input(UInt(16.W))
    val b = Input(UInt(16.W))
    val OutSum = Output(UInt(16.W))
    val OutDif = Output(UInt(16.W))
  })
  val adder = Module(new Adder16())
  adder.io.a   := io.a
  adder.io.b   := io.b
  adder.io.cin := false.B

  io.OutSum := adder.io.sum

  io.OutDif := io.a - io.b

}

// Somador de 1 bit (Full Adder)
class FullAdder extends Module {
  val io = IO(new Bundle {
    val a    = Input(Bool())
    val b    = Input(Bool())
    val cin  = Input(Bool())
    val sum  = Output(Bool())
    val cout = Output(Bool())
  })

  io.sum  := io.a ^ io.b ^ io.cin
  io.cout := (io.a & io.b) | (io.a & io.cin) | (io.b & io.cin)
}

// Somador de 16 bits
class Adder16 extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(16.W))
    val b   = Input(UInt(16.W))
    val cin = Input(Bool())
    val sum = Output(UInt(16.W))
    val cout= Output(Bool())
  })

  // Vetores de wires para propagação
  val sums  = Wire(Vec(16, Bool()))
  val carry = Wire(Vec(17, Bool()))
  carry(0) := io.cin

  // Instancia 16 Full Adders
  for (i <- 0 until 16) {
    val fa = Module(new FullAdder())
    fa.io.a   := io.a(i)
    fa.io.b   := io.b(i)
    fa.io.cin := carry(i)
    sums(i)   := fa.io.sum
    carry(i+1):= fa.io.cout
  }

  io.sum  := sums.asUInt
  io.cout := carry(16)
}

// Somador genérico parametrizável
class Adder(val nBits: Int) extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(nBits.W))
    val b   = Input(UInt(nBits.W))
    val cin = Input(Bool())
    val sum = Output(UInt(nBits.W))
    val cout= Output(Bool())
  })

  val sums  = Wire(Vec(nBits, Bool()))
  val carry = Wire(Vec(nBits + 1, Bool()))
  carry(0) := io.cin

  for (i <- 0 until nBits) {
    val fa = Module(new FullAdder())
    fa.io.a   := io.a(i)
    fa.io.b   := io.b(i)
    fa.io.cin := carry(i)
    sums(i)   := fa.io.sum
    carry(i+1):= fa.io.cout
  }

  io.sum  := sums.asUInt
  io.cout := carry(nBits)
}
