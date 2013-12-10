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

import annotation.tailrec
import collection.immutable.Queue
import collection.mutable.{ HashMap => MHashMap, MultiMap, Set => MSet }
import net.minecraft._,
  block.{ Block, BlockContainer },
  block.material.Material,
  client.renderer.tileentity.TileEntitySpecialRenderer,
  client.renderer.Tessellator.{ instance => tes },
  client.renderer.texture.{ IconRegister, TextureMap },
  client.renderer.{ OpenGlHelper, RenderHelper, RenderBlocks },
  creativetab.CreativeTabs,
  entity.player.EntityPlayer,
  item.{ Item, ItemStack },
  nbt.NBTTagCompound,
  network.INetworkManager,
  network.packet.{ Packet, Packet132TileEntityData },
  tileentity.TileEntity,
  world.{ IBlockAccess, World, WorldServer }
import net.minecraft.util.Icon
import org.lwjgl.opengl.GL11._
import cpw.mods.fml.relauncher.{ SideOnly, Side}
import cpw.mods.fml.common.{ FMLCommonHandler, Loader, Optional }
import cpw.mods.fml.client.registry.{ RenderingRegistry, ISimpleBlockRenderingHandler }
import net.minecraftforge.common.{ MinecraftForge, ForgeDirection }
import TrussMod._
import rainwarrior.utils._
import rainwarrior.hooks.{ MovingRegistry, MovingTileRegistry }
trait TraitMotor extends BlockContainer {
  setHardness(5f)
  setResistance(10f)
  setStepSound(Block.soundMetalFootstep)
  setUnlocalizedName(s"$modId:BlockMotor")
  setCreativeTab(CreativeTabs.tabBlock)

  var renderType = -1
  import cpw.mods.fml.common.registry._
  LanguageRegistry.addName(this, "Motor Block")
  net.minecraftforge.common.MinecraftForge.setBlockHarvestLevel(this, "shovel", 0)
  GameRegistry.registerBlock(this, "Motor_Block")
  GameRegistry.registerTileEntity(classOf[TileEntityMotor], "Motor_TileEntity");
  {
    val motor = new ItemStack(this)
    val frame = new ItemStack(CommonProxy.frameBlock)
    val redstone = new ItemStack(Block.blockRedstone)
    val iron = new ItemStack(Item.ingotIron)
    GameRegistry.addRecipe(
      motor,
      "fif",
      "iri",
      "fif",
      Char.box('r'), redstone,
      Char.box('i'), iron,
      Char.box('f'), frame)
  }

  @SideOnly(Side.CLIENT)
  override def registerIcons(registry: IconRegister) {}

  override def createNewTileEntity(world: World): TileEntity = new TileEntityMotor
  override def isOpaqueCube = false
  override def isBlockSolidOnSide(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = {
    side.ordinal != world.getBlockMetadata(x, y, z)
  }
  override def renderAsNormalBlock = false

  override def getRenderType = renderType

  def rotate(vec: Int, dir: Int, count: Int): Int = count match {
    case 0 => vec
    case _ => rotate(ForgeDirection.ROTATION_MATRIX(dir)(vec), dir, count - 1)
  }

  /*@SideOnly(Side.CLIENT)
  override def getBlockTexture(world: IBlockAccess, x: Int, y: Int, z: Int, side: Int) = {
    world.getBlockTileEntity(x, y, z) match {
      case te: TileEntityMotor =>
        val meta = te.getBlockMetadata
        val or = te.orientation
        val s2 = (side + meta) % 6 // + (if(meta > side) 6 else 0)
        val s3 = rotate(s2, meta, or)
        //log.info(s"meta: $meta, or: $or, s2: $s2, s3: $s3")
        //iconArray(iconMap(tmp(meta)(or)(side)))
        iconArray(iconMap(side))
      case _ => iconArray(0)
    }
  }*/
  override def getIcon(side: Int, metadata: Int) = {
    model.getIcon("block", "MotorFrame")
  }

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
    //log.info(f"onBlockActivated: ($x,$y,$z), isServer: $isServer")
    val te = world.getBlockTileEntity(x, y, z).asInstanceOf[TileEntityMotor]
    if(te == null)
      throw new RuntimeException("no tile entity!")
//    FMLCommonHandler.instance.showGuiScreen(te.openGui())
      if(!te.isMoving) te.rotate(player.isSneaking())
    true
  }

/*  override def onNeighborBlockChange(world: World, x: Int, y: Int, z: Int, id: Int) {
    if(world.isServer) {
      if(world.isBlockIndirectlyGettingPowered(x, y, z)) {
        world.scheduleBlockUpdate(x, y, z, this.blockID, 1)
      }
    }
  }

  override def updateTick(world: World, x: Int, y: Int, z: Int, random: java.util.Random) {
    world.getBlockTileEntity(x, y, z) match {
      case te: TileEntityMotor if(te.moving == 0) =>
        te.activate()
      case _ =>
    }
  }*/
}

