// Don't forget to call (below) before importing this to silence deprecations
// interp.configureCompiler(_.settings.processArguments(List("-Wconf:cat=deprecation:s"), true))

interp.repositories() ::: List(
  coursierapi.MavenRepository.of("https://oss.sonatype.org/content/repositories/snapshots")
)

import $ivy.`edu.berkeley.cs::chisel3:3.6.1`
import $plugin.$ivy.`edu.berkeley.cs:::chisel3-plugin:3.6.1`
import $ivy.`edu.berkeley.cs::chiseltest:0.6.2`
import $ivy.`edu.berkeley.cs::firrtl-diagrammer:1.6.0`

import $ivy.`org.scalatest::scalatest:3.2.15`

// now needed after scala update to 2.13.14?
import scala.language.reflectiveCalls

def removeAllComments(verStr: String, delim: String = " // @"): String = {
    val lines = verStr.split('\n')
    def dropInfo(s: String): String = {
        if (s.contains(delim)) s.split(delim).head else s
    }
    val commentsRemoved = lines map dropInfo
    commentsRemoved.mkString("\n")
}

// Convenience function to invoke Chisel and grab emitted Verilog.
def getVerilog(dut: => chisel3.RawModule): String = {
  val arguments = Array("--emission-options",
                        "disableMemRandomization,disableRegisterRandomization",
                        "--info-mode", "ignore")
	try {
		removeAllComments((new chisel3.stage.ChiselStage).emitVerilog(dut, arguments))
	} catch {
		case e: Exception => s"An exception occurred: ${e.getMessage}"
	}
}

// Convenience function since users typically do println(getVerilog()) anyways
def printVerilog(dut: => chisel3.RawModule): Unit = println(getVerilog(dut))

// Convenience function to invoke Chisel and grab emitted FIRRTL.
def getFirrtl(dut: => chisel3.RawModule): String = {
  val arguments = Array("--emission-options",
                        "disableMemRandomization,disableRegisterRandomization",
                        "--info-mode", "ignore")
	try {
		removeAllComments((new chisel3.stage.ChiselStage).emitFirrtl(dut, arguments), " @")
	} catch {
		case e: Exception => s"An exception occurred: ${e.getMessage}"
	}
}

// Pretty prints the given firrtl AST
def stringifyAST(firrtlAST: firrtl.ir.Circuit): String = {
  var ntabs = 0
  val buf = new StringBuilder
  val string = firrtlAST.toString
  string.zipWithIndex.foreach { case (c, idx) =>
    c match {
      case ' ' =>
      case '(' =>
        ntabs += 1
        buf ++= "(\n" + "| " * ntabs
      case ')' =>
        ntabs -= 1
        buf ++= "\n" + "| " * ntabs + ")"
      case ','=> buf ++= ",\n" + "| " * ntabs
      case  c if idx > 0 && string(idx-1)==')' =>
        buf ++= "\n" + "| " * ntabs + c
      case c => buf += c
    }
  }
  buf.toString
}

// Returns path to module viz and hierarchy viz
def generateVisualizations(gen: () => chisel3.RawModule): (String, String) = {
    import dotvisualizer._
    import dotvisualizer.transforms._

    import java.io._
    import firrtl._
    import firrtl.annotations._

    import almond.interpreter.api.DisplayData
    import almond.api.helpers.Display

    import chisel3._
    import chisel3.stage._
    import firrtl.ir.Module
    import sys.process._

    val sourceFirrtl = scala.Console.withOut(new PrintStream(new ByteArrayOutputStream())) {
      (new ChiselStage).emitChirrtl(gen())
    }
    val ast = Parser.parse(sourceFirrtl)

    val uniqueTopName = ast.main + ast.hashCode().toHexString

    val targetDir = s"diagrams/$uniqueTopName/"

    val cmdRegex = "cmd[0-9]+([A-Za-z]+.*)".r
    val readableTop = ast.main match {
      case cmdRegex(n) => n
      case other => other
    }
    val newTop = readableTop

    // Console hack prevents unnecessary chatter appearing in cell
    scala.Console.withOut(new PrintStream(new ByteArrayOutputStream())) {
      val sourceFirrtl = (new ChiselStage).emitChirrtl(gen())

    val newModules: Seq[firrtl.ir.DefModule] = ast.modules.map {
      case m: Module if m.name == ast.main => m.copy(name = newTop)
      case other => other
    }
    val newAst = ast.copy(main = newTop, modules = newModules)

    val controlAnnotations: Seq[Annotation] = Seq(
        firrtl.stage.FirrtlSourceAnnotation(sourceFirrtl),
        firrtl.options.TargetDirAnnotation(targetDir),
        dotvisualizer.stage.OpenCommandAnnotation("")
      )

      (new dotvisualizer.stage.DiagrammerStage).execute(Array.empty, controlAnnotations)
    }
    val moduleView = s"""$targetDir/$newTop.dot.svg"""
    val instanceView = s"""$targetDir/${newTop}_hierarchy.dot.svg"""

    val svgModuleText = FileUtils.getText(moduleView)
    val svgInstanceText = FileUtils.getText(instanceView)

    val x = s"""<div width="100%" height="100%" overflow="scroll">$svgModuleText</div>"""
    val y = s"""<div> width="100%" height="100%"  overflow="scroll">$svgInstanceText</div>"""

    (x, y)
}

