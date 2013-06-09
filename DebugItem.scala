package rainwarrior.scalamod

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
import Scalamod._
import util._

trait DebugItem extends Item {
  setMaxStackSize(1)
  setCreativeTab(CreativeTabs.tabMisc)
  setUnlocalizedName("Scalamod:debugItem")

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
      EffectiveSide(world) match {
        case Client =>
          val name = Block.blocksList(id).getUnlocalizedName
          val msg = f"USE: ($x,$y,$z), Block: $id, $name"
          //Scalamod.log.info(msg)
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
          FrameBfs(world, (x, y, z), dir.NORTH)
      }
    //}
    false
  }
}

