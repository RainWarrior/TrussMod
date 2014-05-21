/*

Copyright © 2012 - 2014 RainWarrior

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

import org.apache.logging.log4j.Logger
import scala.util.parsing.combinator._
import scala.io.Source
import scala.collection.mutable.{ ArrayBuffer, Map => MMap }
import scala.collection.Map

object obj {

  def readObj(log: Logger, url: String) = {
    val rawFile = Source.fromInputStream(this.getClass.getResource(url).openStream(), "UTF-8").mkString("")
    val file = filterObjFile(rawFile)
    val parser = new ObjParser(log)
    parser.parseAll(parser.obj, file) match {
      case parser.Success(data, next) => groopifyObj(data)
      case parser.NoSuccess(err, next) => throw new RuntimeException(err)
    }
  }

  def filterObjFile(file: String) = {
    (file + "\n").replaceAll("""\r""", "").replaceAll("""\\\n""", "").replaceAll("""#[^\n]*\n""", "\n").replaceAll("""(?m)^[ \t]*\n""", "")
  }

  case class Obj(groupObjects: Map[String, ArrayBuffer[Element]], objects: Map[String, ArrayBuffer[Element]])

  def groopifyObj(data: List[ResultElement]) = {
    val groupObjects = MMap("default" -> new ArrayBuffer[Element])
    val objects: MMap[String, ArrayBuffer[Element]] = MMap.empty
    var curGroups = Set(groupObjects("default"))
    var curObject: Option[ArrayBuffer[Element]] = None
    for(oe <- data) oe match { // TODO unused parameters
      case e: Element =>
        for(g <- curGroups) g += e
        curObject foreach { _ += e }
      case gn: Groups => curGroups = (for(g <- gn.names) yield groupObjects.getOrElseUpdate(g, new ArrayBuffer[Element])).toSet
      case s: SmoothGroup =>
      case o: Object => curObject = Some(objects.getOrElseUpdate(o.name, new ArrayBuffer[Element]))
      case b: Bevel =>
      case c: ColorInterpolation =>
      case d: DissolveInterpolation =>
      case l: LodLevel =>
      case null =>
    }
    Obj(groupObjects, objects)
  }

  sealed abstract class ObjElement
  sealed abstract class Vertex extends ObjElement

  case class CoordVertex(x: Double, y: Double, z: Double, w: Double) extends Vertex
  case class NormalVertex(i: Double, j: Double, k: Double) extends Vertex
  case class TextureVertex(u: Double, v: Double, w: Double) extends Vertex

  sealed abstract class ResultElement extends ObjElement
  sealed abstract class Element extends ResultElement
  case class Points(vs: List[CoordVertex]) extends Element

  sealed abstract class Line extends Element
  case class CoordLine(vs: List[CoordVertex]) extends Line
  case class TexturedLine(vs: List[(CoordVertex, TextureVertex)]) extends Line

  sealed abstract class Face extends Element
  case class CoordFace(vs: List[CoordVertex]) extends Face
  case class TexturedFace(vs: List[(CoordVertex, TextureVertex)]) extends Face
  case class NormaledFace(vs: List[(CoordVertex, NormalVertex)]) extends Face
  case class TexturedNormaledFace(vs: List[(CoordVertex, TextureVertex, NormalVertex)]) extends Face

  case class Groups(names: List[String]) extends ResultElement
  case class SmoothGroup(index: Option[Int]) extends ResultElement
  case class Object(name: String) extends ResultElement
  case class Bevel(state: Boolean) extends ResultElement
  case class ColorInterpolation(state: Boolean) extends ResultElement
  case class DissolveInterpolation(state: Boolean) extends ResultElement
  case class LodLevel(level: Int) extends ResultElement
  //case class Unsupported(string: String) extends ResultElement

  def gs[T](a: ArrayBuffer[T])(i: Int) = {
    if(i >= 0) a(i - 1)
    else a(a.length + i)
  }

  class ObjParser(val log: Logger) extends RegexParsers {
    override val whiteSpace = """[ \t]+""".r

    val vertices = new ArrayBuffer[CoordVertex]
    val normals = new ArrayBuffer[NormalVertex]
    val textures = new ArrayBuffer[TextureVertex]

    def int: Parser[Int] = """-?\d+""".r ^^ (_.toInt)
    def double: Parser[Double] = """-?(\d+(\.\d*)?|\d*\.\d+)""".r ^^ (_.toDouble)

    def line[U](s: String, p: Parser[U]): Parser[U] = ("^" + s).r ~> p <~ "\n"

    def v: Parser[Null] = line("v", double~double~double~opt(double)) ^^ {
      case x~y~z~Some(w) => vertices += CoordVertex(x, y, z, w); null
      case x~y~z~None => vertices += CoordVertex(x, y, z, 1.0); null
    }

    def vn: Parser[Null] = line("vn", double~double~double) ^^ {
      case i~j~k => normals += NormalVertex(i, j, k); null
    }

    def vt: Parser[Null] = line("vt", double~opt(double~opt(double))) ^^ {
      case u~None => textures += TextureVertex(u, 0.0, 0.0); null
      case u~Some(v~None) => textures += TextureVertex(u, v, 0.0); null
      case u~Some(v~Some(w)) => textures += TextureVertex(u, v, w); null
    }

    def ct: Parser[(Int, Int)] = (int<~"/")~int ^^ { case v~vt => (v, vt) }

    def cn: Parser[(Int, Int)] = (int<~"//")~int ^^ { case v~vn => (v, vn) }

    def ctn: Parser[(Int, Int, Int)] = (int<~"/")~(int<~"/")~int ^^ { case v~vt~vn => (v, vt, vn) }

    def p: Parser[Points] = line("p", rep(int) ^^ { vs => Points(vs.map(gs(vertices))) })
    
    def l: Parser[Line] =
      line("l", rep(int) ^^ { vs => CoordLine(vs.map(gs(vertices))) }) |
      line("l", rep(ct) ^^ { vs => TexturedLine(vs.map {
        case (c, t) => (gs(vertices)(c), gs(textures)(t))
      }) })

    def f: Parser[Face] =
      line("f", rep(int) ^^ { vs => CoordFace(vs.map(gs(vertices))) }) |
      line("f", rep(ct) ^^ { vs => TexturedFace(vs.map {
        case (c, t) => (gs(vertices)(c), gs(textures)(t))
      }) }) |
      line("f", rep(cn) ^^ { vs => NormaledFace(vs.map {
        case (c, n) => (gs(vertices)(c), gs(normals)(n))
      }) }) |
      line("f", rep(ctn) ^^ { vs => TexturedNormaledFace(vs.map {
        case (c, t, n) => (gs(vertices)(c), gs(textures)(t), gs(normals)(n))
      }) })

    def on: Parser[Boolean] = ("on" ^^^ true)
    def off: Parser[Boolean] = ("off" ^^^ false)

    def name: Parser[String] = """[^ \t\n]+""".r

    def g: Parser[Groups] = line("g", rep(name) ^^ Groups.apply)

    def s: Parser[SmoothGroup] = line("s", (int ^^ {i => SmoothGroup(Some(i)) }) | (("off" | "0") ^^^ SmoothGroup(None)))
    
    def o: Parser[Object] = line("o", name ^^ Object.apply)

    def bevel: Parser[Bevel] = line("bevel", (on|off) ^^ Bevel.apply)

    def c_interp: Parser[ColorInterpolation] = line("c_interp", (on|off) ^^ ColorInterpolation.apply)

    def d_interp: Parser[DissolveInterpolation] = line("d_interp", (on|off) ^^ DissolveInterpolation.apply)

    def lod: Parser[LodLevel] = line("lod", int ^? ({ case l if(l >= 0 && l <= 100) => LodLevel(l) }, l => s"lod level $l is out of range"))

    def unsupported: Parser[Null] = """^(maplib|usemap|usemtl|mtllib|shadow_obj|trace_obj|deg|bmat|step|curv|curv2|surf|parm|trim|hole|scrv|sp|end|con|mg|ctech|stech).*""".r <~ """\n""".r ^^ { l => log.warn(s"Ignoring statement: '$l'"); null }

    def obj: Parser[List[ResultElement]] = rep(v|vn|vt|p|l|f|g|s|o|bevel|c_interp|d_interp|lod|unsupported)
  }
}
