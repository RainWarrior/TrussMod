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
import TrussMod._

object model {
  val model = AdvancedModelLoader.loadModel(s"/mods/$modId/models/Frame.obj").asInstanceOf[obj.WavefrontObject]
  val faces: Seq[obj.Face] = model.groupObjects.get(0).faces
  @inline def shift(x: Double): Double = (x - .5) * (1D - 1D/65536D) + .5
  def render() {
    val icon = CommonProxy.blockFrame.getIcon(0, 0)
    for(f <- faces; (v, t) <- f.vertices zip f.textureCoordinates) {
      tes.addVertexWithUV(v.x, v.y, v.z, 
        icon.getInterpolatedU(t.u * 16),
        icon.getInterpolatedV(t.v * 16))
    }
  }
}
