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

import collection.immutable.Queue
import collection.mutable.{ HashMap => MHashMap, MultiMap, Set => MSet }
import net.minecraft._,
  block.{ Block, BlockContainer },
  block.material.Material,
  client.renderer.tileentity.TileEntitySpecialRenderer,
  client.renderer.Tessellator.{ instance => tes },
  client.renderer.{ OpenGlHelper, RenderHelper, RenderBlocks },
  creativetab.CreativeTabs,
  entity.player.EntityPlayer,
  nbt.NBTTagCompound,
  network.INetworkManager,
  network.packet.{ Packet, Packet132TileEntityData },
  tileentity.TileEntity,
  world.{ World, IBlockAccess, ChunkPosition, chunk },
  chunk.storage.ExtendedBlockStorage
import org.lwjgl.opengl.GL11._
import cpw.mods.fml.relauncher.{ SideOnly, Side}
import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.{ common, client, relauncher }
import common.{ Mod, event, network, registry, FMLCommonHandler, SidedProxy }
import network.NetworkMod
import registry.{ GameRegistry, LanguageRegistry, TickRegistry }
import client.registry.{ ClientRegistry, RenderingRegistry, ISimpleBlockRenderingHandler }
import relauncher.{ FMLRelaunchLog, Side }
import net.minecraftforge.common.{ MinecraftForge, ForgeDirection }
import Scalamod._
import util._


trait BlockMovingStrip extends BlockContainer {
  setHardness(.5f)
  setStepSound(Block.soundGravelFootstep)
  setUnlocalizedName("Scalamod:BlockMovingStrip")
  setCreativeTab(CreativeTabs.tabBlock)
//  setBlockBounds(.5F, .5F, .5F, .5F, .5F, .5F)

  import cpw.mods.fml.common.registry._
  LanguageRegistry.addName(this, "Moving Strip Block")
  GameRegistry.registerBlock(this, "Moving_Strip_Block")
  GameRegistry.registerTileEntity(classOf[TileEntityMovingStrip], "Moving_Strip_TileEntity")

  override def createNewTileEntity(world: World): TileEntity = new TileEntityMovingStrip
  override def isOpaqueCube = false
  override def renderAsNormalBlock = false
  override def getRenderType = -1

  override def setBlockBoundsBasedOnState(world: IBlockAccess, x: Int, y: Int, z: Int) {
    val metadata = world.getBlockMetadata(x, y, z)
    val te = world.getBlockTileEntity(x, y, z).asInstanceOf[TileEntityMovingStrip]
    if(metadata == 0 || te == null) {
//      setBlockBounds(.5F, .5F, .5F, .5F, .5F, .5F)
    }
    else
    {
      //x1, y1, z1 = world.getBlockId(x + te.
      //val old List(minX, maxX, minY, maxY, minZ, maxZ
      val shift: Float = (16 - te.blockMetadata).toFloat / 16F
      val pos = te - te.dirTo
      val block = Block.blocksList(world.getBlockId(pos.x, pos.y, pos.z))
      setBlockBounds(
        (block.getBlockBoundsMinX - te.dirTo.x * shift).toFloat,
        (block.getBlockBoundsMinY - te.dirTo.y * shift).toFloat,
        (block.getBlockBoundsMinZ - te.dirTo.z * shift).toFloat,
        (block.getBlockBoundsMaxX - te.dirTo.x * shift).toFloat,
        (block.getBlockBoundsMaxY - te.dirTo.y * shift).toFloat,
        (block.getBlockBoundsMaxZ - te.dirTo.z * shift).toFloat)
    }
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
    //log.info(f"onBlockActivated: ($x,$y,$z), isServer: $isServer")
    //if(world.isRemote) {
      //val te = world.getBlockTileEntity(x, y, z).asInstanceOf[TileEntityMovingStrip]
      //if(te == null)
        //throw new RuntimeException("no tile entity!")
//      FMLCommonHandler.instance.showGuiScreen(te.openGui())
    //}
    true
  }
  // should be called server-side
  def create(world: World, x: Int, y: Int, z: Int, dirTo: ForgeDirection, size: Int) {
    world.setBlock(x, y, z, blockID, 0, 3)
    val te = new TileEntityMovingStrip
    te.setWorldObj(world)
    te.xCoord = x
    te.yCoord = y
    te.zCoord = z
    te.dirTo = dirTo
    te.size = size
    world.setBlockTileEntity(x, y, z, te)
  }
}

class TileEntityMovingStrip extends TileEntity {
  lazy val side: EffectiveSide = worldObj
  var dirTo: ForgeDirection = ForgeDirection.UNKNOWN
  var size: Int = 0
  @SideOnly(Side.CLIENT)
  var renderOffset = MovingRegistry.debugOffset

