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

  "ShiftAddMul16 faz multiplicacao com shift-adders" in {
    test(new ShiftAddMul16) { dut =>
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

  "ShiftAddIterMul multiplica corretamente com vários unrolls" in {
    val nBits = 32
    val mask16 = (BigInt(1) << nBits) - 1

    def refLowerUpper(a: BigInt, b: BigInt): (BigInt, BigInt) = {
      val prod  = a * b
      val lower = prod & mask16
      val upper = (prod >> nBits) & mask16
      (lower, upper)
    }

    for (unrollVal <- Seq(1, 2, 4, 8, 16)) {
      test(new ShiftAddIterMul(width = nBits, unroll = unrollVal)) { dut =>

        def runOne(a: BigInt, b: BigInt): Unit = {
          while (!dut.io.in.ready.peek().litToBoolean) { dut.clock.step() }
          dut.io.in.valid.poke(true.B)
          dut.io.in.bits.a.poke(a.U(nBits.W))
          dut.io.in.bits.b.poke(b.U(nBits.W))
          dut.clock.step()
          dut.io.in.valid.poke(false.B)
          while (!dut.io.out.valid.peek().litToBoolean) { dut.clock.step() }

          val (expLo, expHi) = refLowerUpper(a, b)
          dut.io.out.bits.lower.expect(expLo.U(nBits.W))
          dut.io.out.bits.upper.expect(expHi.U(nBits.W))
          
          dut.io.out.ready.poke(true.B)
          dut.clock.step()
          dut.io.out.ready.poke(false.B)
        }

        dut.io.in.valid.poke(false.B)
        dut.io.out.ready.poke(false.B)
        dut.clock.step()

        runRandomVectors(nBits, 50, (a, b) => runOne(a, b))
      }
    }
  }

  "Multiplier (IO estilo Rocket) multiplica corretamente com vários unrolls" in {
    val nBits  = 32
    val mask16 = (BigInt(1) << nBits) - 1

    def refParts(a: BigInt, b: BigInt): (BigInt, BigInt, BigInt) = {
      val prod  = a * b
      val lo    = prod & mask16
      val hi    = (prod >> nBits) & mask16
      val full  = (hi << nBits) | lo
      (lo, hi, full)
    }

    for (unrollVal <- Seq(1, 2, 4, 8, 16)) {
      test(new Multiplier(width = nBits, unroll = unrollVal)) { dut =>

        def runOne(a: BigInt, b: BigInt): Unit = {
          while (!dut.io.req.ready.peek().litToBoolean) { dut.clock.step() }
          dut.io.req.valid.poke(true.B)
          dut.io.req.bits.isSigned.poke(false.B)
          dut.io.req.bits.in1.poke(a.U(nBits.W))
          dut.io.req.bits.in2.poke(b.U(nBits.W))
          dut.clock.step()
          dut.io.req.valid.poke(false.B)

          while (!dut.io.resp.valid.peek().litToBoolean) { dut.clock.step() }

          val (expLo, expHi, expFull) = refParts(a, b)
          dut.io.resp.bits.data.expect(expLo.U(nBits.W))
          dut.io.resp.bits.full_data.expect(expFull.U((2 * nBits).W))

          dut.io.resp.ready.poke(true.B)
          dut.clock.step()
          dut.io.resp.ready.poke(false.B)
        }

        dut.io.req.valid.poke(false.B)
        dut.io.resp.ready.poke(false.B)
        dut.clock.step()

        runRandomVectors(nBits, 100, (a, b) => runOne(a, b))

        Seq(
          (BigInt(0), BigInt(0)),
          (BigInt(0xffff), BigInt(0xffff)),
          (BigInt(0xffff), BigInt(1)),
          (BigInt(0x1234), BigInt(0x5678)),
          (BigInt(0x8000), BigInt(0x8000)),
          (BigInt(0xffff), BigInt(0x0002))
        ).foreach { case (a, b) => runOne(a, b) }
      }
    }
  }

  "Multiplier (signed) multiplica corretamente com vários unrolls" in {
    val nBits   = 32 
    val modN    = BigInt(1) << nBits
    val mod2N   = BigInt(1) << (2 * nBits)
    val minS    = -(BigInt(1) << (nBits - 1))            // -2^(n-1)
    val maxS    =  (BigInt(1) << (nBits - 1)) - 1        //  2^(n-1)-1

    def toUnsignedN(x: BigInt): BigInt = {
      val r = x & (modN - 1)
      if (r.signum < 0) r + modN else r
    }

    def refSigned(aS: BigInt, bS: BigInt): (BigInt, BigInt) = {
      val prod      = aS * bS
      val fullMod   = (prod % mod2N + mod2N) % mod2N
      val lo        = fullMod & (modN - 1)
      (lo, fullMod)
    }

    val rnd = new scala.util.Random(0xC0FFEE)
    def randSigned(): BigInt = {
      val span = maxS - minS + 1
      minS + BigInt(nBits, rnd) % span
    }

    for (unrollVal <- Seq(1, 2, 4, 8, 16)) {
      test(new Multiplier(width = nBits, unroll = unrollVal)) { dut =>

        def runOne(aS: BigInt, bS: BigInt): Unit = {
          val (expLo, expFull) = refSigned(aS, bS)

          val aU = toUnsignedN(aS)
          val bU = toUnsignedN(bS)

          while (!dut.io.req.ready.peek().litToBoolean) { dut.clock.step() }

          dut.io.req.valid.poke(true.B)
          dut.io.req.bits.in1.poke(aU.U(nBits.W))
          dut.io.req.bits.in2.poke(bU.U(nBits.W))
          dut.io.req.bits.isSigned.poke(true.B)
          dut.clock.step()
          dut.io.req.valid.poke(false.B)

          while (!dut.io.resp.valid.peek().litToBoolean) { dut.clock.step() }

          dut.io.resp.bits.data.expect(expLo.U(nBits.W))
          dut.io.resp.bits.full_data.expect(expFull.U((2 * nBits).W))

          dut.io.resp.ready.poke(true.B)
          dut.clock.step()
          dut.io.resp.ready.poke(false.B)
        }

        dut.io.req.valid.poke(false.B)
        dut.io.resp.ready.poke(false.B)
        dut.clock.step()

        (0 until 100).foreach { _ =>
          runOne(randSigned(), randSigned())
        }

        val minVal = minS             // -2^(n-1)
        val maxVal = maxS             //  2^(n-1)-1
        // casos de borda importantes (tudo em BigInt!)
        Seq(
          (BigInt(0), BigInt(0)),
          (BigInt(-1), BigInt(-1)),
          (BigInt(-1), BigInt(1)),
          (BigInt(1), BigInt(-1)),
          (maxVal, maxVal),
          (minVal, BigInt(1)),
          (BigInt(1), minVal),
          (minVal, BigInt(-1)),
          (BigInt(-2), BigInt(2)),
          (BigInt(0x1234), BigInt(-0x2B))   // exemplo misto (positivo × negativo)
        ).foreach { case (aS, bS) => runOne(aS, bS) }
      }
    }
  }
  // ---------------------------- DIVISOR (UNSIGNED) ----------------------------
  "Divider (unsigned) divide corretamente com vários unrolls" in {
    val nBits  = 32
    val modN   = BigInt(1) << nBits
    val maskN  = modN - 1

    def toUnsignedN(x: BigInt): BigInt = x & maskN

    def refUnsigned(dividendU: BigInt, divisorU: BigInt): (BigInt, BigInt) = {
      // Ambos já tratados como não-assinados (0..2^n-1)
      if (divisorU == 0) {
        // RISC-V: DIV/REM por zero => q = -1 (todos 1s), r = dividend
        (maskN, dividendU)
      } else {
        val q = dividendU / divisorU
        val r = dividendU % divisorU
        (q & maskN, r & maskN)
      }
    }

    for (unrollVal <- Seq(1, 2, 4, 8, 16)) {
      test(new Divider(width = nBits, unroll = unrollVal)) { dut =>

        def runOne(aU: BigInt, bU: BigInt): Unit = {
          val (expQ, expR) = refUnsigned(aU & maskN, bU & maskN)
          val expFull      = ((expR << nBits) | expQ) & ((BigInt(1) << (2*nBits)) - 1)

          // esperar pronto
          while (!dut.io.req.ready.peek().litToBoolean) { dut.clock.step() }

          // enviar requisição (unsigned)
          dut.io.req.valid.poke(true.B)
          dut.io.req.bits.in1.poke((aU & maskN).U(nBits.W))   // dividend
          dut.io.req.bits.in2.poke((bU & maskN).U(nBits.W))   // divisor
          dut.io.req.bits.isSigned.poke(false.B)
          dut.clock.step()
          dut.io.req.valid.poke(false.B)

          // aguardar resposta
          while (!dut.io.resp.valid.peek().litToBoolean) { dut.clock.step() }

          // verificar: data = quotient ; full_data = {remainder, quotient}
          dut.io.resp.bits.data.expect(expQ.U(nBits.W))
          dut.io.resp.bits.full_data.expect(expFull.U((2*nBits).W))

          // consumir
          dut.io.resp.ready.poke(true.B)
          dut.clock.step()
          dut.io.resp.ready.poke(false.B)
        }

        // init
        dut.io.req.valid.poke(false.B)
        dut.io.resp.ready.poke(false.B)
        dut.clock.step()

        // aleatórios (re-usa seu helper)
        runRandomVectors(nBits, 100, (a, b) => runOne(a, b))

        // casos de borda
        Seq(
          (BigInt(0), BigInt(0)),                // div0
          (BigInt(12345), BigInt(0)),            // div0
          (BigInt(0), BigInt(1)),
          (maskN, BigInt(1)),
          (maskN, maskN),
          (BigInt(1), maskN),
          (BigInt(37), BigInt(5)),
          (BigInt(65535), BigInt(2))             // max/2
        ).foreach { case (a, b) => runOne(a, b) }
      }
    }
  }

  // ----------------------------- DIVISOR (SIGNED) -----------------------------
  "Divider (signed) divide corretamente com vários unrolls" in {
    val nBits = 32
    val modN  = BigInt(1) << nBits
    val mod2N = BigInt(1) << (2*nBits)
    val minS  = -(BigInt(1) << (nBits - 1))      // -2^(n-1)
    val maxS  =  (BigInt(1) << (nBits - 1)) - 1  //  2^(n-1)-1

    def toUnsignedN(x: BigInt): BigInt = (x & (modN - 1) + modN) % modN

    // RISC-V rules: div0 => q = -1, r = dividend ; overflow (INT_MIN / -1) => q = INT_MIN, r = 0
    def refSigned(aS: BigInt, bS: BigInt): (BigInt, BigInt) = {
      // clamp para o intervalo representável (evita lixo em testes construídos)
      val A = aS.max(minS).min(maxS)
      val B = bS.max(minS).min(maxS)

      if (B == 0) {
        val q = BigInt(-1)                       // all ones
        val r = A
        (toUnsignedN(q), toUnsignedN(r))
      } else if (A == minS && B == -1) {
        val q = minS
        val r = BigInt(0)
        (toUnsignedN(q), toUnsignedN(r))
      } else {
        val q = A / B                            // trunc toward zero (BigInt faz isso)
        val r = A - q*B
        (toUnsignedN(q), toUnsignedN(r))
      }
    }

    val rnd = new scala.util.Random(0xC0FFEE)
    def randSigned(): BigInt = {
      val span = maxS - minS + 1
      minS + (BigInt(nBits, rnd) % span)
    }

    for (unrollVal <- Seq(1, 2, 4, 8, 16)) {
      test(new Divider(width = nBits, unroll = unrollVal)) { dut =>

        def runOne(aS: BigInt, bS: BigInt): Unit = {
          val (expQ, expR) = refSigned(aS, bS)
          val expFull      = ((expR << nBits) | expQ) & ((BigInt(1) << (2*nBits)) - 1)

          val aU = toUnsignedN(aS)
          val bU = toUnsignedN(bS)

          // esperar pronto
          while (!dut.io.req.ready.peek().litToBoolean) { dut.clock.step() }

          // enviar requisição signed
          dut.io.req.valid.poke(true.B)
          dut.io.req.bits.in1.poke(aU.U(nBits.W))    // dividend (two's complement)
          dut.io.req.bits.in2.poke(bU.U(nBits.W))    // divisor
          dut.io.req.bits.isSigned.poke(true.B)
          dut.clock.step()
          dut.io.req.valid.poke(false.B)

          // aguardar resposta
          while (!dut.io.resp.valid.peek().litToBoolean) { dut.clock.step() }

          // checar
          dut.io.resp.bits.data.expect(expQ.U(nBits.W))
          dut.io.resp.bits.full_data.expect(expFull.U((2*nBits).W))

          // consumir
          dut.io.resp.ready.poke(true.B)
          dut.clock.step()
          dut.io.resp.ready.poke(false.B)
        }

        // init
        dut.io.req.valid.poke(false.B)
        dut.io.resp.ready.poke(false.B)
        dut.clock.step()

        // aleatórios com sinal
        (0 until 100).foreach { _ => runOne(randSigned(), randSigned()) }

        // casos de borda relevantes
        val minVal = minS
        val maxVal = maxS
        Seq(
          (BigInt(0), BigInt(0)),          // div0
          (maxVal, BigInt(0)),             // div0
          (minVal, BigInt(-1)),            // overflow: INT_MIN / -1
          (BigInt(-1), BigInt(1)),
          (BigInt(1), BigInt(-1)),
          (BigInt(-37), BigInt(5)),
          (BigInt(37), BigInt(-5)),
          (maxVal, BigInt(1)),
          (minVal, BigInt(1)),
          (BigInt(-2), BigInt(2))
        ).foreach { case (a, b) => runOne(a, b) }
      }
    }
  }

}

