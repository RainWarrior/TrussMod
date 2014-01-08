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
import rainwarrior.serial._
import Serial._
import rainwarrior.hooks.{ MovingRegistry, MovingTileRegistry }


class BlockMovingStrip(id: Int, material: Material) extends BlockContainer(id, material) {
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
    Option(world.getBlockTileEntity(x, y, z)).collect {
      case te: TileEntityMovingStrip if !te.parentPos.isEmpty =>
        Option((te.worldObj.getBlockTileEntity _).tupled(te.parentPos.get.toTuple)).map(p => (te, p))
    }.flatten.collect {
      case (te, parent: StripHolderTile) =>
        val pos = te - parent.dirTo
        val block = Block.blocksList(world.getBlockId(pos.x, pos.y, pos.z))
        if(parent.stripHolder.offset == 0 || block == null) {
          setBlockBounds(.5F, .5F, .5F, .5F, .5F, .5F)
        } else {
          val shift = (16 - parent.stripHolder.offset).toFloat / 16F
          //log.info(s"SBBBOS: ${te.worldObj.isClient}, ${(block.getBlockBoundsMaxY - parent.dirTo.y * shift).toFloat}")
          setBlockBounds(
            (block.getBlockBoundsMinX - parent.dirTo.x * shift).toFloat,
            (block.getBlockBoundsMinY - parent.dirTo.y * shift).toFloat,
            (block.getBlockBoundsMinZ - parent.dirTo.z * shift).toFloat,
            (block.getBlockBoundsMaxX - parent.dirTo.x * shift).toFloat,
            (block.getBlockBoundsMaxY - parent.dirTo.y * shift).toFloat,
            (block.getBlockBoundsMaxZ - parent.dirTo.z * shift).toFloat)
        }
    }
  }

  override def getCollisionBoundingBoxFromPool(world: World, x: Int, y: Int, z: Int) = {
    setBlockBoundsBasedOnState(world, x, y, z)
    Option(world.getBlockTileEntity(x, y, z)).collect {
      case te: TileEntityMovingStrip if !te.parentPos.isEmpty =>
        Option((te.worldObj.getBlockTileEntity _).tupled(te.parentPos.get.toTuple))
    }.flatten.collect {
      case te: StripHolderTile =>
        val aabb = te.stripHolder.getAabb((x, y, z))
        //log.info(s"GCBBFP: ${te.worldObj.isClient}, ${aabb.maxY}")
        aabb
    }.orNull
  }
}

final class TileEntityMovingStrip extends TileEntity with SimpleSerialTile[TileEntityMovingStrip] {
  var parentPos: Option[WorldPos] = None

  def channel = TrussMod.tileChannel

  implicit def Repr = TileEntityMovingStrip.serialInstance

  //log.info(s"new TileEntityMovingStrip, pos: ${WorldPos(this)}")
  override def updateEntity() {
    //log.info(s"update TileEntityMovingStrip: ${worldObj.isClient}")
    if(!parentPos.isEmpty) (worldObj.getBlockTileEntity _).tupled(parentPos.get.toTuple) match {
      case parent: StripHolderTile =>
      case _ => worldObj.setBlock(xCoord, yCoord, zCoord, 0, 0, 3)
    } else worldObj.setBlock(xCoord, yCoord, zCoord, 0, 0, 3)
  }
}
object TileEntityMovingStrip {
  implicit object serialInstance extends IsCopySerial[TileEntityMovingStrip] {
    def pickle[F: IsSerialSink](te: TileEntityMovingStrip): F = {
      val (x, y, z) = te.parentPos match {
        case Some(pos) => pos.toTuple
        case None => (0, -10, 0)
      }
      /*F.toSerialMap(
        s("parentX") -> s(x),
        s("parentY") -> s(y),
        s("parentZ") -> s(z)
      )*/
      P.list(P(x), P(y), P(z))
    }
    def unpickle[F: IsSerialSource](f: F): TileEntityMovingStrip = {
      //val map = F.fromSerialMap(f).map{ case (k, v) => F.fromSerial[String](k) -> F.fromSerial[Int](v) }.toMap
      val Seq(x, y, z) = P.unList(f).map(c => P.unpickle[F, Int](c))
      val te = new TileEntityMovingStrip
      //te.parentPos = Some((map("parentX"), map("parentY"), map("parentZ")))
      te.parentPos = Some((x, y, z))
      te
    }
    def copy(from: TileEntityMovingStrip, to: TileEntityMovingStrip): Unit = {
      to.parentPos = from.parentPos
    }
  }
}
  
