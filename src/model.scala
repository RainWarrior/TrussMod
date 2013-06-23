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
import net.minecraft.util.Icon
import TrussMod._

object model {
  var models = Map.empty[String, obj.WavefrontObject]

  def loadModel(name: String) {
    val model = AdvancedModelLoader.loadModel(s"/mods/$modId/models/$name.obj").asInstanceOf[obj.WavefrontObject]
    val partNames = for(p <- model.groupObjects) yield p.name
    log.info(s"Loaded model $model with parts ${partNames.mkString}")
    models += name -> model
  }

  def render(modelName: String, partName: String, icon: Icon) {
    val model = models(modelName)
    val part = (for {
      part <- model.groupObjects
      if part.name == partName
    } yield part).head

    for(f <- part.faces; i <- 0 until f.vertices.length; v = f.vertices(i); t = f.textureCoordinates(i)) {
      tes.addVertexWithUV(v.x, v.y, v.z,
        icon.getInterpolatedU(t.u * 16),
        icon.getInterpolatedV(t.v * 16))
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
      (v, t) <- f.vertices zip f.textureCoordinates
      (x, y, z) = rotator(v.x, v.y, v.z)
    } {
      tes.addVertexWithUV(x, y, z,
        icon.getInterpolatedU(t.u * 16),
        icon.getInterpolatedV(t.v * 16))
    }
  }
}
