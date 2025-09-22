import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class Welcome extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(8.W))
    val b   = Input(UInt(8.W))
    val sum = Output(UInt(8.W))
  })
  io.sum := io.a + io.b
}

class WelcomeSpec extends AnyFlatSpec with ChiselScalatestTester {
  "Welcome" should "add two numbers correctly" in {
    test(new Welcome) { c =>
      c.io.a.poke(3.U)
      c.io.b.poke(4.U)
      c.clock.step()
      c.io.sum.expect(7.U)

      println("Welcome test passed! Chisel is working")
    }
  }
}

