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
  item.{ Item, ItemStack },
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


trait BlockMovingStrip extends BlockContainer {
  setHardness(-1F)
  setStepSound(Block.soundGravelFootstep)
  setUnlocalizedName(modId + ":BlockMovingStrip")
  setCreativeTab(null)
  setBlockBounds(.5F, .5F, .5F, .5F, .5F, .5F)

  import cpw.mods.fml.common.registry._
  LanguageRegistry.addName(this, "Moving Strip Block")
  GameRegistry.registerBlock(this, "Moving_Strip_Block")
  GameRegistry.registerTileEntity(classOf[TileEntityMovingStrip], "Moving_Strip_TileEntity")

  override def createNewTileEntity(world: World) = new TileEntityMovingStrip
  override def isOpaqueCube = false
  override def renderAsNormalBlock = false
  override def getRenderType = -1

/*  override def setBlockBoundsBasedOnState(world: IBlockAccess, x: Int, y: Int, z: Int) {
    world.getBlockTileEntity(x, y, z) match {
      case te: TileEntityMovingStrip if te.parent != null =>
        import te.parent
        val pos = te - parent.dirTo
        val block = Block.blocksList(world.getBlockId(pos.x, pos.y, pos.z))
        if(parent.counter == 0 || block == null) {
          setBlockBounds(.5F, .5F, .5F, .5F, .5F, .5F)
        } else {
          val shift = (16 - parent.counter).toFloat / 16F
          setBlockBounds(
            (block.getBlockBoundsMinX - parent.dirTo.x * shift).toFloat,
            (block.getBlockBoundsMinY - parent.dirTo.y * shift).toFloat,
            (block.getBlockBoundsMinZ - parent.dirTo.z * shift).toFloat,
            (block.getBlockBoundsMaxX - parent.dirTo.x * shift).toFloat,
            (block.getBlockBoundsMaxY - parent.dirTo.y * shift).toFloat,
            (block.getBlockBoundsMaxZ - parent.dirTo.z * shift).toFloat)
        }
      case _ =>
    }
  }*/

  override def getCollisionBoundingBoxFromPool(world: World, x: Int, y: Int, z: Int) = {
    world.getBlockTileEntity(x, y, z) match {
      case te: TileEntityMovingStrip if te.parent != null => te.getAabb
      case _ => null
    }
  }
}

class TileEntityMovingStrip extends TileEntity {
  import rainwarrior.hooks.MovingRegistry
  lazy val side = EffectiveSide(worldObj)
  var parent: StripHolder = null

  //log.info(s"new TileEntityMovingStrip, pos: ${WorldPos(this)}")

  def getAabb() = {
    val pos = this - parent.dirTo
    val block = Block.blocksList(worldObj.getBlockId(pos.x, pos.y, pos.z))
    if(parent.counter == 0 || block == null) null
    else {
      val shift = (parent.counter + 1).toFloat / 16F
      block.getCollisionBoundingBoxFromPool(worldObj, pos.x, pos.y, pos.z) match {
        case aabb: AxisAlignedBB => 
          aabb.minX += shift * Math.min(0, parent.dirTo.x)
          aabb.minY += shift * Math.min(0, parent.dirTo.y)
          aabb.minZ += shift * Math.min(0, parent.dirTo.z)
          aabb.maxX += shift * Math.max(0, parent.dirTo.x)
          aabb.maxY += shift * Math.max(0, parent.dirTo.y)
          aabb.maxZ += shift * Math.max(0, parent.dirTo.z)
          //log.info(s"AABB: $aabb")
          aabb
        case _ => null
      }
    }
  }

  override def updateEntity() {
    val pos = WorldPos(this)
    //log.info(s"TileEntityMovingStrip onUpdate, side: $side, pos: $pos")
    if(parent == null) {
      worldObj.setBlock(pos.x, pos.y, pos.z, 0, 0, 3)
    } else {
      val aabb = getAabb
      val shift = 2F / 16F
      if(aabb != null) worldObj.getEntitiesWithinAABBExcludingEntity(null, aabb) match {
        case list: JList[_] => for(e <- list.asInstanceOf[JList[Entity]]) {
          e.moveEntity(
            shift * parent.dirTo.x,
            shift * parent.dirTo.y,
            shift * parent.dirTo.z)
        }
        case _ =>
      }
    }
  }
}