class BlockMotor(id: Int)
  extends BlockContainer(id, Material.iron)
  with TraitMotor

import Power._

@Optional.InterfaceList(Array(
  new Optional.Interface(iface = CIPowerReceptor, modid = bcid),
  //new Optional.Interface(iface = CBuildcraftPowerReceptor, modid = bcid),
  new Optional.Interface(iface = CIEnergyHandler, modid = cofhid),
  //new Optional.Interface(iface = CCofhEnergyHandler, modid = cofhid),
  new Optional.Interface(iface = CIEnergySink, modid = icid)
))
class TileEntityMotor extends StripHolder with PowerTile {
  val powerSize = 32000

  lazy val side = EffectiveSide(worldObj)
  var orientation = 0
  //log.info(s"new TileEntityMotor, isServer: $isServer")

  override def readFromNBT(cmp: NBTTagCompound) {
    super.readFromNBT(cmp)
    orientation = cmp.getInteger("orientation")
    //log.info(s"Motor readFromNBT: ($xCoord,$yCoord,$zCoord), " + (if(worldObj != null) side else "NONE"))
  }

  override def writeToNBT(cmp: NBTTagCompound) {
    super.writeToNBT(cmp)
    cmp.setInteger("orientation", orientation)
    //log.info(s"Motor writeToNBT: ($xCoord,$yCoord,$zCoord), $side")
  }

  override def updateEntity() {
    super.updateEntity()
    //log.info(s"updateEntity, ($xCoord, $yCoord, $zCoord), ${worldObj.isClient}")
  }
  
  override def shouldRefresh(oldId: Int, newId: Int, oldMeta: Int, newMeta: Int, world: World, x: Int, y: Int, z: Int) = {
    if(oldId == newId) false else true
  }

