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
  client.renderer.{ OpenGlHelper, RenderHelper, RenderBlocks },
  creativetab.CreativeTabs,
  entity.Entity,
  entity.player.EntityPlayer,
  init.{ Blocks, Items },
  item.{ Item, ItemStack },
  nbt.{ NBTTagCompound, NBTTagList },
  tileentity.TileEntity,
  util.{ AxisAlignedBB, MovingObjectPosition, Vec3 },
  world.{ ChunkPosition, chunk, EnumSkyBlock, IBlockAccess, NextTickListEntry, World, WorldServer },
  chunk.storage.ExtendedBlockStorage
import org.lwjgl.opengl.GL11._
import cpw.mods.fml.relauncher.{ SideOnly, Side }
import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.{ common, client, relauncher }
import common.{ Mod, event, registry, FMLCommonHandler, SidedProxy }
import registry.{ GameRegistry, LanguageRegistry }
import client.registry.{ ClientRegistry, RenderingRegistry, ISimpleBlockRenderingHandler }
import relauncher.{ FMLRelaunchLog, Side }
import net.minecraftforge.common.util.ForgeDirection
import TrussMod._
import rainwarrior.utils._
import rainwarrior.hooks.{ MovingRegistry, MovingTileRegistry }

import mods.immibis.core.api.multipart.util.BlockMultipartBase
import mods.immibis.microblocks.api.util.TileCoverableBase
import mods.immibis.microblocks.api.{ EnumPosition, EnumPositionClass, IMicroblockCoverSystem, PartType }

trait TraitImmibisFrame extends BlockMultipartBase with Frame {
  setStepSound(Block.soundTypeMetal)
  setBlockName(s"$modId:BlockFrame")
  setCreativeTab(CreativeTabs.tabBlock)
  //setBlockBounds(eps, eps, eps, 1 - eps, 1 - eps, 1 - eps)
  setBlockBounds(0, 0, 0, 1, 1, 1)

  import cpw.mods.fml.common.registry._
  GameRegistry.registerBlock(this, frameItemClass, "Frame_Block");
  GameRegistry.registerTileEntity(classOf[TileEntityImmibisFrame], "Frame_TileEntity");
  {
    val frame = new ItemStack(this, 8)
    val iron = new ItemStack(Blocks.iron_block)
    val redstone = new ItemStack(Items.redstone)
    val slime = new ItemStack(Items.slime_ball)
    GameRegistry.addRecipe(
      frame,
      "srs",
      "rir",
      "srs",
      Char.box('i'), iron,
      Char.box('r'), redstone,
      Char.box('s'), slime)
  }

  override def getPartHardness(world: World, x: Int, y: Int, z: Int, part: Int) = 5f 
  override def createNewTileEntity(world: World, meta: Int): TileEntity = new TileEntityImmibisFrame
  @SideOnly(Side.CLIENT)
  override def wrappedGetRenderType = BlockFrameRenderer.getRenderId

  override def wrappedCollisionRayTrace(world: World, x: Int, y: Int, z: Int, from: Vec3, to: Vec3) =
    blockRayTrace(world, x, y, z, from, to, model.getPartFaces("Frame", "Frame"))

  override def isSideSticky(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = {
    world.getTileEntity(x, y, z) match {
      case te: TileEntityImmibisFrame =>
        te.isSideSticky(side)
      case _ => true
    }
  }
}

class BlockImmibisFrame
  extends BlockMultipartBase(Material.ground)
  with TraitImmibisFrame

class TileEntityImmibisFrame extends TileCoverableBase {
  import EnumPosition._
  import EnumPositionClass.{ Centre => CCentre, _ }

  def isSideSticky(side: ForgeDirection) = {
    getCoverSystem match {
      case cs: IMicroblockCoverSystem =>
        val pos = EnumPosition.getFacePosition(side.ordinal)
        (for {
          p <- cs.getAllParts
          if p.pos == pos
          if p.`type`.getSize == 1D/8D
        } yield p).headOption match {
          case Some(part) =>
            false
          case none => true
        }
      case _ => true
    }
  }

  override def getPartPosition(subHit: Int) = Centre

  override def isPlacementBlockedByTile(tpe: PartType[_], pos: EnumPosition) = 
    tpe.getSize > 3F/8F || pos.clazz == EnumPositionClass.Post

  override def isPositionOccupiedByTile(pos: EnumPosition) = pos == Centre

  override def getPlayerRelativePartHardness(player: EntityPlayer, part: Int) =
    player.getBreakSpeed(getBlockType, false, getBlockMetadata, 0, -1, 0) / 3F / 30F

  override def pickPart(rayTrace: MovingObjectPosition, part: Int) = 
    new ItemStack(getBlockType)

  override def isSolidOnSide(side: ForgeDirection) = false

  @SideOnly(Side.CLIENT)
  override def render(rb: RenderBlocks) {
    renderPart(rb, 0)
  }

  @SideOnly(Side.CLIENT)
  override def renderPart(rb: RenderBlocks, part: Int) {
    BlockFrameRenderer.renderWithSides(
      worldObj,
      xCoord, yCoord, zCoord,
      getBlockType,
      for(s <- ForgeDirection.VALID_DIRECTIONS) yield isSideSticky(s))
  }

  override def removePartByPlayer(player: EntityPlayer, part: Int): JList[ItemStack] = {
    val drops = List(new ItemStack(getBlockType))
    getCoverSystem match {
      case cs: IMicroblockCoverSystem =>
        cs.convertToContainerBlock()
      case _ =>
        worldObj.setBlockToAir(xCoord, yCoord, zCoord)
    }
    drops
  }

  override def getPartAABBFromPool(part: Int) =
    AxisAlignedBB.getAABBPool.getAABB(eps, eps, eps, 1 - eps, 1 - eps, 1 - eps)

  /*override def getCollidingBoundingBoxes(mask: AxisAlignedBB, list: JList[AxisAlignedBB]) {
    val hit = AxisAlignedBB.getAABBPool.getAABB(0, 0, 0, 1, 1, 1).offset(xCoord, yCoord, zCoord)
    if(hit.intersectsWith(mask))
      list.add(hit)
  }*/

  override def getNumTileOwnedParts() = 1

}