object StripData {
  def readFromNBT(cmp: NBTTagCompound) = {
    val x = cmp.getInteger("x")
    val y = cmp.getInteger("y")
    val z = cmp.getInteger("z")
    StripData(
      (x, y, z),
      ForgeDirection.values()(cmp.getInteger("dirTo")),
      cmp.getInteger("size"))
  }
}
case class StripData(pos: WorldPos, dirTo: ForgeDirection, size: Int) {
  def writeToNBT(cmp: NBTTagCompound) {
    cmp.setInteger("x", pos.x)
    cmp.setInteger("y", pos.y)
    cmp.setInteger("z", pos.z)
    cmp.setInteger("dirTo", dirTo.ordinal)
    cmp.setInteger("size", size)
  }
  def cycle(world: World) {
    val c = pos - dirTo * size
    world.getBlockTileEntity(pos.x, pos.y, pos.z) match {
      case te: TileEntityMovingStrip => te
      case te => 
        log.severe(s"Tried to cycle invalid TE: $te, $pos, ${EffectiveSide(world)}, id: ${world.getBlockId(pos.x, pos.y, pos.z)}")
        //Thread.dumpStack()
    }
    world.removeBlockTileEntity(pos.x, pos.y, pos.z)
    uncheckedSetBlock(world, pos.x, pos.y, pos.z, 0, 0)
    //world.setBlock(pos.x, pos.y, pos.z, 0, 0, 0)
    for(i <- 1 to size) {
      val c = pos - dirTo * i
      //log.info(s"c: $c")
      MovingTileRegistry.move(world, c.x, c.y, c.z, dirTo)
    }
  }
  def stopMoving(world: World) {
    for(i <- 0 to size) {
      MovingRegistry.delMoving(world, pos - dirTo * i)
    }
  }
  def notifyOfChanges(world: World) { // TODO: Optimise this
    for {
      i <- 0 to size
      c = pos - dirTo * i
      // id = world.getBlockId(c.x, c.y, c.z)
    } {
      //log.info(s"NOTIFY, pos: $c")
      world.notifyBlockOfNeighborChange(c.x, c.y, c.z, 0)
      world.notifyBlockChange(c.x, c.y, c.z, 0)
    }
  }
}

abstract class StripHolder extends TileEntity {
  private[this] var strips = HashSet.empty[StripData]
  var counter = 0
  var shouldUpdate = true
  val renderOffset = new BlockData(0, 0, 0, ForgeDirection.UNKNOWN)
  def dirTo: ForgeDirection

  def +=(s: StripData) {
    //log.info(s"+=strip: $s, client: ${worldObj.isClient}")
    strips += s
    renderOffset.dirTo = dirTo
    for(i <- 1 to s.size; c = s.pos - s.dirTo * i) {
      MovingRegistry.addMoving(worldObj, c, renderOffset)
    }
    //worldObj.markBlockForUpdate(xCoord, yCoord, zCoord)
  }

  def blocks() = { val d = dirTo; for(s <- strips; i <- 1 to s.size) yield s.pos - d * i }
  def allBlocks() = { val d = dirTo; for(s <- strips; i <- 0 to s.size) yield s.pos - d * i }

  def postMove() {
    //log.info(s"postMove, strips: ${strips.toString}, pos: ${WorldPos(this)}, side: ${EffectiveSide(worldObj)}")
    if(worldObj.isServer) {
      val players = worldObj.asInstanceOf[WorldServer].getPlayerManager.getOrCreateChunkWatcher(xCoord >> 4, zCoord >> 4, false)
      if(players != null) {
        // can update from 15 to 16
        val packet = new Packet132TileEntityData(xCoord, yCoord, zCoord, 3, null)
        players.sendToAllPlayersWatchingChunk(packet)
      }
    }
    //var t = System.currentTimeMillis
    for(s <- strips) s.cycle(worldObj)
    //println(s"1: ${System.currentTimeMillis - t}")
    //t = System.currentTimeMillis
    for(s <- strips) s.stopMoving(worldObj)
    //println(s"2: ${System.currentTimeMillis - t}")
    //t = System.currentTimeMillis
    fixScheduledTicks()
    //println(s"3: ${System.currentTimeMillis - t}")
    //t = System.currentTimeMillis
    for(s <- strips) s.notifyOfChanges(worldObj)
    //println(s"4: ${System.currentTimeMillis - t}")
    //t = System.currentTimeMillis
    notifyOfRenderChanges()
    //println(s"5: ${System.currentTimeMillis - t}")
    strips = HashSet.empty[StripData]
    renderOffset.x = 0
    renderOffset.y = 0
    renderOffset.z = 0
    renderOffset.dirTo = ForgeDirection.UNKNOWN
    counter = 0
    shouldUpdate = true
  }

