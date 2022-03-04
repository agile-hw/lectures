interp.repositories() ::: List(
  coursierapi.MavenRepository.of("https://oss.sonatype.org/content/repositories/snapshots")
)

import $ivy.`edu.berkeley.cs::chisel3:3.5.0`
import $plugin.$ivy.`edu.berkeley.cs:::chisel3-plugin:3.5.0`
import $ivy.`edu.berkeley.cs::chiseltest:0.5.+`
import $ivy.`edu.berkeley.cs::firrtl-diagrammer:1.5.+`

import $ivy.`org.scalatest::scalatest:3.2.2`


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
  removeAllComments((new chisel3.stage.ChiselStage).emitVerilog(dut, arguments))
}

// Convenience function to invoke Chisel and grab emitted FIRRTL.
def getFirrtl(dut: => chisel3.RawModule): String = {
  val arguments = Array("--emission-options",
                        "disableMemRandomization,disableRegisterRandomization",
                        "--info-mode", "ignore")
  removeAllComments((new chisel3.stage.ChiselStage).emitFirrtl(dut, arguments), " @")
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