  override def getDescriptionPacket(): Packet = {
    log.info(s"TileEntityMovingStrip getPacket, side: $side")

    assert(side.isServer)
    val cmp = new NBTTagCompound
    writeToNBT(cmp);
    new Packet132TileEntityData(xCoord, yCoord, zCoord, 255, cmp)
  }

  override def onDataPacket(netManager: INetworkManager, packet: Packet132TileEntityData) {
    log.info(s"TileEntityMovingStrip onPacket, side: $side")

    assert(side.isClient)
    readFromNBT(packet.customParam1)
  }

  override def readFromNBT(cmp: NBTTagCompound) {
    super.readFromNBT(cmp)
    dirTo = ForgeDirection.values()(cmp.getInteger("dirTo"))
    size = cmp.getInteger("size")
  }

  override def writeToNBT(cmp: NBTTagCompound) {
    super.writeToNBT(cmp);
    cmp.setInteger("dirTo", dirTo.ordinal)
    cmp.setInteger("size", size)
  }

  override def updateEntity() {
    if(WorldPos(dirTo) == WorldPos(ForgeDirection.UNKNOWN)) {
      worldObj.setBlock(xCoord, yCoord, zCoord, 0, 0, 3)
    }
    val meta = getBlockMetadata + 1
    log.info(s"TileEntityMovingStrip onUpdate, side: $side, meta: $meta, dirTo: $dirTo")
    if(meta < 16) {
      worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, meta, 1)
    }
    (meta, side) match {
      case (1, Client) =>
        renderOffset = new BlockData(0, 0, 0)
        for(i <- 1 to size) {
          MovingRegistry.addMoving(worldObj, this - dirTo * i, renderOffset)
        }
      case (16, _) =>
        if(side.isClient) for(i <- 1 to size) {
          MovingRegistry.delMoving(worldObj, this - dirTo * i)
        }
        for(i <- 0 to size) {
          val c2 = this - dirTo * i
          val c1: WorldPos = if(i != size) (c2 - dirTo) else this
          log.info(s"c1: $c1, c2: $c2")
          val (id, m, te) = if(i != size) (
            worldObj.getBlockId(c1.x, c1.y, c1.z),
            worldObj.getBlockMetadata(c1.x, c1.y, c1.z),
            worldObj.getBlockTileEntity(c1.x, c1.y, c1.z))
          else (
            CommonProxy.movingStripBlockId,
            16,
            this)
          val ch1 = worldObj.getChunkFromChunkCoords(c1.x >> 4, c1.z >> 4)
          val ch2 = worldObj.getChunkFromChunkCoords(c2.x >> 4, c2.z >> 4)
          if(te != null) {
            if(i != size) ch1.chunkTileEntityMap.remove(new ChunkPosition(c1.x & 0xF, c1.y, c1.z & 0xF))
            te.xCoord = c2.x
            te.yCoord = c2.y
            te.zCoord = c2.z
            ch2.chunkTileEntityMap.asInstanceOf[java.util.Map[ChunkPosition, TileEntity]].
              put(new ChunkPosition(c2.x & 0xF, c2.y, c2.z & 0xF), te)
          }
          def arr = ch2.getBlockStorageArray()
          if(arr(c2.y >> 4) == null)
            arr(c2.y >> 4) = new ExtendedBlockStorage(c2.y & (~0xF), !worldObj.provider.hasNoSky)
          arr(c2.y >> 4).setExtBlockID(c2.x & 0xF, c2.y & 0xF, c2.z & 0xF, id)
          arr(c2.y >> 4).setExtBlockMetadata(c2.x & 0xF, c2.y & 0xF, c2.z & 0xF, m)
          //worldObj.setBlock(c2.x, c2.y, c2.z, id, m, 0)
        }
        //val c = this - dirTo * (size)
        worldObj.setBlock(xCoord, yCoord, zCoord, 0, 0, 0)
      case (_, Client) =>
        val shift = (meta / 16F).toFloat
        renderOffset.x = dirTo.x * shift
        renderOffset.y = dirTo.y * shift
        renderOffset.z = dirTo.z * shift
      case (_, Server) =>
    }
  }
}


trait BlockFrame extends Block {
  setHardness(.5f)
  setStepSound(Block.soundGravelFootstep)
  setUnlocalizedName("Scalamod:BlockFrame")
  setCreativeTab(CreativeTabs.tabBlock)

  import cpw.mods.fml.common.registry._
  LanguageRegistry.addName(this, "Frame Block")
  GameRegistry.registerBlock(this, "Frame_Block")

