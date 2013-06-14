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

package rainwarrior.trussmod

import java.util.{ List => JList }
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
  entity.player.EntityPlayer,
  nbt.{ NBTTagCompound, NBTTagList },
  network.INetworkManager,
  network.packet.{ Packet, Packet132TileEntityData },
  tileentity.TileEntity,
  world.{ EnumSkyBlock, World, IBlockAccess, ChunkPosition, chunk },
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
import rainwarrior.hooks.MovingRegistry


trait BlockMovingStrip extends Block/*Container*/ {
  setHardness(.5f)
  setStepSound(Block.soundGravelFootstep)
  setUnlocalizedName(modId + ":BlockMovingStrip")
  setCreativeTab(CreativeTabs.tabBlock)
//  setBlockBounds(.5F, .5F, .5F, .5F, .5F, .5F)

  import cpw.mods.fml.common.registry._
  LanguageRegistry.addName(this, "Moving Strip Block")
  GameRegistry.registerBlock(this, "Moving_Strip_Block")
  //GameRegistry.registerTileEntity(classOf[TileEntityMovingStrip], "Moving_Strip_TileEntity")

  /*override def createNewTileEntity(world: World): TileEntity = {
    log.severe("Called createNewTileEntity")
    //throw new RuntimeException("Called createNewTileEntity")
    new TileEntityMovingStrip
  }
  override def createTileEntity(world: World, meta: Int): TileEntity = meta match {
    case _ => new TileEntityMovingStrip
    //case _ => null
  }*/
  override def isOpaqueCube = false
  override def renderAsNormalBlock = false
  override def getRenderType = -1

  /*override def setBlockBoundsBasedOnState(world: IBlockAccess, x: Int, y: Int, z: Int) {
    val metadata = world.getBlockMetadata(x, y, z)
    world.getBlockTileEntity(x, y, z).asInstanceOf[TileEntityMovingStrip] match {
      case te: TileEntityMovingStrip =>
        val pos = te - te.dirTo
        val block = Block.blocksList(world.getBlockId(pos.x, pos.y, pos.z))
        if(metadata == 0 || te == null || block == null) {
          //setBlockBounds(.5F, .5F, .5F, .5F, .5F, .5F)
        } else {
          //x1, y1, z1 = world.getBlockId(x + te.
          //val old List(minX, maxX, minY, maxY, minZ, maxZ
          val shift: Float = (16 - te.blockMetadata).toFloat / 16F
          setBlockBounds(
            (block.getBlockBoundsMinX - te.dirTo.x * shift).toFloat,
            (block.getBlockBoundsMinY - te.dirTo.y * shift).toFloat,
            (block.getBlockBoundsMinZ - te.dirTo.z * shift).toFloat,
            (block.getBlockBoundsMaxX - te.dirTo.x * shift).toFloat,
            (block.getBlockBoundsMaxY - te.dirTo.y * shift).toFloat,
            (block.getBlockBoundsMaxZ - te.dirTo.z * shift).toFloat)
        }
      case _ =>
    }
  }*/

