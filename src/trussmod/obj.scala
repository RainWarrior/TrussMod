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

  class ObjParser(val log: Logger) extends RegexParsers {
    override val whiteSpace = """[ \t]+""".r

    def int: Parser[Int] = """-?\d+""".r ^^ (_.toInt)
    def double: Parser[Double] = """-?(\d+(\.\d*)?|\d*\.\d+)""".r ^^ (_.toDouble)

    def line[U](s: String, p: Parser[U]): Parser[U] = ("^" + s).r ~> p <~ "\n"

    def v: Parser[(Double, Double, Double, Double)] = line("v", double~double~double~opt(double)) ^^ {
      case x~y~z~Some(w) => (x, y, z, w)
      case x~y~z~None => (x, y, z, 1.0)
    }

    def vn: Parser[(Double, Double, Double)] = line("vn", double~double~double) ^^ {
      case i~j~k => (i, j, k)
    }

    def vt: Parser[(Double, Double, Double)] = line("vt", double~opt(double~opt(double))) ^^ {
      case u~None => (u, 0.0, 0.0)
      case u~Some(v~None) => (u, v, 0.0)
      case u~Some(v~Some(w)) => (u, v, w)
    }

    def ct: Parser[(Int, Int)] = (int<~"/")~int ^^ { case v~vt => (v, vt) }

    def cn: Parser[(Int, Int)] = (int<~"//")~int ^^ { case v~vn => (v, vn) }

    def ctn: Parser[(Int, Int, Int)] = (int<~"/")~(int<~"/")~int ^^ { case v~vt~vn => (v, vt, vn) }

    def p: Parser[List[Int]] = line("p", rep(int))
    
    def l: Parser[List[Either[Int, (Int, Int)]]] =
      line("l", rep(int) ^^ { _.map(Left.apply) }) |
      line("l", rep(ct) ^^ { _.map(Right.apply) })

    def f: Parser[List[Either[Either[Int, (Int, Int)], Either[(Int, Int), (Int, Int, Int)]]]] =
      line("f", rep(int) ^^ { _.map(c => Left(Left(c)))   }) |
      line("f", rep(ct)  ^^ { _.map(c => Left(Right(c)))  }) |
      line("f", rep(cn)  ^^ { _.map(c => Right(Left(c)))  }) |
      line("f", rep(ctn) ^^ { _.map(c => Right(Right(c))) })

    def on: Parser[Boolean] = ("on" ^^^ true)
    def off: Parser[Boolean] = ("off" ^^^ false)

    def name: Parser[String] = """[^ \t\n]+""".r

    def g: Parser[List[String]] = line("g", rep(name))

    def s: Parser[Option[Int]] = line("s", (int ^^ Some.apply ) | (("off" | "0") ^^^ None))
    
    def o: Parser[String] = line("o", name)

    def bevel = line("bevel", on|off)

    def c_interp = line("c_interp", on|off)

    def d_interp = line("d_interp", on|off)

    def lod = line("lod", int ^? ({ case l if(l >= 0 && l <= 100) => l }, l => s"lod level $l is out of range"))

    def unsupported = """^(maplib|usemap|usemtl|mtllib|shadow_obj|trace_obj|deg|bmat|step|curv|curv2|surf|parm|trim|hole|scrv|sp|end|con|mg|ctech|stech).*\n""".r ^^ { l => log.warning(s"Ignoring statement: '$l'") }

    def obj: Parser[List[Any]] = rep(v|vn|vt|p|l|f|g|s|o|bevel|c_interp|d_interp|lod|unsupported)
  }
}