  def rotate(isSneaking: Boolean) {
    if(isSneaking) {
      val meta = getBlockMetadata() match {
        case 5 => 0
        case _ => getBlockMetadata() + 1
      }
      //log.info(s"rotated1, m: $getBlockMetadata, meta: $meta, o: $orientation")
      worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, meta, 1)
      //log.info(s"rotated1, m: $getBlockMetadata, meta: $meta, o: $orientation")
    } else {
      orientation = orientation match {
        case 3 => 0
        case o => o + 1
      }
      //log.info(s"rotated2, m: $getBlockMetadata, o: $orientation")
    }
    worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord)
  }

  override def dirTo = ForgeDirection.values()(moveDir(orientation)(getBlockMetadata))

  override def shouldContinue: Boolean = {
    //var t = System.currentTimeMillis

    if(!worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord)) return false

    val meta = getBlockMetadata
    val pos = WorldPos(this) + ForgeDirection.values()(meta)
    //log.info(s"shouldUpdate! meta: $meta, pos: $pos, dirTo: $dirTo, side: ${EffectiveSide(worldObj)}")
    val id = worldObj.getBlockId(pos.x, pos.y, pos.z)
    if ( id == 0
      || MovingRegistry.isMoving(this.worldObj, pos.x, pos.y, pos.z)
      || !CommonProxy.movingTileHandler.canMove(this.worldObj, pos.x, pos.y, pos.z)
    ) return false

    //log.info(s"Activated! meta: $meta, pos: $pos, dirTo: $dirTo, side: ${EffectiveSide(worldObj)}")
    val blocks = bfs(Queue(pos))
    val map = new MHashMap[Tuple2[Int, Int], MSet[Int]] with MultiMap[Tuple2[Int, Int], Int]
    for (c <- blocks) {
      //log.info(s"c: $c")
      map.addBinding(c.normal(dirTo), c.basis(dirTo))
    }

    val shift = if((dirTo.ordinal & 1) == 1) 1 else -1
    var size = 0
    val strips = for {
      normal <- map.keys
      line = map(normal).toArray
      sline = if(shift == 1) line.sorted else line.sorted(Ordering[Int].reverse)
      (basis, size) <- splitLine(sline, shift)
      c = WorldPos(dirTo, normal, basis + shift)
    } yield {
      //log.info(s"xyz: $c, basis: $basis, size: $size, shift: $shift")
      (c, size)
    }

    val canMove = (blocks.size <= CommonProxy.structureLimit) && !strips.exists { (pair) =>
      val c = pair._1
      if(c.y < 0 || c.y >= 256) true
      else {
        val id = worldObj.getBlockId(c.x, c.y, c.z)
        val block = Block.blocksList(id)
        //log.info(s"block: $block")
        if(block == null) false
        else
          !block.isBlockReplaceable(worldObj, c.x, c.y, c.z)
      }
    }

    if(canMove) {
      isMoving = true
      for ((c, size) <- strips) {
        //log.info(s"c: $c")
        //CommonProxy.blockMovingStrip.create(worldObj, this, c.x, c.y, c.z, dirTo, size)
        worldObj.setBlock(c.x, c.y, c.z, CommonProxy.blockMovingStripId, 0, 3)
        worldObj.getBlockTileEntity(c.x, c.y, c.z) match {
          case te: TileEntityMovingStrip => te.parentPos = Some(this)
          case _ =>
        }
        worldObj.markBlockForUpdate(c.x, c.y, c.z)
        this += StripData(c, dirTo, size)
      }
      val manager = worldObj.asInstanceOf[WorldServer].getPlayerManager
      for {
        players <- Option(manager.getOrCreateChunkWatcher(xCoord >> 4, zCoord >> 4, false))
      } {
        players.sendToAllPlayersWatchingChunk(getDescriptionPacket)
      }
    }
    //println(s"Motor activation took: ${System.currentTimeMillis - t}")
    //worldObj.markBlockForUpdate(xCoord, yCoord, zCoord) 
    canMove
  }

  @tailrec private def bfs(
      greyBlocks: Seq[WorldPos], // frames to visit
      blackBlocks: Set[WorldPos] = Set.empty): Set[WorldPos] = { // all blocks to move
    greyBlocks match {
      case _ if blackBlocks.size > CommonProxy.structureLimit => blackBlocks
      case Seq() => blackBlocks
      case Seq(next, rest@ _*) => Block.blocksList(worldObj.getBlockId(next.x, next.y, next.z)) match {
        case block: Block =>
          val toCheck = for { // TODO: prettify
            dir <- ForgeDirection.VALID_DIRECTIONS.toList
            if (
              (block.isInstanceOf[Frame]
                && block.asInstanceOf[Frame].isSideSticky(worldObj, next.x, next.y, next.z, dir))
              || {
                val te = worldObj.getBlockTileEntity(next.x, next.y, next.z)
                (te.isInstanceOf[Frame]
                && te.asInstanceOf[Frame].isSideSticky(worldObj, next.x, next.y, next.z, dir))
              } || MovingTileRegistry.stickyHook(worldObj, next.x, next.y, next.z, dir))
            c = next + dir
            if !(c == WorldPos(this))
            if !blackBlocks(c)
            if !greyBlocks.contains(c)
            if !(MovingRegistry.isMoving(this.worldObj, c.x, c.y, c.z))
            if !worldObj.isAirBlock(c.x, c.y, c.z)
            if CommonProxy.movingTileHandler.canMove(this.worldObj, c.x, c.y, c.z)
            /*if !(id == CommonProxy.blockMotorId && {
              val meta = worldObj.getBlockMetadata(c.x, c.y, c.z)
              c == next - ForgeDirection.values()(meta)
            })*/
          } yield c
          /*List("111",
            s"next: $next",
            s"rest: $rest",
            s"blocks: $blackBlocks",
            s"toCheck: $toCheck").map(log.info(_))*/
          bfs(rest ++ toCheck, blackBlocks + next)
        case _ =>
          /*List("222",
            s"next: $next",
            s"rest: $rest",
            s"blocks: $blackBlocks").map(log.info(_))*/
          bfs(rest, blackBlocks + next)
      }
    }
  }
}

@SideOnly(Side.CLIENT)
object TileEntityMotorRenderer extends TileEntitySpecialRenderer {
  var rb: RenderBlocks = null
  override def onWorldChange(world: World) {
    rb = new RenderBlocks(world)
  }

  def addSquareX(x: Double, y1: Double, y2: Double, z1: Double, z2: Double) {
    tes.addVertex(x, y1, z1);
    tes.addVertex(x, y2, z1);
    tes.addVertex(x, y2, z2);
    tes.addVertex(x, y1, z2);
  }

  def addSquareY(x1: Double, x2: Double, y: Double, z1: Double, z2: Double) {
    tes.addVertex(x1, y, z1);
    tes.addVertex(x1, y, z2);
    tes.addVertex(x2, y, z2);
    tes.addVertex(x2, y, z1);
  }

