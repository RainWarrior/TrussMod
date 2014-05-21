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

import net.minecraft.{ block, creativetab, entity, item, world, util },
  block.Block,
  creativetab.CreativeTabs,
  entity.player.EntityPlayer,
  item.{ Item, ItemStack },
  world.World,
  util.ChatComponentText
import cpw.mods.fml.common.{ Loader, registry }
import registry.GameRegistry
import cpw.mods.fml.client.FMLClientHandler.{ instance => FMLClientHandler }
import net.minecraftforge.common.util.ForgeDirection

import rainwarrior.utils._
import TrussMod._

class DebugItem extends Item {
  setMaxStackSize(1)
  setCreativeTab(CreativeTabs.tabMisc)
  setUnlocalizedName("trussmod:DebugItem")
  setTextureName("iron_shovel")

  GameRegistry.registerItem(this, "debugItem")

  override def onItemUseFirst(
      stack: ItemStack,
      player: EntityPlayer,
      world: World,
      x: Int, y: Int, z: Int,
      side: Int,
      hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    val block = world.getBlock(x, y, z)
    if(world.isClient) {
      val name = block.getUnlocalizedName
      val msg = s"USE: ($x,$y,$z), Block: $name"
      //log.info(msg)
      FMLClientHandler.getClient.ingameGUI.getChatGUI.printChatMessage(new ChatComponentText(msg))
      rainwarrior.hooks.MovingRegistry.addMoving(world, (x, y, z), new BlockData(0F, .5F, 0F, ForgeDirection.UP))
    } else {
      world.getTileEntity(x, y, z) match {
        /*case te: TileEntityMotor =>
          var msg = s"($x, $y, $z): ${te.energy} "
          if(Loader.isModLoaded(Power.bcid)) msg += te.powerHandler.getEnergyStored
          log.info(msg)*/
        case _ =>
      }
    }
    false
  }
}

