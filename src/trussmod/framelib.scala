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

import java.util.{ List => JList, ArrayList, EnumSet, Set => JSet, TreeSet => JTreeSet }
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
import common.{ Mod, event, ITickHandler, network, registry, FMLCommonHandler, TickType, SidedProxy }
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
  //setBlockBounds(.5F, .5F, .5F, .5F, .5F, .5F)

  import cpw.mods.fml.common.registry._
  LanguageRegistry.addName(this, "Moving Strip Block")
  GameRegistry.registerBlock(this, "Moving_Strip_Block")
  GameRegistry.registerTileEntity(classOf[TileEntityMovingStrip], "Moving_Strip_TileEntity")

  @SideOnly(Side.CLIENT)
  override def registerIcons(registry: IconRegister) {}

  override def createNewTileEntity(world: World) = new TileEntityMovingStrip
  override def isOpaqueCube = false
  override def renderAsNormalBlock = false
  override def getRenderType = -1

  override def setBlockBoundsBasedOnState(world: IBlockAccess, x: Int, y: Int, z: Int) {
    world.getBlockTileEntity(x, y, z) match {
      case te: TileEntityMovingStrip if !te.parentPos.isEmpty =>
        (te.worldObj.getBlockTileEntity _).tupled(te.parentPos.get.toTuple) match {
          case parent: StripHolder =>
            val pos = te - parent.dirTo
            val block = Block.blocksList(world.getBlockId(pos.x, pos.y, pos.z))
            if(parent.offset == 0 || block == null) {
              setBlockBounds(.5F, .5F, .5F, .5F, .5F, .5F)
            } else {
              val shift = (16 - parent.offset).toFloat / 16F
              //log.info(s"SBBBOS: ${te.worldObj.isClient}, ${(block.getBlockBoundsMaxY - parent.dirTo.y * shift).toFloat}")
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
      case _ =>
    }
  }

  override def getCollisionBoundingBoxFromPool(world: World, x: Int, y: Int, z: Int) = {
    setBlockBoundsBasedOnState(world, x, y, z)
    world.getBlockTileEntity(x, y, z) match {
      case te: TileEntityMovingStrip if !te.parentPos.isEmpty =>
        (te.worldObj.getBlockTileEntity _).tupled(te.parentPos.get.toTuple) match {
          case te: StripHolder =>
            val aabb = te.getAabb((x, y, z))
            //log.info(s"GCBBFP: ${te.worldObj.isClient}, ${aabb.maxY}")
            aabb
          case _ => null
        }
      case _ => null
    }
  }
}

class TileEntityMovingStrip extends TileEntity {
  var parentPos: Option[WorldPos] = None

  //log.info(s"new TileEntityMovingStrip, pos: ${WorldPos(this)}")
  override def updateEntity() {
    //log.info(s"update TileEntityMovingStrip: ${worldObj.isClient}")
    if(!parentPos.isEmpty) (worldObj.getBlockTileEntity _).tupled(parentPos.get.toTuple) match {
      case parent: StripHolder =>
      case _ => worldObj.setBlock(xCoord, yCoord, zCoord, 0, 0, 3)
    } else worldObj.setBlock(xCoord, yCoord, zCoord, 0, 0, 3)
  }
  override def getDescriptionPacket(): Packet = {
    val cmp = new NBTTagCompound
    writeToNBT(cmp)
    new Packet132TileEntityData(xCoord, yCoord, zCoord, 1, cmp)
  }

  override def onDataPacket(netManager: INetworkManager, packet: Packet132TileEntityData) {
    readFromNBT(packet.data)
  }

  override def readFromNBT(cmp: NBTTagCompound) {
    super.readFromNBT(cmp)
    val x = cmp.getInteger("parentX")
    val y = cmp.getInteger("parentY")
    val z = cmp.getInteger("parentZ")
    parentPos = Some((x, y, z))
  }

  override def writeToNBT(cmp: NBTTagCompound) {
    super.writeToNBT(cmp)
    parentPos match {
      case Some(pos) =>
        cmp.setInteger("parentX", pos.x)
        cmp.setInteger("parentY", pos.y)
        cmp.setInteger("parentZ", pos.z)
      case None =>
        cmp.setInteger("parentX", 0)
        cmp.setInteger("parentY", -10)
        cmp.setInteger("parentZ", 0)
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
    if(pos.y < 0 || pos.y >= 256) return
/*    world.getBlockTileEntity(pos.x, pos.y, pos.z) match {
      case te: TileEntityMovingStrip => te
      case te => 
        log.severe(s"Tried to cycle invalid TE: $te, $pos, ${EffectiveSide(world)}, id: ${world.getBlockId(pos.x, pos.y, pos.z)}")
        //Thread.dumpStack()
    }*/
    world.removeBlockTileEntity(pos.x, pos.y, pos.z)
    uncheckedSetBlock(world, pos.x, pos.y, pos.z, 0, 0)
    //world.setBlock(pos.x, pos.y, pos.z, 0, 0, 0)
    for(i <- 1 to size) {
      val c = pos - dirTo * i
      //log.info(s"c: $c")
      CommonProxy.movingTileHandler.move(world, c.x, c.y, c.z, dirTo)
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
      //world.notifyBlockOfNeighborChange(c.x, c.y, c.z, 0)
      for(d <- ForgeDirection.values()) {
        world.notifyBlockChange(c.x + d.x, c.y + d.y, c.z + d.z, 0)
      }
    }
  }
}

abstract class StripHolder extends TileEntity {
  def dirTo: ForgeDirection
  def shouldContinue: Boolean

  private[this] var strips = HashSet.empty[StripData]
  var isMoving: Boolean = false
  var offset = 0
  val renderOffset = new BlockData(0, 0, 0, ForgeDirection.UNKNOWN)

  def +=(s: StripData) {
    //log.info(s"+=strip: $s, client: ${worldObj.isClient}")
    strips += s
    renderOffset.dirTo = dirTo
    /*for(i <- 1 to s.size; c = s.pos - s.dirTo * i) {
      MovingRegistry.addMoving(worldObj, c, renderOffset)
    }*/
    //worldObj.markBlockForUpdate(xCoord, yCoord, zCoord)
  }

  def blocks() = { val d = dirTo; for(s <- strips; i <- 1 to s.size) yield s.pos - d * i }
  def allBlocks() = { val d = dirTo; for(s <- strips; i <- 0 to s.size) yield s.pos - d * i }

  def postMove() {
    //log.info(s"postMove, strips: ${strips.toString}, pos: ${WorldPos(this)}, side: ${EffectiveSide(worldObj)}")
    if(worldObj.isServer) {
      val players = worldObj.asInstanceOf[WorldServer].getPlayerManager.getOrCreateChunkWatcher(xCoord >> 4, zCoord >> 4, false)
      if(players != null) {
        val packet = getDescriptionPacket
        packet.asInstanceOf[Packet132TileEntityData].actionType = 2
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
  }

  def fixScheduledTicks() {
    worldObj match {
      case world: WorldServer =>
        val hash = world.pendingTickListEntriesHashSet.asInstanceOf[JSet[NextTickListEntry]]
        val tree = world.pendingTickListEntriesTreeSet.asInstanceOf[JTreeSet[NextTickListEntry]]
        val list = world.pendingTickListEntriesThisTick.asInstanceOf[ArrayList[NextTickListEntry]]
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

  def preMove() {
    //log.info(s"stripHolder marking, p: ${WorldPos(this)}, sd: ${EffectiveSide(worldObj)}")
    if(worldObj != null) for(s <- strips; i <- 1 to s.size; c = s.pos - s.dirTo * i) {
      MovingRegistry.addMoving(worldObj, c, renderOffset)
      //mc.renderGlobal.markBlockForRenderUpdate(c.x, c.y, c.z)
    }
  }

  override def updateEntity() {
    super.updateEntity()
    //log.info(s"stripHolder onUpdate, p: ${WorldPos(this)}, m: $isMoving, o: $offset, dirTo: $dirTo, sd: ${EffectiveSide(worldObj)}, ro: $renderOffset")
    if(isServer) {
      if(offset >= 16) {
        pushEntities()
        postMove()
        isMoving = false
        offset = 0
      }
      if(offset == 0 && shouldContinue) {
        //isMoving = true
        preMove()
      }
    }
      /*for(s <- strips) {
        worldObj.getBlockTileEntity(s.pos.x, s.pos.y, s.pos.z) match {
          case te: TileEntityMovingStrip => te.parent = this
          case _ =>
        }
      }*/
    if(isMoving && offset <= 16) {
      val dirTo = this.dirTo

      offset = (offset + 1)
      val shift = ((offset).toFloat / 0x10)
      renderOffset.x = dirTo.x * shift
      renderOffset.y = dirTo.y * shift
      renderOffset.z = dirTo.z * shift
    }
    pushEntities()
  }

  def pushEntities() {
    val sh2 = (1F + 2F / 16F) / 16F
    for (s <- strips) {
      val aabb = getAabb(s.pos)
      //log.info(s"Yup2, ${worldObj.isClient}, $aabb")
      if(aabb != null) worldObj.getEntitiesWithinAABBExcludingEntity(null, aabb) match {
        case list: JList[_] => for(e <- list.asInstanceOf[JList[Entity]]) {
          //log.info(s"Yup, ${dirTo}, $e")
          //e.isAirBorne = true
          e.moveEntity(
            sh2 * dirTo.x,
            sh2 * dirTo.y,
            sh2 * dirTo.z)
          /*e.addVelocity(
            sh2 * dirTo.x,
            sh2 * dirTo.y,
            sh2 * dirTo.z)*/
          /*e.posX += sh2 * dirTo.x
          e.posY += sh2 * dirTo.y
          e.posZ += sh2 * dirTo.z*/
        }
        case _ =>
      }
    }
  }

  def getAabb(spos: WorldPos) = {
    val pos = spos - dirTo
    val block = Block.blocksList(worldObj.getBlockId(pos.x, pos.y, pos.z))
    if(!isMoving || block == null) {
      null
    } else {
      val shift = offset.toFloat / 16F
      block.getCollisionBoundingBoxFromPool(worldObj, pos.x, pos.y, pos.z) match {
        case aabb: AxisAlignedBB => 
          aabb.minX += shift * Math.min(0, dirTo.x)
          aabb.minY += shift * Math.min(0, dirTo.y)
          aabb.minZ += shift * Math.min(0, dirTo.z)
          aabb.maxX += shift * Math.max(0, dirTo.x)
          aabb.maxY += shift * Math.max(0, dirTo.y)
          aabb.maxZ += shift * Math.max(0, dirTo.z)
          //log.info(s"AABB: $aabb")
          aabb
        case _ => null
      }
    }
  }

  override def getDescriptionPacket(): Packet = {
    val cmp = new NBTTagCompound
    writeToNBT(cmp)
    //log.info("getPacket, world: " + worldObj)
    new Packet132TileEntityData(xCoord, yCoord, zCoord, 1, cmp)
  }

  override def onDataPacket(netManager: INetworkManager, packet: Packet132TileEntityData) {
    //log.info(s"Holder onDatapacket, type: ${packet.actionType}")
    //log.info(s"onDataPacket, ($xCoord, $yCoord, $zCoord), ${packet.actionType}")
    packet.actionType match {
      case 1 => readFromNBT(packet.data)
      case 2 =>
        readFromNBT(packet.data)
        postMove()
        isMoving = false
        offset = 0
    }
    super.onDataPacket(netManager, packet)
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
    if(cmp.hasKey("counter")) {
      throw new IllegalStateException(s"Save data incompatible with current version of $modId")
    }
    isMoving = cmp.getBoolean("isMoving")
    offset = cmp.getInteger("offset")
    renderOffset.readFromNBT(cmp.getCompoundTag("renderOffset"))
    if(isMoving) preMove()
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
    cmp.setBoolean("isMoving", isMoving)
    cmp.setInteger("offset", offset)
    val cmp1 = new NBTTagCompound
    renderOffset.writeToNBT(cmp1)
    cmp.setTag("renderOffset", cmp1)
    //log.info(s"StripHolder writeToNBT, pos: ${WorldPos(this)}, counter: $counter, side:" + FMLCommonHandler.instance.getEffectiveSide)
  }
}

