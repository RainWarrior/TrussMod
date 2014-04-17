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
import java.nio.ByteBuffer
import collection.immutable.{ HashSet, Queue }
import collection.mutable.{ HashMap => MHashMap, MultiMap, Set => MSet, HashSet => MHashSet }
import collection.JavaConversions._
import net.minecraft._,
  block.{ Block, BlockContainer },
  block.material.Material,
  client.Minecraft.{ getMinecraft => mc },
  client.renderer.tileentity.TileEntitySpecialRenderer,
  client.renderer.Tessellator.{ instance => tes },
  client.renderer.texture.IIconRegister,
  client.renderer.{ OpenGlHelper, RenderHelper, RenderBlocks },
  creativetab.CreativeTabs,
  entity.Entity,
  entity.player.EntityPlayer,
  init.Blocks,
  item.{ Item, ItemStack },
  nbt.{ NBTTagCompound, NBTTagList },
  network.NetworkManager,
  tileentity.TileEntity,
  util.AxisAlignedBB,
  world.{ ChunkPosition, chunk, EnumSkyBlock, IBlockAccess, NextTickListEntry, World, WorldServer },
  chunk.storage.ExtendedBlockStorage
import org.lwjgl.opengl.GL11._
import cpw.mods.fml.relauncher.{ SideOnly, Side }
import cpw.mods.fml.{ common, client, relauncher }
import common.{ Mod, event, network, registry, FMLCommonHandler, ObfuscationReflectionHelper }
import network.{ NetworkRegistry }
import registry.{ GameRegistry, LanguageRegistry }
import client.registry.{ ClientRegistry, RenderingRegistry, ISimpleBlockRenderingHandler }
import relauncher.{ FMLRelaunchLog, Side }
import net.minecraftforge.common.util.ForgeDirection
import TrussMod._
import rainwarrior.utils._
import rainwarrior.serial._
import Serial._
import rainwarrior.hooks.{ MovingRegistry, MovingTileRegistry }


class BlockMovingStrip(material: Material) extends BlockContainer(material) {
  setHardness(-1F)
  setStepSound(Block.soundTypeGravel)
  setBlockName(modId + ":BlockMovingStrip")
  setCreativeTab(null)
  //setBlockBounds(.5F, .5F, .5F, .5F, .5F, .5F)

  import cpw.mods.fml.common.registry._
  //LanguageRegistry.addName(this, "Moving Strip Block")
  GameRegistry.registerBlock(this, "Moving_Strip_Block")
  GameRegistry.registerTileEntity(classOf[TileEntityMovingStrip], "Moving_Strip_TileEntity")

  @SideOnly(Side.CLIENT)
  override def registerBlockIcons(registry: IIconRegister) {}

  override def createNewTileEntity(world: World, meta: Int) = new TileEntityMovingStrip(None)
  override def isOpaqueCube = false
  override def renderAsNormalBlock = false
  override def getRenderType = -1

