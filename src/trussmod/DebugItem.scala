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

import cpw.mods.fml.relauncher
import relauncher.{ Side, SideOnly }
import net.minecraft._,
  item.{ Item, ItemStack },
  block.Block,
  client.renderer.texture.IconRegister,
  client.Minecraft.getMinecraft,
  creativetab.CreativeTabs,
  entity.player.EntityPlayer,
  world.World
  import net.minecraftforge.common.{ ForgeDirection => dir }
import TrussMod._
import rainwarrior.utils._

trait DebugItem extends Item {
  setMaxStackSize(1)
  setCreativeTab(CreativeTabs.tabMisc)
  setUnlocalizedName(modId + ":debugItem")

  import cpw.mods.fml.common.registry._
  LanguageRegistry.addName(this, "Debug Item")

  override def requiresMultipleRenderPasses() = true

  override def onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ItemStack = stack

  override def onItemUseFirst(
      stack: ItemStack,
      player: EntityPlayer,
      world: World,
      x: Int,
      y: Int,
      z: Int,
      side: Int,
      hitX: Float,
      hitY: Float,
      hitZ: Float): Boolean = {
    //if(world.getBlockId(x, y + 1, z) == 0) {
      val id = world.getBlockId(x, y, z)
      val meta = world.getBlockMetadata(x, y, z)
      val te = world.getBlockTileEntity(x, y, z)
      val name = Block.blocksList(id).getUnlocalizedName
      EffectiveSide(world) match {
        case Client =>
          val msg = s"USE: ($x,$y,$z), Block: $id, $meta, $te, $name"
          getMinecraft.ingameGUI.getChatGUI.printChatMessage(msg)
          /*val te = world.getBlockTileEntity(x, y, z)
          val pos = WorldPos(x, y, z)
          if(MovingRegistry.moving.isDefinedAt(pos)) {
            MovingRegistry.delMoving(world, pos)
          } else {
            MovingRegistry.addMoving(world, pos, MovingRegistry.debugOffset)
          }*/
        case Server =>
          //CommonProxy.blockMovingStrip.create(world, x, y + 1, z, dir.UP, 2)
          //FrameBfs(world, (x, y, z), dir.NORTH)
      }
    //}
    false
  }
}