  /*override def onBlockActivated(
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
  }*/
  // should be called server-side
  /*def create(world: World, parent: StripHolder, x: Int, y: Int, z: Int, dirTo: ForgeDirection, size: Int) {
    world.setBlock(x, y, z, blockID, 0, 0)
    world.getBlockTileEntity(x, y, z) match {
      case te: TileEntityMovingStrip =>
        te.parent = Some(parent)
        //te.setWorldObj(world)
        //te.xCoord = x
        //te.yCoord = y
        //te.zCoord = z
        te.dirTo = dirTo
        te.size = size
      case _ =>
        log.severe(s"Failed to create tilentity: ($x,$y,$z)")
        throw new RuntimeException(s"Failed to create tilentity: ($x,$y,$z)")
    }
  }*/
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
    //val c = pos + dirTo * size
    /*val strip = world.getBlockTileEntity(pos.x, pos.y, pos.z) match {
      case te: TileEntityMovingStrip => te
      case te => throw new RuntimeException(s"Tried to cycle invalid TE: $te, $pos")
    }*/
    for(i <- 0 to size) {
      val c2 = pos - dirTo * i
      val c1 = if(i != size) (c2 - dirTo) else pos
      log.info(s"c1: $c1, c2: $c2")
      val (id, m, te) = if(i != size) (
        world.getBlockId(c1.x, c1.y, c1.z),
        world.getBlockMetadata(c1.x, c1.y, c1.z),
        world.getBlockTileEntity(c1.x, c1.y, c1.z))
      else (
        CommonProxy.blockMovingStripId,
        16,
        null)
      val ch1 = world.getChunkFromChunkCoords(c1.x >> 4, c1.z >> 4)
      val ch2 = world.getChunkFromChunkCoords(c2.x >> 4, c2.z >> 4)
      val arr1 = ch1.getBlockStorageArray()
      var arr2 = ch2.getBlockStorageArray()
      if(te != null) {
        //te.invalidate()
        if(i != size) {
          ch1.chunkTileEntityMap.remove(new ChunkPosition(c1.x & 0xF, c1.y, c1.z & 0xF))
          
          if(arr1(c1.y >> 4) == null)
            arr1(c1.y >> 4) = new ExtendedBlockStorage(c1.y & (~0xF), !world.provider.hasNoSky)
          arr1(c1.y >> 4).setExtBlockID(c1.x & 0xF, c1.y & 0xF, c1.z & 0xF, 0)
          arr1(c1.y >> 4).setExtBlockMetadata(c1.x & 0xF, c1.y & 0xF, c1.z & 0xF, 0)
        }
        te.xCoord = c2.x
        te.yCoord = c2.y
        te.zCoord = c2.z
        ch2.chunkTileEntityMap.asInstanceOf[java.util.Map[ChunkPosition, TileEntity]].
          put(new ChunkPosition(c2.x & 0xF, c2.y, c2.z & 0xF), te)
        //te.validate()
      }
      if(arr2(c2.y >> 4) == null)
        arr2(c2.y >> 4) = new ExtendedBlockStorage(c2.y & (~0xF), !world.provider.hasNoSky)
      arr2(c2.y >> 4).setExtBlockID(c2.x & 0xF, c2.y & 0xF, c2.z & 0xF, id)
      arr2(c2.y >> 4).setExtBlockMetadata(c2.x & 0xF, c2.y & 0xF, c2.z & 0xF, m)
    }
    val c = pos - dirTo * size
    world.setBlock(c.x, c.y, c.z, 0, 0, 0)
  }
  def stopMoving(world: World) {
    for(i <- 0 to size) {
      MovingRegistry.delMoving(world, pos - dirTo * i)
    }
  }
  def notifyOfChanges(world: World) {
    for {
      i <- 0 to size
      c = pos - dirTo * i
      // id = world.getBlockId(c.x, c.y, c.z)
    } {
      log.info(s"NOTIFY, pos: $c")
      world.notifyBlockOfNeighborChange(c.x, c.y, c.z, 0)
      //world.notifyBlockChange(c.x, c.y, c.z, 0)
    }
  }
  def notifyOfRenderChanges(world: World) {
    // fix for WorldRenderer filtering TEs out
    if(world.isRemote) for(i <- 0 to 1) {
      for {
        i <- 0 to size
        c = pos - dirTo * i
        te = world.getBlockTileEntity(c.x, c.y, c.z)
      } {
        log.info(s"NOTIFY RENDER, pos: $c")
        if(te != null) {
          mc.renderGlobal.tileEntities.asInstanceOf[JList[TileEntity]].add(te)
        }
        mc.renderGlobal.markBlockForRenderUpdate(c.x, c.y, c.z)
      }
      mc.renderGlobal.updateRenderers(mc.renderViewEntity, false)
    }
  }
}

