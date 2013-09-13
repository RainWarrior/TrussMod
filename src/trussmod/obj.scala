/*

Copyright © 2012, 2013 RainWarrior

This file is part of TrussMod.

TrussMod is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

TrussMod is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with TrussMod. If not, see <http://www.gnu.org/licenses/>.

Additional permission under GNU GPL version 3 section 7

If you modify this Program, or any covered work, by linking or combining it
with Minecraft and/or MinecraftForge (or a modified version of Minecraft
and/or Minecraft Forge), containing parts covered by the terms of
Minecraft Terms of Use and/or Minecraft Forge Public Licence, the licensors
of this Program grant you additional permission to convey the resulting work.

*/

package rainwarrior

import java.util.logging.Logger
import scala.util.parsing.combinator._
import scala.io.Source

object obj {

  def readObj(log: Logger, url: String) = {
    val rawFile = Source.fromInputStream(this.getClass.getResource(url).openStream()).mkString("")
    val file = rawFile.replaceAll("""\r""", "").replaceAll("""\\\n""", "").replaceAll("""#[^\n]*\n""", "\n").replaceAll("""(?m)^[ \t]*\n""", "")
    val parser = new ObjParser(log)
    parser.parseAll(parser.obj, file)
  }

  sealed abstract class ObjElement
  case class Vertex(x: Double, y: Double, z: Double, w: Double) extends ObjElement
  case class NormalVertex(i: Double, j: Double, k: Double) extends ObjElement
  case class TextureVertex(u: Double, v: Double, w: Double) extends ObjElement
  case class Points(vs: List[Int]) extends ObjElement

  sealed abstract class Line extends ObjElement
  case class VertexLine(vs: List[Int]) extends Line
  case class TexturedLine(vs: List[(Int, Int)]) extends Line

  sealed abstract class Face extends ObjElement
  case class VertexFace(vs: List[Int]) extends Face
  case class TexturedFace(vs: List[(Int, Int)]) extends Face
  case class NormaledFace(vs: List[(Int, Int)]) extends Face
  case class TexturedNormaledFace(vs: List[(Int, Int, Int)]) extends Face

  case class Groups(names: List[String]) extends ObjElement
  case class SmoothGroup(index: Option[Int]) extends ObjElement
  case class Object(name: String) extends ObjElement
  case class Bevel(state: Boolean) extends ObjElement
  case class ColorInterpolation(state: Boolean) extends ObjElement
  case class DissolveInterpolation(state: Boolean) extends ObjElement
  case class LodLevel(level: Int) extends ObjElement
  case class Unsupported(string: String) extends ObjElement

  class ObjParser(val log: Logger) extends RegexParsers {
    override val whiteSpace = """[ \t]+""".r

    def int: Parser[Int] = """-?\d+""".r ^^ (_.toInt)
    def double: Parser[Double] = """-?(\d+(\.\d*)?|\d*\.\d+)""".r ^^ (_.toDouble)

    def line[U](s: String, p: Parser[U]): Parser[U] = ("^" + s).r ~> p <~ "\n"

    def v: Parser[Vertex] = line("v", double~double~double~opt(double)) ^^ {
      case x~y~z~Some(w) => Vertex(x, y, z, w)
      case x~y~z~None => Vertex(x, y, z, 1.0)
    }

    def vn: Parser[NormalVertex] = line("vn", double~double~double) ^^ {
      case i~j~k => NormalVertex(i, j, k)
    }

    def vt: Parser[TextureVertex] = line("vt", double~opt(double~opt(double))) ^^ {
      case u~None => TextureVertex(u, 0.0, 0.0)
      case u~Some(v~None) => TextureVertex(u, v, 0.0)
      case u~Some(v~Some(w)) => TextureVertex(u, v, w)
    }

    def ct: Parser[(Int, Int)] = (int<~"/")~int ^^ { case v~vt => (v, vt) }

    def cn: Parser[(Int, Int)] = (int<~"//")~int ^^ { case v~vn => (v, vn) }

    def ctn: Parser[(Int, Int, Int)] = (int<~"/")~(int<~"/")~int ^^ { case v~vt~vn => (v, vt, vn) }

    def p: Parser[Points] = line("p", rep(int) ^^ Points.apply)
    
    def l: Parser[Line] =
      line("l", rep(int) ^^ VertexLine.apply) |
      line("l", rep(ct) ^^ TexturedLine.apply)

    def f: Parser[Face] =
      line("f", rep(int) ^^ VertexFace.apply) |
      line("f", rep(ct)  ^^ TexturedFace.apply) |
      line("f", rep(cn)  ^^ NormaledFace.apply) |
      line("f", rep(ctn) ^^ TexturedNormaledFace.apply)

    def on: Parser[Boolean] = ("on" ^^^ true)
    def off: Parser[Boolean] = ("off" ^^^ false)

    def name: Parser[String] = """[^ \t\n]+""".r

    def g = line("g", rep(name) ^^ Groups.apply)

    def s = line("s", (int ^^ {i => SmoothGroup(Some(i)) }) | (("off" | "0") ^^^ SmoothGroup(None)))
    
    def o = line("o", name ^^ Object.apply)

    def bevel = line("bevel", on|off ^^ Bevel.apply)

    def c_interp = line("c_interp", on|off ^^ ColorInterpolation.apply)

    def d_interp = line("d_interp", on|off ^^ DissolveInterpolation.apply)

    def lod = line("lod", int ^? ({ case l if(l >= 0 && l <= 100) => LodLevel(l) }, l => s"lod level $l is out of range"))

    def unsupported = """^(maplib|usemap|usemtl|mtllib|shadow_obj|trace_obj|deg|bmat|step|curv|curv2|surf|parm|trim|hole|scrv|sp|end|con|mg|ctech|stech).*""".r <~ """\n""".r ^^ { l => log.warning(s"Ignoring statement: '$l'"); Unsupported(l) }

    def obj: Parser[List[Any]] = rep(v|vn|vt|p|l|f|g|s|o|bevel|c_interp|d_interp|lod|unsupported)
  }
}
