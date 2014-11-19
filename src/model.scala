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

package rainwarrior.trussmod

import org.apache.logging.log4j.Logger

import scala.collection.mutable.ArrayBuffer
import scala.collection.Map
import scala.collection.JavaConversions._

import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.Minecraft.{ getMinecraft => mc }
import net.minecraft.util.IIcon

import cpw.mods.fml.relauncher.{ Side, SideOnly }
import Side.CLIENT

import rainwarrior.obj
import rainwarrior.utils.{ filterQuads, TexturedQuad, LightMatrix, light, staticLight }

import scalaxy.streams.optimize

object model {
  @inline def tes = Tessellator.instance
  var models = Map.empty[String, Map[String, Seq[TexturedQuad]]]
  var icons = Map.empty[String, IIcon]

  def loadModel(log: Logger, modId: String, name: String) {
    val model = obj.readObj(log, s"/assets/${modId.toLowerCase}/models/$name.obj")
    val partNames = for(o <- model.objects.keys) yield o
    log.trace(s"Loaded model $name with parts ${partNames.mkString}")
    models += name -> (model.objects.mapValues(filterQuads))
  }

  def getIcon(tpe: String, name: String) = icons.get(name) match {
    case Some(icon) => icon
    case None => throw new RuntimeException(s"Texture $tpe : $name wasn't loaded")
  }

  def loadIcon(log: Logger, map: TextureMap, modId: String, name: String) = if(map.getTextureType == 0) {
      val icon = map.registerIcon(s"${modId.toLowerCase}:$name")
      icons += name -> icon
      //tmap.refreshTextures()
      log.trace((name, icon, s"$modId:$name").toString)
  }

  def getPartFaces(modelName: String, partName: String) =
    models(modelName)(partName)

  @SideOnly(CLIENT)
  def render(m: LightMatrix, modelName: String, partName: String, icon: IIcon) : Unit =
    render(m, getPartFaces(modelName, partName), icon)

  @SideOnly(CLIENT)
  def render(m: LightMatrix, faces: Seq[TexturedQuad], icon: IIcon): Unit = {
    assert(icon != null)
    for {
      f <- faces
    } {
      val n = f.normal
      tes.setNormal(n.x.toFloat, n.y.toFloat, n.z.toFloat)
      optimize {
        for {
          i <- (0 until f.length)
        } {
          val v = f(i)
          val t = f.tq(i)
          val (b, c) = light(m)(v.x, v.y, v.z)
          val fc = (staticLight(n.x, n.y, n.z) * c).toFloat
          tes.setColorOpaque_F(fc, fc, fc)
          tes.setBrightness(b)
          tes.addVertexWithUV(v.x, v.y, v.z,
            icon.getInterpolatedU(t.x * 16),
            icon.getInterpolatedV(16.0 - t.y * 16))
        }
      }
    }
  }

  @SideOnly(CLIENT)
  def renderTransformed(
      m: LightMatrix,
      modelName: String,
      partName: String,
      icon: IIcon,
      rotator: (Double, Double, Double) => (Double, Double, Double)) {
    for {
      f <- getPartFaces(modelName, partName)
    } {
      val n = f.normal
      val (nx, ny, nz) = rotator(n.x, n.y, n.z)
      tes.setNormal(nx.toFloat, ny.toFloat, nz.toFloat)
      optimize {
        for {
          i <- (0 until f.length)
        } {
          val v = f(i)
          val t = f.tq(i)
          val (x, y, z) = rotator(v.x, v.y, v.z)
          val (b, c) = light(m)(x, y, z)
          val fc = (staticLight(nx, ny, nz) * c).toFloat
          tes.setColorOpaque_F(fc, fc, fc)
          tes.setBrightness(b)
          tes.addVertexWithUV(x, y, z,
            icon.getInterpolatedU(t.x * 16),
            icon.getInterpolatedV(16.0 - t.y * 16))
        }
      }
    }
  }
}
