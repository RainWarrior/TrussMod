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

package rainwarrior.trussmod

import collection.JavaConversions._
import net.minecraftforge.client.model.{ AdvancedModelLoader, obj }
import net.minecraft.client.renderer.Tessellator.{ instance => tes }
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.Minecraft.{ getMinecraft => mc }
import net.minecraft.util.{ Icon, ResourceLocation }
import TrussMod._

object model {
  var models = Map.empty[String, obj.WavefrontObject]
  var icons = Map.empty[String, Icon]

  def loadModel(name: String) {
    val model = AdvancedModelLoader.loadModel(s"/assets/${modId.toLowerCase}/models/$name.obj").asInstanceOf[obj.WavefrontObject]
    val partNames = for(p <- model.groupObjects) yield p.name
    log.info(s"Loaded model $model with parts ${partNames.mkString}")
    models += name -> model
  }

  def getIcon(tpe: String, name: String) = icons.get(name) match {
    case Some(icon) => icon
    case None => throw new RuntimeException(s"Texture $tpe : $name wasn't loaded")
  }

  def loadIcon(map: TextureMap, name: String) = if(map.textureType == 0) {
      val icon = map.registerIcon(s"${modId.toLowerCase}:$name")
      icons += name -> icon
      //tmap.refreshTextures()
      println((name, icon, s"$modId:$name"))
  }

  def render(modelName: String, partName: String, icon: Icon) {
    assert(icon != null)
    val model = models(modelName)
    val part = (for {
      part <- model.groupObjects
      if part.name == partName
    } yield part).head

    for {
      f <- part.faces
      n = f.faceNormal
    } {
      tes.setNormal(n.x, n.y, n.z)
      for {
        i <- 0 until f.vertices.length
        v = f.vertices(i)
        t = f.textureCoordinates(i)
      } {
        tes.addVertexWithUV(v.x, v.y, v.z,
          icon.getInterpolatedU(t.u * 16),
          icon.getInterpolatedV(t.v * 16))
      }
    }
  }

  def renderTransformed(
      modelName: String,
      partName: String,
      icon: Icon,
      rotator: (Float, Float, Float) => (Float, Float, Float)) {
    val model = models(modelName)
    val part = (for {
      part <- model.groupObjects
      if part.name == partName
    } yield part).head

    for {
      f <- part.faces
      n = f.faceNormal
      (nx, ny, nz) = rotator(n.x, n.y, n.z)
    } {
      tes.setNormal(nx, ny, nz)
      for {
        i <- 0 until f.vertices.length
        v = f.vertices(i)
        t = f.textureCoordinates(i)
        (x, y, z) = rotator(v.x, v.y, v.z)
      } {
        tes.addVertexWithUV(x, y, z,
          icon.getInterpolatedU(t.u * 16),
          icon.getInterpolatedV(t.v * 16))
      }
    }
  }
}