  //override def createNewTileEntity(world: World): TileEntity = null // new TileEntityFrame
  override def isOpaqueCube = false
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

object FrameBfs {
  def apply(world: World, pos: WorldPos, dirTo: ForgeDirection) {
    val blocks = bfs(world, Queue(pos))
    val map = new MHashMap[Tuple2[Int, Int], MSet[Int]] with MultiMap[Tuple2[Int, Int], Int]
    for (c <- blocks) {
      //log.info(s"c: $c")
      map.addBinding(c.normal(dirTo), c.basis(dirTo))
    }

    val strips = for { // TODO: fix direction here
      normal <- map.keys
      strip = map(normal)
      size = strip.size
    } yield {
      val basis = if(dirTo.basis(dirTo) > 0) strip.max else strip.min
      val c = WorldPos(dirTo, normal, basis)
      log.info(s"xyz: $c, size: $size")
      (c + dirTo, size)
    }
    val canMove = strips.exists { (pair) =>
      val c = pair._1
      val id = world.getBlockId(c.x, c.y, c.z)
      val block = Block.blocksList(id)
      log.info(s"block: $block")
      if(block == null) true
      else
        block.isBlockReplaceable(world, c.x, c.y, c.z)
    }
    if (canMove) for ((c, size) <- strips) {
      log.info(s"c: $c")
      CommonProxy.blockMovingStrip.create(world, c.x, c.y, c.z, dirTo, size)
    }
      
  }
  def bfs(
      world: World,
      greyBlocks: Seq[WorldPos], // fames to visit
      blackBlocks: Set[WorldPos] = Set.empty): Set[WorldPos] = { // all blocks to move
    greyBlocks match {
      case Seq() => blackBlocks
      case Seq(next, rest@ _*) => world.getBlockId(next.x, next.y, next.z) match {
        case CommonProxy.blockFrameId =>
          val toCheck = for {
            dir <- ForgeDirection.VALID_DIRECTIONS.toList
            c = next + dir
            if !blackBlocks(c)
            if !greyBlocks.contains(c)
            id = world.getBlockId(c.x, c.y, c.z)
            if id != 0
          } yield c
          List("111",
            s"next: $next",
            s"rest: $rest",
            s"blocks: $blackBlocks",
            s"toCheck: $toCheck").map(log.info(_))
          bfs(world, rest ++ toCheck, blackBlocks + next)
        case _ =>
          List("222",
            s"next: $next",
            s"rest: $rest",
            s"blocks: $blackBlocks").map(log.info(_))
          bfs(world, rest, blackBlocks + next)
      }
    }
  }
}
object BlockFrameRenderer extends ISimpleBlockRenderingHandler {
  override def renderInventoryBlock(
      block: Block,
      metadata: Int,
      modelId: Int,
      rb: RenderBlocks) {
    rb.useInventoryTint = true
    block.setBlockBoundsForItemRender()
    rb.setRenderBoundsFromBlock(block)
    glPushMatrix()
    glRotatef(90, 0, 1, 0)
    glTranslatef(-.5F, -.5F, -.5F)
    tes.startDrawingQuads()
    tes.setNormal(0, -1, 0)
    rb.renderFaceYNeg(block, 0, 0, 0, rb.getBlockIconFromSideAndMetadata(block, 0, metadata))

    if (rb.useInventoryTint) {
      val c = block.getRenderColor(metadata)
      val r = c >> 16 & 0xFF
      val g = c >> 8 & 0xFF
      val b = c & 0xFF
      tes.setColorRGBA(r, g, b, 0xFF)
    }

    tes.setNormal(0, 1, 0)
    rb.renderFaceYPos(block, 0, 0, 0, rb.getBlockIconFromSideAndMetadata(block, 1, metadata))

    if (rb.useInventoryTint) {
      tes.setColorRGBA_F(1, 1, 1, 1)
    }

    tes.setNormal(0, 0, -1)
    rb.renderFaceZNeg(block, 0, 0, 0, rb.getBlockIconFromSideAndMetadata(block, 2, metadata))
    tes.setNormal(0, 0, 1)
    rb.renderFaceZPos(block, 0, 0, 0, rb.getBlockIconFromSideAndMetadata(block, 3, metadata))
    tes.setNormal(-1, 0, 0)
    rb.renderFaceXNeg(block, 0, 0, 0, rb.getBlockIconFromSideAndMetadata(block, 4, metadata))
    tes.setNormal(1, 0, 0)
    rb.renderFaceXPos(block, 0, 0, 0, rb.getBlockIconFromSideAndMetadata(block, 5, metadata))
    tes.draw()
    glPopMatrix()
  }
  override def renderWorldBlock(
      world: IBlockAccess,
      x: Int,
      y: Int,
      z: Int,
      block: Block,
      modelId: Int,
      rb: RenderBlocks) = {
    rb.setRenderBoundsFromBlock(block)
    rb.renderStandardBlock(block, x, y, z)
  }
  override val shouldRender3DInInventory = true
  override lazy val getRenderId = RenderingRegistry.getNextAvailableRenderId()
}