def visualize(gen: () => chisel3.RawModule): Unit = {
    val (moduleView, instanceView) = generateVisualizations(gen)
    html(moduleView)
}

def visualizeHierarchy(gen: () => chisel3.RawModule): Unit = {
    val (moduleView, instanceView) = generateVisualizations(gen)
    html(instanceView)
}


def simForWaveform[T <: chisel3.Module](dutGen: => T)(testFn: T => Unit) = {
    def vcdToWaveJSON(vcd: String): String = {
        val lines = vcd.split('\n').toSeq.filter(_.nonEmpty)
        val (header, rest) = lines.splitAt(lines.indexWhere(_.contains("scope")))
        val (decsRaw, values) = rest.splitAt(rest.indexWhere(_.contains("dumpvars")))
        val decs = decsRaw.tail.init.init
        val (initRaw, changesRaw) = values.splitAt(values.indexWhere(_.contains("end")))
        val init = initRaw.tail
        val changes = changesRaw.tail

        def splitDec(decStr: String) = {
            val tokens = decStr.trim.split(' ')
            val width = tokens(2).toInt
            assert(width <= 3)
            val label = tokens(3)
            val name = tokens(4)
            (label, name)
        }
        val labelsToNames = decs.map(splitDec).toMap

        def splitUpdate(updateStr: String) = {
            val value = updateStr.head.toString
            val label = updateStr.tail
            (label, value)
        }
        val labelsToValues = init.map(splitUpdate).toMap

        def grabCycleUpdates(cycleToProcess: Int,
                         wavesSoFar: Map[String,String],
                         changes: Seq[String]): Map[String,String] = {
            def cycleDelimeter(str: String) = str.head == '#'
            val nextCycleIndex = changes.indexWhere(cycleDelimeter, 1)
            val (currCycle, rest) = if (nextCycleIndex != -1) changes.splitAt(nextCycleIndex)
                                    else (changes, Seq())
            val currCycleNum = changes.head.tail.toInt
            val missingCycles = currCycleNum - cycleToProcess
            val filler = "." * missingCycles
            val changeList = currCycle.tail.map(splitUpdate).toMap
            val cycleAppended = wavesSoFar.map{
                case (label, valuesSoFar) => (label, valuesSoFar + filler + changeList.getOrElse(label, "."))
            }
            if (rest.nonEmpty) grabCycleUpdates(currCycleNum+1, cycleAppended, rest)
            else cycleAppended
        }
        val waves = grabCycleUpdates(0, labelsToValues, changes)
        def isMultibit(c: Char) = (c.toInt - '0'.toInt) > 1
        def containsMultiBit(waveValues: String) = waveValues.exists(isMultibit)
        def cleanMultibitInWave(wave: String) = {
            val waveRetouched = wave map { c => if (isMultibit(c)) '=' else c }
            val multibits = wave.filter(isMultibit)
            val datas = multibits.map(c => s"\"$c\"").mkString(",")
            (waveRetouched, datas)
        }

        def signalOrder(sigName: String): String = sigName match {
            case "clock" => "0"
            case "reset" => "1"
            case i if (i.startsWith("io")) => "2" + i
            case s => s
        }

        def wavesToWaveJSON(labelsToNames: Map[String, String], waves: Map[String, String]) = {
            val order = labelsToNames.toSeq.sortBy{case (label, name) => signalOrder(name)}
            val body = order.map {
                case (label, name) => if (containsMultiBit(waves(label))) {
                    val (waveRetouched, datas) = cleanMultibitInWave(waves(label))
                    s"""  { name: "$name", wave: "$waveRetouched", data: [$datas] },"""
                } else s"""  { name: "$name", wave: "${waves(label)}" },"""
            }
            (Seq("{ signal: [") ++ body ++ Seq("], }")).mkString("\n")
        }
        wavesToWaveJSON(labelsToNames, waves)
    }

    // run simulation
    val waveformDirName = "waveform_sim"
    firrtl.FileUtils.deleteDirectoryHierarchy(waveformDirName)
    val simAnnos = Seq(treadle.WriteVcdAnnotation,
                       firrtl.options.TargetDirAnnotation(waveformDirName))
    chiseltest.RawTester.test(dutGen, simAnnos)(testFn)

    // grab VCD and convert to wavedrom format
    val rawFirrtl = getFirrtl(dutGen)
    val circuitDecLine = rawFirrtl.split('\n')(1).split(' ')
    assert(circuitDecLine.head == "circuit")
    val circuitName = circuitDecLine(1)
    val vcdRaw = firrtl.FileUtils.getText(s"$waveformDirName/$circuitName.vcd")
    val waveJSON = vcdToWaveJSON(vcdRaw)

    // render result
    html(s"""
    <script src="https://cdnjs.cloudflare.com/ajax/libs/wavedrom/3.0.1/skins/default.js" type="text/javascript"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/wavedrom/3.0.1/wavedrom.min.js" type="text/javascript"></script>
    <script type="WaveDrom">
        $waveJSON
    </script>""")
    Javascript("""WaveDrom.ProcessAll();""")
}