  override def setBlockBoundsBasedOnState(world: IBlockAccess, x: Int, y: Int, z: Int) {
    Option(world.getTileEntity(x, y, z)).collect {
      case te: TileEntityMovingStrip if !te.parentPos.isEmpty =>
        Option((te.getWorldObj.getTileEntity _).tupled(te.parentPos.get.toTuple)).map(p => (te, p))
    }.flatten.collect {
      case (te, parent: StripHolderTile) =>
        val pos = te - parent.dirTo
        val block = world.getBlock(pos.x, pos.y, pos.z)
        if(parent.offset == 0 || block == null) {
          setBlockBounds(.5F, .5F, .5F, .5F, .5F, .5F)
        } else {
          val shift = (16 - parent.offset).toFloat / 16F
          //log.info(s"SBBBOS: ${te.getWorldObj.isClient}, ${(block.getBlockBoundsMaxY - parent.dirTo.y * shift).toFloat}")
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
    Option(world.getTileEntity(x, y, z)).collect {
      case te: TileEntityMovingStrip if !te.parentPos.isEmpty =>
        Option((te.getWorldObj.getTileEntity _).tupled(te.parentPos.get.toTuple))
    }.flatten.collect {
      case te: StripHolderTile =>
        val aabb = te.getAabb((x, y, z))
        //log.info(s"GCBBFP: ${te.getWorldObj.isClient}, ${aabb.maxY}")
        aabb
    }.orNull
  }
}

final class TileEntityMovingStrip(
    var parentPos: Option[WorldPos] = None
  ) extends SimpleSerialTile[TileEntityMovingStrip] {

  final def channel = tileChannel
  final def Repr = TileEntityMovingStrip.serialInstance

  override def updateEntity() {
    if(!parentPos.isEmpty) (worldObj.getTileEntity _).tupled(parentPos.get.toTuple) match {
      case parent: StripHolderTile =>
      case _ => worldObj.setBlock(xCoord, yCoord, zCoord, Blocks.air, 0, 3)
    } else worldObj.setBlock(xCoord, yCoord, zCoord, Blocks.air, 0, 3)
  }
}

object TileEntityMovingStrip {
  implicit object serialInstance extends IsMutableSerial[TileEntityMovingStrip] {
    def pickle[F: IsSerialSink](b: TileEntityMovingStrip): F = {
      val (x, y, z) = b.parentPos match {
        case Some(pos) => pos.toTuple
        case None => (0, -10, 0)
      }
      P.list(P(x), P(y), P(z))
    }
    def unpickle[F: IsSerialSource](t: TileEntityMovingStrip, f: F): Unit = {
      val Seq(x, y, z) = P.unList(f).map(c => P.unpickle[F, Int](c))
      t.parentPos = Some((x, y, z))
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
/*    world.getTileEntity(pos.x, pos.y, pos.z) match {
      case te: TileEntityMovingStrip => te
      case te => 
        log.severe(s"Tried to cycle invalid TE: $te, $pos, ${EffectiveSide(world)}, id: ${world.getBlockId(pos.x, pos.y, pos.z)}")
        //Thread.dumpStack()
    }*/
    world.removeTileEntity(pos.x, pos.y, pos.z)
    uncheckedSetBlock(world, pos.x, pos.y, pos.z, Blocks.air, 0)
    //world.setBlock(pos.x, pos.y, pos.z, 0, 0, 0)
    for(i <- 1 to size) {
      val c = pos - dirTo * i
      //log.info(s"c: $c")
      MovingTileRegistry.rootHandler.move(world, c.x, c.y, c.z, dirTo)
    }
  }
  def postCycle(world: World) {
    for(i <- 0 until size) {
      val c = pos - dirTo * i
      MovingTileRegistry.rootHandler.postMove(world, c.x, c.y, c.z)
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
        world.notifyBlockChange(c.x + d.x, c.y + d.y, c.z + d.z, Blocks.air)
      }
    }
  }
}

trait StripHolderTile extends TileEntity {
  private var strips: Set[StripData] = HashSet.empty[StripData]
  var isMoving: Boolean = false
  var offset: Int = 0
  var renderOffset: BlockData = new BlockData(0, 0, 0, ForgeDirection.UNKNOWN)

  StateHandler

  def dirTo: ForgeDirection
  def shouldContinue: Boolean

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
    if(getWorldObj.isServer) {
      implicit val vi = SerialFormats.vectorSerialInstance
      sendToPlayersWatchingChunk(
        StateHandler.channels.get(Side.SERVER),
        getWorldObj.asInstanceOf[WorldServer],
        xCoord >> 4,
        zCoord >> 4,
        P.list(
          P(xCoord),
          P(yCoord),
          P(zCoord)
        )
      )
    }
    //var t = System.currentTimeMillis
    for(s <- strips) s.cycle(getWorldObj)
    //println(s"1: ${System.currentTimeMillis - t}")
    //t = System.currentTimeMillis
    for(s <- strips) s.postCycle(getWorldObj)
    //println(s"1: ${System.currentTimeMillis - t}")
    //t = System.currentTimeMillis
    for(s <- strips) s.stopMoving(getWorldObj)
    //println(s"2: ${System.currentTimeMillis - t}")
    //t = System.currentTimeMillis
    fixScheduledTicks()
    //println(s"3: ${System.currentTimeMillis - t}")
    //t = System.currentTimeMillis
    for(s <- strips) s.notifyOfChanges(getWorldObj)
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
    getWorldObj match {
      case world: WorldServer =>
        val hash = ObfuscationReflectionHelper.getPrivateValue(
          classOf[WorldServer],
          world,
          "field_73064_N",
          "pendingTickListEntriesHashSet"
        ).asInstanceOf[JSet[NextTickListEntry]]
        val tree = ObfuscationReflectionHelper.getPrivateValue(
          classOf[WorldServer],
          world,
          "field_73065_O",
          "pendingTickListEntriesTreeSet"
        ).asInstanceOf[JTreeSet[NextTickListEntry]]
        val list = ObfuscationReflectionHelper.getPrivateValue(
          classOf[WorldServer],
          world,
          "field_94579_S",
          "pendingTickListEntriesThisTick"
        ).asInstanceOf[ArrayList[NextTickListEntry]]
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
    if(getWorldObj.isClient) {
      /*val coords = for(s <- strips; i <- 0 to s.size) yield s.pos - dirTo * i
      val xs = coords.map(_.x)
      val ys = coords.map(_.y)
      val zs = coords.map(_.z)*/
      val teList = mc.renderGlobal.tileEntities.asInstanceOf[JList[TileEntity]]
      //var t = System.currentTimeMillis
      for(pass <- 0 to 1) {
        for {
          c <- allBlocks
          te = getWorldObj.getTileEntity(c.x, c.y, c.z)
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
    //log.info(s"stripHolder marking, p: ${WorldPos(parent)}, sd: ${EffectiveSide(getWorldObj)}")
    if(getWorldObj != null) for(s <- strips; i <- 1 to s.size; c = s.pos - s.dirTo * i) {
      MovingRegistry.addMoving(getWorldObj, c, renderOffset)
      //mc.renderGlobal.markBlockForRenderUpdate(c.x, c.y, c.z)
    }
  }

  abstract override def updateEntity() {
    super.updateEntity()
    //log.info(s"stripHolder onUpdate, p: ${WorldPos(this)}, m: $isMoving, o: $offset, dirTo: $dirTo, sd: ${EffectiveSide(worldObj)}, ro: $renderOffset")
    if(getWorldObj.isServer) {
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
        worldObj.getTileEntity(s.pos.x, s.pos.y, s.pos.z) match {
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
    //val sh2 = (1F + 2F / 16F) / 16F
    val sh2 = (1F / 16F)
    for (s <- strips) {
      val aabb = getAabb(s.pos)
      //log.info(s"Yup2, ${worldObj.isClient}, $aabb")
      if(aabb != null) getWorldObj.getEntitiesWithinAABBExcludingEntity(null, aabb) match {
        case list: JList[_] => for(e <- list.asInstanceOf[JList[Entity]]) {
          //log.info(s"Yup, ${dirTo}, $e")
          //e.isAirBorne = true
          /*e.moveEntity(
            sh2 * dirTo.x,
            sh2 * dirTo.y,
            sh2 * dirTo.z)*/
          e.moveEntity(0, (sh2 * dirTo.y) max 0, 0)
          e.addVelocity(
            sh2 * dirTo.x,
            sh2 * dirTo.y,
            sh2 * dirTo.z)
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
    val block = getWorldObj.getBlock(pos.x, pos.y, pos.z)
    if(!isMoving || block == null) {
      null
    } else {
      val shift = offset.toFloat / 16F
      block.getCollisionBoundingBoxFromPool(getWorldObj, pos.x, pos.y, pos.z) match {
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

  def clientPostMove(): Unit = {
    postMove()
    isMoving = false
    offset = 0
  }
}
object StripHolderTile {
  implicit object serialInstance extends IsMutableSerial[StripHolderTile] {
    def pickle[F: IsSerialSink](t: StripHolderTile): F = {
      val strips = P.list(t.strips.toSeq.map(s => P(s)): _*)
      P.list(
        strips,
        P(t.isMoving),
        P(t.offset),
        P(t.renderOffset)
      )
    }
    def unpickle[F](t: StripHolderTile, f: F)(implicit F: IsSerialSource[F]): Unit = {
      val Seq(strips, isMoving, offset, renderOffset) = P.unList(f)
      t.strips = P.unList(strips).map(P.unpickle[F, StripData]).to[HashSet]
      t.isMoving = P.unpickle[F, Boolean](isMoving)
      t.offset = P.unpickle[F, Int](offset)
      t.renderOffset = P.unpickle[F, BlockData](renderOffset)
      if(t.isMoving) t.preMove()
    }
  }
}

import io.netty.channel.{ ChannelHandler, ChannelHandlerContext, SimpleChannelInboundHandler }

@ChannelHandler.Sharable
object StateHandler extends SimpleChannelInboundHandler[Vector[Byte]] {
  final val channel = modId + ":Move"
  val channels = NetworkRegistry.INSTANCE.newChannel(channel, VectorCodec, this)
  override def channelRead0(ctx: ChannelHandlerContext, msg: Vector[Byte]): Unit = {
    implicit val vi = SerialFormats.vectorSerialInstance
    val world = getPlayer(ctx).worldObj
    val Seq(x, y, z) = P.unList(msg).map(P.unpickle[Vector[Byte], Int])
    world.getTileEntity(x, y, z) match {
      case te: StripHolderTile => te.clientPostMove()
      case _ => log.fatal(s"stray packet for coords: ($x, $y, $z)")
    }
  }
}