  def fixScheduledTicks() {
    worldObj match {
      case world: WorldServer =>
        val hash = world.field_73064_N.asInstanceOf[JSet[NextTickListEntry]]
        val tree = world.pendingTickListEntries.asInstanceOf[JTreeSet[NextTickListEntry]]
        val list = world.field_94579_S.asInstanceOf[ArrayList[NextTickListEntry]]
        val isOptifine = world.getClass.getName == "WorldServerOF"
        val blocks = this.blocks().toSet
        val allBlocks = this.allBlocks()
        val chunkCoords =
          for(b <- allBlocks; x = b.x >> 4; z = b.z >> 4)
            yield (x, z)
        val chunks = (
          for {
            (x, z) <- chunkCoords
            ch = world.getChunkFromChunkCoords(x, z)
            if ch != null
          } yield ((x, z) -> world.getChunkFromChunkCoords(x, z))).toMap
        val scheduledTicks = (
          for {
            ch <- chunks.valuesIterator
            list = world.getPendingBlockUpdates(ch, !isOptifine).asInstanceOf[ArrayList[NextTickListEntry]]
            if list != null
            tick <- list
          } yield tick).toIterable
        //log.info(s"chunks: ${chunks.mkString}")
        //log.info(s"ticks: ${scheduledTicks.mkString}")
        if(isOptifine) {
          //log.info("Found Optifine")
          for (tick <- scheduledTicks) { // fix your hacks, Optifine! :P
            tree.remove(tick)
            hash.remove(tick)
            list.remove(tick)
          }
        }
        for(tick <- scheduledTicks if blocks((tick.xCoord, tick.yCoord, tick.zCoord))) {
          tick.xCoord += dirTo.x
          tick.yCoord += dirTo.y
          tick.zCoord += dirTo.z
        }
        //log.info(s"ticks: ${scheduledTicks.mkString}")
        for(tick <- scheduledTicks) {
          if(!hash.contains(tick)) {
            hash.add(tick)
            tree.add(tick)
          }
        }
      case _ =>
    }
  }

  def notifyOfRenderChanges() {
    // fix for WorldRenderer filtering TEs out
    if(worldObj.isClient) {
      /*val coords = for(s <- strips; i <- 0 to s.size) yield s.pos - dirTo * i
      val xs = coords.map(_.x)
      val ys = coords.map(_.y)
      val zs = coords.map(_.z)*/
      val teList = mc.renderGlobal.tileEntities.asInstanceOf[JList[TileEntity]]
      //var t = System.currentTimeMillis
      for(pass <- 0 to 1) {
        for {
          c <- allBlocks
          te = worldObj.getBlockTileEntity(c.x, c.y, c.z)
        } {
          //log.info(s"NOTIFY RENDER, pos: $c")
          if(te != null)  pass match {
            case 0 => teList.remove(te)
            case 1 => teList.add(te)
          }
          mc.renderGlobal.markBlockForRenderUpdate(c.x, c.y, c.z)
        }
        //mc.renderGlobal.markBlocksForUpdate(xs.min - 1, ys.min - 1, zs.min - 1, xs.max + 1, ys.max + 1, zs.max + 1)
        //println(s"Time1: ${System.currentTimeMillis - t}")
        //t = System.currentTimeMillis
        mc.renderGlobal.updateRenderers(mc.renderViewEntity, false)
        //println(s"Time2: ${System.currentTimeMillis - t}")
      }
    }
  }

