package verification

import asm.Assembler
import org.junit.jupiter.api.Assertions.assertEquals
import scc.SpamCC
import terminal.VerilogUARTTerminal

import java.io.{File, PrintWriter}
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable.ListBuffer

object Verification {

  def compile(linesRaw: String,
              verbose: Boolean = false,
              quiet: Boolean = true,
              dataIn: List[String] = List("t10000000"),
              outputCheck: List[String] => Unit = _ => {},
              checkHalt: Option[HaltCode] = Some(HaltCode(65535, 255)),
              timeout: Int = 60
             ): List[String] = {

    val scc = new SpamCC

    val lines = "program {\n" + linesRaw + "\n}"
    val actual: List[String] = scc.compile(lines)

    val str = actual.mkString("\n")
    println("ASSEMBLING:\n")
    var pc = 0

    val IsEqu = "^\\s*[a-zA-Z0-9_]+:\\s*EQU.*$".r
    val IsLabel = "^\\s*[a-zA-Z0-9_]+:\\s*$".r
    val IsComment = "^\\s*;.*$".r
    val IsBytes = """^\s*[a-zA-Z0-9_]+:\s*BYTES\s.*$""".r
    val IsString = """^\s*[a-zA-Z0-9_]+:\s*STR\s.*$""".r

    actual.foreach { l =>
      if (IsComment.matches(l)) {
        println(s"${"".formatted("%5s")}  $l")
      }
      else if (IsEqu.matches(l)) {
        println(s"${"".formatted("%5s")}  $l")
      }
      else if (IsLabel.matches(l)) {
        println(s"${"".formatted("%5s")}  $l")
      } else if (IsBytes.matches(l)) {
        val withoutBrackets = l.replaceAll(""".*\[""", "").replaceAll("""\].*$""", "")

        val bytes = withoutBrackets.split(",").length

        println(s"${pc.formatted("%5d")}  $l")
        pc += bytes

      } else if (IsString.matches(l)) {
        val withoutQuotes = l.replaceAll("""^[^"]+"""", "").replaceAll(""""[^"]*$""", "")
        val lstr = org.apache.commons.text.StringEscapeUtils.unescapeJava(withoutQuotes)

        val bytes = lstr.getBytes("UTF-8").toSeq

        println(s"${pc.formatted("%5d")}  $l")
        pc += bytes.length

      } else {
        println(s"${pc.formatted("%5d")}  $l")
        pc += 1
      }
    }

    val asm = new Assembler
    val roms = asm.assemble(str, quiet = quiet)

    // ditch comments
    val filtered = actual.filter { l =>
      (!quiet) || !l.matches("^\\s*;.*")
    }

    val tmpFileRom = new File("build", "spammcc-test.rom")
    val tmpUartControl = new File(VerilogUARTTerminal.uartControl)

    println("WRITING ROM TO :\n" + tmpFileRom)
    writeFile(roms, tmpFileRom)

    writeUartControlFile(tmpUartControl, dataIn)
    exec(tmpFileRom, tmpUartControl, verbose, outputCheck, checkHalt, timeout)

    print("ASM RAN OK\n" + filtered.map(_.stripLeading()).mkString("\n"))
    filtered
  }

  def writeFile(roms: List[List[String]], tmpFileRom: File): Unit = {
    if (tmpFileRom.getParentFile.exists()) {
      if (!tmpFileRom.getParentFile.isDirectory) {
        sys.error("expected a directory : " + tmpFileRom.getParentFile.getAbsolutePath)
      }
    } else {
      tmpFileRom.getParentFile.mkdirs()
    }

    val pw = new PrintWriter(tmpFileRom)

    roms.foreach { line =>
      line.foreach { rom =>
        pw.write(rom)
      }
      pw.write("\n")
    }

    pw.close()
  }

  def writeUartControlFile(tmpFileRom: File, data: List[String]): Unit = {
    val pw = new PrintWriter(tmpFileRom)

    data.foreach {
      pw.println
    }
    pw.close()
  }

  def exec(romsPath: File, tmpUartControl: File, verbose: Boolean,
           outputCheck: List[String] => Unit,
           checkHalt: Option[HaltCode],
           timeout: Int): Unit = {
    import scala.language.postfixOps
    import scala.sys.process._
    val romFileUnix = romsPath.getPath.replaceAll("\\\\", "/")
    val controlFileUnix = tmpUartControl.getPath.replaceAll("\\\\", "/")

    println("RUNNING :\n" + romFileUnix)

    //    val pb: ProcessBuilder = Process(Seq("bash", "-c", s"""../verilog/spamcc_sim.sh ../verilog/cpu/demo_assembler_roms.v +rom=`pwd`/$romFileUnix  +uart_control_file=`pwd`/$controlFileUnix"""))
    val pb: ProcessBuilder = Process(Seq("bash", "-c", s"""../../verilog/spamcc_sim.sh '$timeout' ../../verilog/cpu/demo_assembler_roms.v `pwd`/$romFileUnix"""))

    val halted = new AtomicReference[HaltCode]()

    val lines = ListBuffer.empty[String]

    def addLine(line: String): Unit = {
      lines.synchronized {
        lines.append(line)
      }
    }

    val logger = ProcessLogger.apply(
      fout = output => {
        addLine("OUT:" + output)
        if (output.contains("--- HALTED ")) {
          val haltedRegex = "--* HALTED <(\\d+)\\s.*> <(\\d+)\\s.*>.*".r
          val haltedRegex(marHaltCode, aluHaltCode) = output

          halted.set(HaltCode(marHaltCode.toInt, aluHaltCode.toInt))
        }
        if (verbose) println("\t   \t: " + output)
      },
      ferr = output => {
        addLine("ERR:" + output)
        if (verbose) println("\tERR\t: " + output)
      }
    )

    // process has builtin timeout
    println("CMD: " + pb)
    val process = pb.run(logger)
    val ex = process.exitValue()
    println("EXIT CODE " + ex)

    if (ex == 127) {
      println("CANT FIND EXEC")
    }

    lines.synchronized {
      outputCheck(lines.toList)
    }

    val actualHalt = halted.get()
    val expectedHalt = checkHalt.orNull
    if (expectedHalt == null) {
      if (actualHalt != null) {
        println("expected no halt, but halted with: " + actualHalt)
      }
    } else {
      assertEquals(expectedHalt, actualHalt)
    }
    println("halted state: " + actualHalt)
  }
}

case class HaltCode(mar: Int, alu: Int) {
  override def toString: String = {
    s"HaltCode(mar:$mar, alu:$alu)"
  }
}
