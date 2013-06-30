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

import java.util.{ List => JList, ArrayList, Set => JSet, TreeSet => JTreeSet }
import collection.immutable.{ HashSet, Queue }
import collection.mutable.{ HashMap => MHashMap, MultiMap, Set => MSet, HashSet => MHashSet }
import collection.JavaConversions._
import net.minecraft._,
  block.{ Block, BlockContainer },
  block.material.Material,
  client.Minecraft.{ getMinecraft => mc },
  client.renderer.tileentity.TileEntitySpecialRenderer,
  client.renderer.Tessellator.{ instance => tes },
  client.renderer.texture.IconRegister,
  client.renderer.{ OpenGlHelper, RenderHelper, RenderBlocks },
  creativetab.CreativeTabs,
  entity.Entity,
  entity.player.EntityPlayer,
  item.{ Item, ItemBlock, ItemStack },
  nbt.{ NBTTagCompound, NBTTagList },
  network.INetworkManager,
  network.packet.{ Packet, Packet132TileEntityData },
  tileentity.TileEntity,
  util.AxisAlignedBB,
  world.{ ChunkPosition, chunk, EnumSkyBlock, IBlockAccess, NextTickListEntry, World, WorldServer },
  chunk.storage.ExtendedBlockStorage
import org.lwjgl.opengl.GL11._
import cpw.mods.fml.relauncher.{ SideOnly, Side }
import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.{ common, client, relauncher }
import common.{ Mod, event, network, registry, FMLCommonHandler, SidedProxy }
import network.NetworkMod
import registry.{ GameRegistry, LanguageRegistry, TickRegistry }
import client.registry.{ ClientRegistry, RenderingRegistry, ISimpleBlockRenderingHandler }
import relauncher.{ FMLRelaunchLog, Side }
import net.minecraftforge.common.{ MinecraftForge, ForgeDirection }
import TrussMod._
import rainwarrior.utils._
import rainwarrior.hooks.{ MovingRegistry, MovingTileRegistry }

trait Frame {
  def isSideSticky(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): Boolean
}

class FrameBlockProxy {
  def init(): Block = {
    object blockFrame
      extends Block(CommonProxy.blockFrameId, Material.ground)
      with BlockFrame
    blockFrame
  }
}

class FrameItemProxy {
  def init(): Item = {
    new ItemBlock(CommonProxy.blockFrameId - 256) // Hmm
  }
}

trait BlockFrame extends Block with Frame {
  var renderType = -1

  setHardness(.5f)
  setStepSound(Block.soundGravelFootstep)
  setUnlocalizedName(modId + ":BlockFrame")
  setCreativeTab(CreativeTabs.tabBlock)

  import cpw.mods.fml.common.registry._
  LanguageRegistry.addName(this, "Frame Block")
  GameRegistry.registerBlock(this, "Frame_Block");
  {
    val frame = new ItemStack(this, 8)
    val iron = new ItemStack(Block.blockIron)
    val redstone = new ItemStack(Item.redstone)
    val slime = new ItemStack(Item.slimeBall)
    GameRegistry.addRecipe(
      frame,
      "rsr",
      "sis",
      "rsr",
      Char.box('i'), iron,
      Char.box('r'), redstone,
      Char.box('s'), slime)
  }

  @SideOnly(Side.CLIENT)
  override def registerIcons(registry: IconRegister) {}

  //override def createNewTileEntity(world: World): TileEntity = null // new TileEntityFrame
  override def isOpaqueCube = false
  override def isBlockSolidOnSide(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = {
    true
  }
  override def renderAsNormalBlock = false
  //override def renderAsNormalBlock = false

  override def getRenderType = renderType

  override def onBlockAdded(world: World, x: Int, y: Int, z: Int) {
    super.onBlockAdded(world, x, y, z)
    //onNeighborBlockChange(world, x, y, z, this.blockID)
    //world.setBlockMetadata(x, y, z, 13)
  }

  override def breakBlock(world: World, x: Int, y: Int, z: Int, id: Int, metadata: Int) {
    //TileEntity t = world.getBlockTileEntity(x, y, z)
    //if(t instanceof TileEntityProxy)
    //{
      //((TileEntityProxy)t).invalidate
    //}
    super.breakBlock(world, x, y, z, id, metadata)
    //world.notifyBlocksOfNeighborChange(x, y, z, this.blockID)
  }

  override def onBlockActivated(
      world: World,
      x: Int,
      y: Int,
      z: Int,
      player: EntityPlayer,
      side: Int,
      dx: Float,
      dy: Float,
      dz: Float): Boolean  = {
    /*log.info(f"onBlockActivated: ($x,$y,$z), isServer: $isServer")
    //if(world.isRemote) {
      val te = world.getBlockTileEntity(x, y, z).asInstanceOf[TileEntityFrame]
      if(te == null)
        throw new RuntimeException("no tile entity!")
        FMLCommonHandler.instance.showGuiScreen(te.openGui())
    }*/
    false
  }

  override def isSideSticky(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = true
}

@SideOnly(Side.CLIENT)
object BlockFrameRenderer extends ISimpleBlockRenderingHandler {
  model.loadModel("Frame")
  CommonProxy.frameBlock match {
    case block: BlockFrame => block.renderType = getRenderId
    case _ =>
  }

  override def renderInventoryBlock(
      block: Block,
      metadata: Int,
      modelId: Int,
      rb: RenderBlocks) {
    //rainwarrior.utils.renderInventoryBlock(rb, block, metadata)
    RenderHelper.disableStandardItemLighting()
    tes.startDrawingQuads()
    tes.setColorOpaque_F(1, 1, 1)
    model.render("Frame", "Frame", model.getIcon("block", "BlockFrame"))
    tes.draw()
    RenderHelper.enableStandardItemLighting()
  }

  override def renderWorldBlock(
      world: IBlockAccess,
      x: Int,
      y: Int,
      z: Int,
      block: Block,
      modelId: Int,
      rb: RenderBlocks) = {
    //rb.setRenderBoundsFromBlock(block)
    //rb.renderStandardBlock(block, x, y, z)
    tes.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z))
    tes.setColorOpaque_F(1, 1, 1)
    tes.addTranslation(x + .5F, y + .5F, z + .5F)
    model.render("Frame", "Frame", model.getIcon("block", "BlockFrame"))
    tes.addTranslation(-x - .5F, -y - .5F, -z - .5F)
    true
  }

  def renderWithSides(
      world: World,
      x: Int,
      y: Int,
      z: Int,
      block: Block,
      sideSticky: Array[Boolean]) {
    val sideOffsets = for(s <- sideSticky) yield s match {
      case true => 1
      case false => -1
    }
    assert(block != null)
    assert(world != null)
    tes.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z))
    tes.setColorOpaque_F(1, 1, 1)
    tes.addTranslation(x + .5F, y + .5F, z + .5F)
    model.renderTransformed("Frame", "Frame", model.getIcon("block", "BlockFrame"), sideFixer(sideOffsets))
    tes.addTranslation(-x - .5F, -y - .5F, -z - .5F)
  }

  override val shouldRender3DInInventory = true
  override lazy val getRenderId = RenderingRegistry.getNextAvailableRenderId()
}