  override def updateEntity() = if(!strips.isEmpty) {
    super.updateEntity()
    //log.info(s"stripHolder onUpdate, p: ${WorldPos(this)}, c: $counter, sd: ${EffectiveSide(worldObj)}")
    if(counter == 0 || shouldUpdate) {
      for(s <- strips) {
        worldObj.getBlockTileEntity(s.pos.x, s.pos.y, s.pos.z) match {
          case te: TileEntityMovingStrip => te.parent = this
          case _ =>
        }
      }
    }
    if(counter < 15 || (counter == 15 && worldObj.isServer)) {
      counter += 1
      val shift = (counter / 16F).toFloat
      renderOffset.x = dirTo.x * shift
      renderOffset.y = dirTo.y * shift
      renderOffset.z = dirTo.z * shift
      if(shouldUpdate) {
        //log.info(s"stripHolder marking, p: ${WorldPos(this)}, sd: ${EffectiveSide(worldObj)}")
        for(s <- strips; i <- 1 to s.size; c = s.pos - s.dirTo * i) {
          MovingRegistry.addMoving(worldObj, c, renderOffset)
          //mc.renderGlobal.markBlockForRenderUpdate(c.x, c.y, c.z)
        }
      }
    }
    shouldUpdate = false
    if(counter >= 16) postMove
  }

  override def onDataPacket(netManager: INetworkManager, packet: Packet132TileEntityData) {
    //log.info(s"Holder onDatapacket, type: ${packet.actionType}")
    packet.actionType match {
      case 3 =>
        counter = 16
        updateEntity()
      case _ => super.onDataPacket(netManager, packet)
    }
  }

  override def readFromNBT(cmp: NBTTagCompound) {
    super.readFromNBT(cmp)
    val list = cmp.getTagList("strips")
    strips = HashSet[StripData](
      (for(i <- 0 until list.tagCount) yield list.tagAt(i) match {
        case cmp1: NBTTagCompound =>
          val strip = StripData.readFromNBT(cmp1)
          strip
        case x => throw new RuntimeException(s"Invalid tag: $x")
      }): _*)
    counter = cmp.getInteger("counter")
    renderOffset.readFromNBT(cmp.getCompoundTag("renderOffset"))
    //log.info(s"StripHolder readFromNBT, pos: ${WorldPos(this)}, counter: $counter, side:" + FMLCommonHandler.instance.getEffectiveSide)
  }

  override def writeToNBT(cmp: NBTTagCompound) {
    super.writeToNBT(cmp)
    val stripList = new NBTTagList
    for(strip <- strips) {
      val cmp1 = new NBTTagCompound
      strip.writeToNBT(cmp1)
      stripList.appendTag(cmp1)
    }
    cmp.setTag("strips", stripList)
    cmp.setInteger("counter", counter)
    val cmp1 = new NBTTagCompound
    renderOffset.writeToNBT(cmp1)
    cmp.setTag("renderOffset", cmp1)
    //log.info(s"StripHolder writeToNBT, pos: ${WorldPos(this)}, counter: $counter, side:" + FMLCommonHandler.instance.getEffectiveSide)
  }
}

trait BlockFrame extends Block {
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

  //override def createNewTileEntity(world: World): TileEntity = null // new TileEntityFrame
  override def isOpaqueCube = false
  override def isBlockSolidOnSide(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = {
    true
  }
  override def renderAsNormalBlock = false
  //override def renderAsNormalBlock = false
  override def getRenderType = BlockFrameRenderer.getRenderId

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
}

object BlockFrameRenderer extends ISimpleBlockRenderingHandler {
  override def renderInventoryBlock(
      block: Block,
      metadata: Int,
      modelId: Int,
      rb: RenderBlocks) {
    //rainwarrior.utils.renderInventoryBlock(rb, block, metadata)
    RenderHelper.disableStandardItemLighting()
    tes.startDrawingQuads()
    tes.setColorOpaque_F(1, 1, 1)
    model.renderFrame()
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
    tes.setBrightness(CommonProxy.blockFrame.getMixedBrightnessForBlock(world, x, y, z))
    tes.setColorOpaque_F(1, 1, 1)
    tes.addTranslation(x + .5F, y + .5F, z + .5F)
    model.renderFrame()
    tes.addTranslation(-x - .5F, -y - .5F, -z - .5F)
    true
  }
  override val shouldRender3DInInventory = true
  override lazy val getRenderId = RenderingRegistry.getNextAvailableRenderId()
}