  def addSquareZ(x1: Double, x2: Double, y1: Double, y2: Double, z: Double) {
    tes.addVertex(x1, y1, z);
    tes.addVertex(x2, y1, z);
    tes.addVertex(x2, y2, z);
    tes.addVertex(x1, y2, z);
  }

  override def renderTileEntityAt(tile: TileEntity, x: Double, y: Double, z: Double, partialTick: Float) {
    import net.minecraft.client.Minecraft.{ getMinecraft => mc }
    //val rb = mc.renderGlobal.globalRenderBlocks
    val te = tile.asInstanceOf[TileEntityMotor]
    val block = CommonProxy.blockMotor
    if(te == null) return

    /*val pos = WorldPos(
      (x + tileEntityRenderer.playerX).toInt,
      (y + tileEntityRenderer.playerY).toInt,
      (z + tileEntityRenderer.playerZ).toInt)*/
    val pos = WorldPos(
      te.xCoord,
      te.yCoord,
      te.zCoord)
    //log.info(s"pos: $pos")
    val meta = te.getBlockMetadata
    val or = te.orientation
    val dir = ForgeDirection.values()(meta)
    glColor4f(0F, 0F, 0F, 0F)
    glPushMatrix()
    glTranslated(x, y, z)
    RenderHelper.disableStandardItemLighting()
    this.bindTexture(TextureMap.locationBlocksTexture)
    glTranslatef(.5F, .5F, .5F)
    glRotatef(90 * or, dir.offsetX, dir.offsetY, dir.offsetZ)
    meta match {
      case 0 => glRotatef(180, 1, 0, 0)
      case 1 => 
      case 2 => glRotatef(90, -1, 0, 0)
      case 3 => glRotatef(90, 1, 0, 0)
      case 4 => glRotatef(90, 0, 0, 1)
      case 5 => glRotatef(90, 0, 0, -1)
    }
    val astep = 360F / 64F
    val angle = if(te.offset == 0) 0
      else (te.offset - 1 + partialTick) * astep

    /*tes.startDrawingQuads()
    tes.setBrightness(CommonProxy.blockMotor.getMixedBrightnessForBlock(tile.worldObj, pos.x, pos.y, pos.z))
    tes.setColorOpaque_F(1, 1, 1)
    model.render("Motor", "Base")
    model.render("Motor", "Frame")
    tes.draw()*/

    glTranslatef(0, 3F/14F, 0)
    if(angle != 0) {
      glRotatef(angle, -1, 0, 0)
    }
    tes.startDrawingQuads()
    tes.setBrightness(CommonProxy.blockMotor.getMixedBrightnessForBlock(tile.worldObj, pos.x, pos.y, pos.z))
    tes.setColorOpaque_F(1, 1, 1)
    model.render("Motor", "Gear", model.getIcon("block", "MotorGear"))
    tes.draw()
    glPopMatrix()
    RenderHelper.enableStandardItemLighting()
  }
}

object BlockMotorRenderer extends ISimpleBlockRenderingHandler {
  CommonProxy.blockMotor.renderType = getRenderId

  override def renderInventoryBlock(
      block: Block,
      metadata: Int,
      modelId: Int,
      rb: RenderBlocks) {
    glPushMatrix()
    RenderHelper.disableStandardItemLighting()
    tes.startDrawingQuads()
    tes.setColorOpaque_F(1, 1, 1)
    model.render("Motor", "Base", model.getIcon("block", "MotorBase"))
    model.render("Motor", "Frame", model.getIcon("block", "MotorFrame"))
    tes.draw()
    glTranslatef(0, 3F/14F, 0)
    tes.startDrawingQuads()
    tes.setColorOpaque_F(1, 1, 1)
    model.render("Motor", "Gear", model.getIcon("block", "MotorGear"))
    tes.draw()
    glPopMatrix()
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
    world.getBlockTileEntity(x, y, z) match {
      case te: TileEntityMotor =>
        val meta = te.getBlockMetadata
        val or = te.orientation
        tes.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z))
        tes.setColorOpaque_F(1, 1, 1)
        tes.addTranslation(x + .5F, y + .5F, z + .5F)
        model.renderTransformed("Motor", "Base", model.getIcon("block", "MotorBase"), rotator2(meta, or))
        model.renderTransformed("Motor", "Frame", model.getIcon("block", "MotorFrame"), rotator2(meta, or))
        tes.addTranslation(-x - .5F, -y - .5F, -z - .5F)
        true
      case _ => false
    }
  }
  override val shouldRender3DInInventory = true
  override lazy val getRenderId = RenderingRegistry.getNextAvailableRenderId()
}