trait StripHolder extends TileEntity {
  private[this] var strips = HashSet.empty[StripData]
  private[this] var stripsLeft = HashSet.empty[StripData]
  var counter = 0
  val renderOffset = new BlockData(0, 0, 0)
  def dirTo: ForgeDirection
  def +=(s: StripData) {
    log.info(s"+=strip: $s, client: ${worldObj.isClient}")
    strips += s
    stripsLeft += s
    for(i <- 1 to s.size; c = s.pos - s.dirTo * i) {
      MovingRegistry.addMoving(worldObj, c, renderOffset)
    }
    worldObj.markBlockForUpdate(xCoord, yCoord, zCoord)
  }
  def -=(s: StripData) {
    log.info(s"-=strip: $s, client: ${worldObj.isClient}")
    stripsLeft -= s
    if(stripsLeft.isEmpty && counter >= 16) postMove
  }
  def postMove() {
    log.info(s"postMove, strips: ${strips.toString}, side: ${EffectiveSide(worldObj)}")
    for(s <- strips) s.cycle(worldObj)
    for(s <- strips) s.stopMoving(worldObj)
    for(s <- strips) s.notifyOfChanges(worldObj)
    for(s <- strips) s.notifyOfRenderChanges(worldObj)
    strips = HashSet.empty[StripData]
    stripsLeft = HashSet.empty[StripData]
    renderOffset.x = 0
    renderOffset.y = 0
    renderOffset.z = 0
    counter = 0
  }
  abstract override def updateEntity() = if(!strips.isEmpty) {
    super.updateEntity()
    log.info(s"stripHolder onUpdate, counter: $counter, renderOffset: $renderOffset")
    if(counter == 0) {
      for(s <- strips; i <- 1 to s.size; c = s.pos - s.dirTo * i) {
        MovingRegistry.addMoving(worldObj, c, renderOffset)
      }
    }
    if(counter < 16) {
      counter += 1
      val shift = (counter / 16F).toFloat
      renderOffset.x = dirTo.x * shift
      renderOffset.y = dirTo.y * shift
      renderOffset.z = dirTo.z * shift
    }
    if(counter >= 16) postMove
  }
  abstract override def readFromNBT(cmp: NBTTagCompound) {
    super.readFromNBT(cmp)
    strips = HashSet[StripData](
      (for(cmp1 <- cmp.getTagList("strips").tagList.toSeq) yield cmp1 match {
        case cmp1: NBTTagCompound => StripData.readFromNBT(cmp1)
        case x => throw new RuntimeException(s"Invalid tag: $x")
      }): _*)
    stripsLeft = HashSet[StripData](
      (for(cmp1 <- cmp.getTagList("stripsLeft").tagList.toSeq) yield cmp1 match {
        case cmp1: NBTTagCompound => StripData.readFromNBT(cmp1)
        case x => throw new RuntimeException(s"Invalid tag: $x")
      }): _*)
    counter = cmp.getInteger("counter")
    renderOffset.readFromNBT(cmp.getCompoundTag("renderOffset"))
  }
  abstract override def writeToNBT(cmp: NBTTagCompound) {
    super.writeToNBT(cmp)
    val stripList = new NBTTagList
    for(strip <- strips) {
      val cmp1 = new NBTTagCompound
      strip.writeToNBT(cmp1)
      stripList.appendTag(cmp1)
    }
    cmp.setTag("strips", stripList)
    val stripLeftList = new NBTTagList
    for(strip <- stripsLeft) {
      val cmp1 = new NBTTagCompound
      strip.writeToNBT(cmp1)
      stripLeftList.appendTag(cmp1)
    }
    cmp.setTag("stripsLeft", stripLeftList)
    cmp.setInteger("counter", counter)
    val cmp1 = new NBTTagCompound
    renderOffset.writeToNBT(cmp1)
    cmp.setTag("renderOffset", cmp1)
  }
}

