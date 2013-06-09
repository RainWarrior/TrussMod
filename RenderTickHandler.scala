/*

Copyright © 2012, 2013 RainWarrior

This file is part of MT100.

MT100 is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

MT100 is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with MT100. If not, see <http://www.gnu.org/licenses/>.

Additional permission under GNU GPL version 3 section 7

If you modify this Program, or any covered work, by linking or combining it
with Minecraft and/or MinecraftForge (or a modified version of Minecraft
and/or Minecraft Forge), containing parts covered by the terms of
Minecraft Terms of Use and/or Minecraft Forge Public Licence, the licensors
of this Program grant you additional permission to convey the resulting work.

*/

package rainwarrior.scalamod

import java.util.{ EnumSet, Set, HashSet }
import cpw.mods.fml.{ common, relauncher }
import common.{ ITickHandler, TickType, registry }
import relauncher.Side

object RenderTickHandler extends ITickHandler {
  override def ticks = EnumSet.of(TickType.RENDER)
  override def getLabel = "Scalamod Render Handler"
  override def tickStart(tp: EnumSet[TickType], tickData: AnyRef*) {
    if(tp contains TickType.RENDER) MovingRegistry.onPreRenderTick(tickData(0).asInstanceOf[Float])
  }
  override def tickEnd(tp: EnumSet[TickType], tickData: AnyRef*) {
    if(tp contains TickType.RENDER) MovingRegistry.onPostRenderTick()
  }
}
