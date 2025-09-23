package seq

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random

class SeqSpec extends AnyFreeSpec with ChiselScalatestTester with Matchers {

  private def runRandomVectors(
      nBits: Int,
      trials: Int,
      body: (BigInt, BigInt) => Unit
  ): Unit = {
    val rnd  = new Random(0xC0FFEE)
    def randN(): BigInt = BigInt(nBits, rnd)
    for (_ <- 0 until trials) body(randN(), randN())
  }

  "Mul16 multiplica dois numeros e retorna lower e upper" in {
    test(new Mul16) { dut =>
      val nBits  = 16
      val mask16 = (BigInt(1) << nBits) - 1

      def refLowerUpper(a: BigInt, b: BigInt): (BigInt, BigInt) = {
        val prod  = a * b                           // até 32 bits
        val lower = prod & mask16                   // bits [15:0]
        val upper = (prod >> nBits) & mask16        // bits [31:16]
        (lower, upper)
      }

      runRandomVectors(nBits, 200, (a, b) => {
        val (expLo, expHi) = refLowerUpper(a, b)
        dut.io.a.poke(a.U(nBits.W))
        dut.io.b.poke(b.U(nBits.W))
        dut.clock.step()
        dut.io.lower.expect(expLo.U(nBits.W))
        dut.io.upper.expect(expHi.U(nBits.W))
      })
    }
  }

  /*
  "CombParam em (8,16,32,64)" in {
    Seq(8, 16, 32, 64).foreach { nBits =>
      test(new CombParam(nBits)) { dut =>
        val mod = BigInt(1) << nBits
        def modn(x: BigInt) = { val r = x % mod; if (r.signum < 0) r + mod else r }

        runRandomVectors(nBits, 200, (a, b) => {
          dut.io.a.poke(a.U(nBits.W))
          dut.io.b.poke(b.U(nBits.W))
          dut.io.OutSum.expect(modn(a + b).U(nBits.W))
          dut.io.OutDif.expect(modn(a - b).U(nBits.W))
        })
      }
    }
  }

  "CombParamSInt em (8,16,32,64)" in {
    Seq(8, 16, 32, 64).foreach { nBits =>
      test(new CombParamSInt(nBits)) { dut =>
        val (min, max) = {
          val min = -(BigInt(1) << (nBits - 1))
          val max =  (BigInt(1) << (nBits - 1)) - 1
          (min, max)
        }
        def wrapSInt(x: BigInt): BigInt = {
          val mod = BigInt(1) << nBits
          val y   = ((x % mod) + mod) % mod
          val signBit = BigInt(1) << (nBits - 1)
          if (y >= signBit) y - mod else y
        }
        def addOv(a: BigInt, b: BigInt) = {
          val r = a + b; r < min || r > max
        }
        def subOv(a: BigInt, b: BigInt) = {
          val r = a - b; r < min || r > max
        }

        runRandomVectors(nBits, 200, (aU, bU) => {
          // gera valores no intervalo signed
          val a = wrapSInt(aU)
          val b = wrapSInt(bU)

          dut.io.a.poke(a.S(nBits.W))
          dut.io.b.poke(b.S(nBits.W))

          dut.io.OutSum.expect(wrapSInt(a + b).S(nBits.W))
          dut.io.OutDif.expect(wrapSInt(a - b).S(nBits.W))
          dut.io.addOverflow.expect(addOv(a, b).B)
          dut.io.subOverflow.expect(subOv(a, b).B)
        })
      }
    }
  }
  */
}