object StripData {
  implicit object serialInstance extends IsSerializable[StripData] {
    def pickle[F: IsSerialSink](t: StripData): F = {
      P.list(P(t.pos.x), P(t.pos.y), P(t.pos.z), P(t.dirTo.ordinal), P(t.size))
    }
    def unpickle[F: IsSerialSource](f: F): StripData = {
      val Seq(x, y, z, dirTo, size) = P.unList(f).map(c => P.unpickle[F, Int](c))
      StripData((x, y, z), ForgeDirection.values()(dirTo), size)
    }
  }
}
case class StripData(pos: WorldPos, dirTo: ForgeDirection, size: Int) {
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
  def postCycle(world: World) {
    for(i <- 0 until size) {
      val c = pos - dirTo * i
      CommonProxy.movingTileHandler.postMove(world, c.x, c.y, c.z)
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

trait StripHolderTile extends TileEntity {
  def dirTo: ForgeDirection
  def shouldContinue: Boolean
  def stripHolder: StripHolder
}

class StripHolder(
    var parent: StripHolderTile,
    private var strips: Set[StripData] = HashSet.empty[StripData],
    var isMoving: Boolean = false,
    var offset: Int = 0,
    val renderOffset: BlockData = new BlockData(0, 0, 0, ForgeDirection.UNKNOWN)
) {

  def +=(s: StripData) {
    //log.info(s"+=strip: $s, client: ${worldObj.isClient}")
    strips += s
    renderOffset.dirTo = parent.dirTo
    /*for(i <- 1 to s.size; c = s.pos - s.dirTo * i) {
      MovingRegistry.addMoving(worldObj, c, renderOffset)
    }*/
    //worldObj.markBlockForUpdate(xCoord, yCoord, zCoord)
  }

  def blocks() = { val d = parent.dirTo; for(s <- strips; i <- 1 to s.size) yield s.pos - d * i }
  def allBlocks() = { val d = parent.dirTo; for(s <- strips; i <- 0 to s.size) yield s.pos - d * i }

  def postMove() {
    //log.info(s"postMove, strips: ${strips.toString}, pos: ${WorldPos(this)}, side: ${EffectiveSide(worldObj)}")
    /*if(parent.worldObj.isServer) { // TODO send update packet
      val players = parent.worldObj.asInstanceOf[WorldServer].getPlayerManager.getOrCreateChunkWatcher(parent.xCoord >> 4, parent.zCoord >> 4, false)
      if(players != null) {
        val packet = parent.getDescriptionPacket
        packet.asInstanceOf[Packet132TileEntityData].actionType = 2
        players.sendToAllPlayersWatchingChunk(packet)
      }
    }*/
    //var t = System.currentTimeMillis
    for(s <- strips) s.cycle(parent.worldObj)
    //println(s"1: ${System.currentTimeMillis - t}")
    //t = System.currentTimeMillis
    for(s <- strips) s.postCycle(parent.worldObj)
    //println(s"1: ${System.currentTimeMillis - t}")
    //t = System.currentTimeMillis
    for(s <- strips) s.stopMoving(parent.worldObj)
    //println(s"2: ${System.currentTimeMillis - t}")
    //t = System.currentTimeMillis
    fixScheduledTicks()
    //println(s"3: ${System.currentTimeMillis - t}")
    //t = System.currentTimeMillis
    for(s <- strips) s.notifyOfChanges(parent.worldObj)
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
    parent.worldObj match {
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
          tick.xCoord += parent.dirTo.x
          tick.yCoord += parent.dirTo.y
          tick.zCoord += parent.dirTo.z
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
    if(parent.worldObj.isClient) {
      /*val coords = for(s <- strips; i <- 0 to s.size) yield s.pos - dirTo * i
      val xs = coords.map(_.x)
      val ys = coords.map(_.y)
      val zs = coords.map(_.z)*/
      val teList = mc.renderGlobal.tileEntities.asInstanceOf[JList[TileEntity]]
      //var t = System.currentTimeMillis
      for(pass <- 0 to 1) {
        for {
          c <- allBlocks
          te = parent.worldObj.getBlockTileEntity(c.x, c.y, c.z)
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
    if(parent.worldObj != null) for(s <- strips; i <- 1 to s.size; c = s.pos - s.dirTo * i) {
      MovingRegistry.addMoving(parent.worldObj, c, renderOffset)
      //mc.renderGlobal.markBlockForRenderUpdate(c.x, c.y, c.z)
    }
  }

  def update() {
    //log.info(s"stripHolder onUpdate, p: ${WorldPos(this)}, m: $isMoving, o: $offset, dirTo: $dirTo, sd: ${EffectiveSide(worldObj)}, ro: $renderOffset")
    if(isServer) {
      if(offset >= 16) {
        pushEntities()
        postMove()
        isMoving = false
        offset = 0
      }
      if(offset == 0 && parent.shouldContinue) {
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
      val dirTo = this.parent.dirTo

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
      if(aabb != null) parent.worldObj.getEntitiesWithinAABBExcludingEntity(null, aabb) match {
        case list: JList[_] => for(e <- list.asInstanceOf[JList[Entity]]) {
          //log.info(s"Yup, ${dirTo}, $e")
          //e.isAirBorne = true
          e.moveEntity(
            sh2 * parent.dirTo.x,
            sh2 * parent.dirTo.y,
            sh2 * parent.dirTo.z)
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
    val pos = spos - parent.dirTo
    val block = Block.blocksList(parent.worldObj.getBlockId(pos.x, pos.y, pos.z))
    if(!isMoving || block == null) {
      null
    } else {
      val shift = offset.toFloat / 16F
      block.getCollisionBoundingBoxFromPool(parent.worldObj, pos.x, pos.y, pos.z) match {
        case aabb: AxisAlignedBB => 
          val dirTo = parent.dirTo
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

  // TODO catch update packet

  /*override def onDataPacket(netManager: INetworkManager, packet: Packet132TileEntityData) {
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
  }*/
}
object StripHolder {
  implicit object serialInstance extends IsSerializable[StripHolder] {
    def pickle[F: IsSerialSink](t: StripHolder): F = {
      val strips = P.list(t.strips.toSeq.map(s => P(s)): _*)
      P.list(
        strips,
        P(t.isMoving),
        P(t.offset),
        P(t.renderOffset)
      )
    }
    def unpickle[F](f: F)(implicit F: IsSerialSource[F]): StripHolder = {
      val Seq(strips, isMoving, offset, renderOffset) = P.unList(f)
      new StripHolder(
        null,
        P.unList(strips).map(P.unpickle[F, StripData]).to[HashSet],
        P.unpickle[F, Boolean](isMoving),
        P.unpickle[F, Int](offset),
        P.unpickle[F, BlockData](renderOffset)
      )
    }
  }
}