/*class TileEntityMovingStrip extends TileEntity {
  import rainwarrior.hooks.MovingRegistry
  lazy val side = EffectiveSide(worldObj)
  var parent: Option[WorldPos] = None
  var dirTo: ForgeDirection = ForgeDirection.UNKNOWN
  var size: Int = 0

  log.info(s"new TileEntityMovingStrip, pos: ${WorldPos(this)}")

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
    val x = cmp.getInteger("parent.x")
    val y = cmp.getInteger("parent.y")
    val z = cmp.getInteger("parent.z")
    parent = Some((x, y, z))
    preParent()
  }

  override def writeToNBT(cmp: NBTTagCompound) {
    super.writeToNBT(cmp);
    cmp.setInteger("dirTo", dirTo.ordinal)
    cmp.setInteger("size", size)
    parent match {
      case Some(p) =>
        cmp.setInteger("parent.x", p.x)
        cmp.setInteger("parent.y", p.y)
        cmp.setInteger("parent.z", p.z)
      case None => throw new RuntimeException(s"writeToNBT with null parent")
    }
  }

  override def updateEntity() {
    val meta = getBlockMetadata + 1
    val pos = WorldPos(this)
    log.info(s"TileEntityMovingStrip onUpdate, side: $side, meta: $meta, pos: $pos, dirTo: $dirTo")
    if(WorldPos(dirTo) == WorldPos(ForgeDirection.UNKNOWN)) {
      worldObj.setBlock(xCoord, yCoord, zCoord, 0, 0, 3)
      invalidate()
      return
    }
    if(meta < 16) {
      if(!worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, meta, 1)) {
        log.severe("Cannot set metadata!")
        throw new RuntimeException("Cannot set metadata!")
      }
      blockMetadata = meta
    }
    (meta, side) match {
      case (1, _) =>
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord)
      case (16, _) =>
        invalidate()
        //worldObj.setBlock(xCoord, yCoord, zCoord, 0, 0, 0)
      case (_, _) =>
    }
  }
  override def validate() = if(tileEntityInvalid) {
    super.validate()
    log.info(s"TileEntityMovingStrip validate, pos: ${WorldPos(this)}, side: ${EffectiveSide(worldObj)}")
  }
  def preParent() {
    log.info(s"TileEntityMovingStrip preParent, pos: ${WorldPos(this)}")
    parent match {
      case Some(p) => worldObj.getBlockTileEntity(p.x, p.y, p.z) match {
        case te: StripHolder => te += StripData(this, dirTo, size)
        case te => throw new RuntimeException(s"preParent with invalid parent: $te")
      }
      case None => throw new RuntimeException(s"preParent with no parent")
    }   
  }
  override def invalidate() = if(!tileEntityInvalid) {
    super.invalidate()
    log.info(s"TileEntityMovingStrip invalidate, pos: ${WorldPos(this)}, side: ${EffectiveSide(worldObj)}")
    postParent()
    //worldObj.removeBlockTileEntity(xCoord, yCoord, zCoord)
    //worldObj.setBlock(xCoord, yCoord, zCoord, 0, 0, 3)
  }
  def postParent() {
    log.info(s"TileEntityMovingStrip postParent, pos: ${WorldPos(this)}")
    parent match {
      case Some(p) => worldObj.getBlockTileEntity(p.x, p.y, p.z) match {
        case te: StripHolder => te -= StripData(this, dirTo, size)
        case te => // throw new RuntimeException(s"postParent with invalid parent: $te")
      }
      case None => // throw new RuntimeException(s"postParent with no parent")
    }
  }
}*/


trait BlockFrame extends Block {
  setHardness(.5f)
  setStepSound(Block.soundGravelFootstep)
  setUnlocalizedName(modId + ":BlockFrame")
  setCreativeTab(CreativeTabs.tabBlock)

  import cpw.mods.fml.common.registry._
  LanguageRegistry.addName(this, "Frame Block")
  GameRegistry.registerBlock(this, "Frame_Block")

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
    rainwarrior.utils.renderInventoryBlock(rb, block, metadata)
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
