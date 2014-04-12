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
  client.renderer.texture.{ IIconRegister, TextureMap },
  client.renderer.{ OpenGlHelper, RenderHelper, RenderBlocks },
  creativetab.CreativeTabs,
  entity.player.EntityPlayer,
  init.{ Blocks, Items},
  item.{ Item, ItemStack },
  nbt.NBTTagCompound,
  tileentity.TileEntity,
  world.{ IBlockAccess, World, WorldServer }
import org.lwjgl.opengl.GL11._
import cpw.mods.fml.relauncher.{ SideOnly, Side}
import cpw.mods.fml.common.{ FMLCommonHandler, Loader, Optional }
import cpw.mods.fml.client.registry.{ RenderingRegistry, ISimpleBlockRenderingHandler }
import net.minecraftforge.common.{ MinecraftForge, util }
import util.ForgeDirection
import TrussMod._
import rainwarrior.utils._
import rainwarrior.serial._
import Serial._
import rainwarrior.hooks.{ MovingRegistry, MovingTileRegistry }
trait TraitMotor extends BlockContainer {
  setHardness(5f)
  setResistance(10f)
  setStepSound(Block.soundTypeMetal)
  setBlockName(s"$modId:BlockMotor")
  setCreativeTab(CreativeTabs.tabBlock)

  var renderType = -1
  import cpw.mods.fml.common.registry._
  //LanguageRegistry.addName(this, "Motor Block")
  //net.minecraftforge.common.MinecraftForge.setBlockHarvestLevel(this, "shovel", 0)
  GameRegistry.registerBlock(this, "Motor_Block")
  GameRegistry.registerTileEntity(classOf[TileEntityMotor], "Motor_TileEntity");
  {
    val motor = new ItemStack(this)
    val frame = new ItemStack(frameBlock)
    val redstone = new ItemStack(Blocks.redstone_block)
    val iron = new ItemStack(Items.iron_ingot)
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
  override def registerBlockIcons(registry: IIconRegister) {}

  override def createNewTileEntity(world: World, meta: Int): TileEntity = new TileEntityMotor
  override def isOpaqueCube = false
  override def isSideSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: ForgeDirection) = {
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

  override def breakBlock(world: World, x: Int, y: Int, z: Int, block: Block, metadata: Int) {
    //TileEntity t = world.getBlockTileEntity(x, y, z)
    //if(t instanceof TileEntityProxy)
    //{
      //((TileEntityProxy)t).invalidate
    //}
    super.breakBlock(world, x, y, z, block, metadata)
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
    val te = world.getTileEntity(x, y, z).asInstanceOf[TileEntityMotor]
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

class BlockMotor
  extends BlockContainer(Material.iron)
  with TraitMotor

import Power._

object TileEntityMotor {
  implicit object serialInstance extends IsMutableSerial[TileEntityMotor] {
    def pickle[F: IsSerialSink](t: TileEntityMotor): F = {
      P.list(
        P(t.energy),
        P(t.orientation),
        P[F, StripHolderTile](t)
      )
    }
    def unpickle[F](t: TileEntityMotor, f: F)(implicit F: IsSerialSource[F]): Unit = {
      val Seq(energy, orientation, stripHolder) = P.unList(f)
      t.energy = P.unpickle[F, Double](energy)
      t.orientation = P.unpickle[F, Int](orientation)
      StripHolderTile.serialInstance.unpickle(t, stripHolder)
    }
  }
}

trait MotorTile extends TileEntity with StripHolderTile {
  var energy: Double
  var orientation: Int = 0

  lazy val side = EffectiveSide(getWorldObj)

  //log.info(s"new TileEntityMotor, isServer: $isServer")

  override def shouldRefresh(oldBlock: Block, newBlock: Block, oldMeta: Int, newMeta: Int, world: World, x: Int, y: Int, z: Int) = {
    if(oldBlock eq newBlock) false else true
  }

  def rotate(isSneaking: Boolean) {
    if(isSneaking) {
      val meta = getBlockMetadata match {
        case 5 => 0
        case _ => getBlockMetadata + 1
      }
      //log.info(s"rotated1, m: $getBlockMetadata, meta: $meta, o: $orientation")
      getWorldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, meta, 1)
      //log.info(s"rotated1, m: $getBlockMetadata, meta: $meta, o: $orientation")
    } else {
      orientation = orientation match {
        case 3 => 0
        case o => o + 1
      }
      //log.info(s"rotated2, m: $getBlockMetadata, o: $orientation")
    }
    getWorldObj.func_147479_m(xCoord, yCoord, zCoord)
  }

  def dirTo = ForgeDirection.values()(moveDir(orientation)(getBlockMetadata))

  def shouldContinue: Boolean = {
    //var t = System.currentTimeMillis

    if(!getWorldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord)) return false

    val meta = getBlockMetadata
    val pos = WorldPos(this) + ForgeDirection.values()(meta)
    //log.info(s"shouldUpdate! meta: $meta, pos: $pos, dirTo: $dirTo, side: ${EffectiveSide(getWorldObj)}")
    val block = getWorldObj.getBlock(pos.x, pos.y, pos.z)
    if ( block == Blocks.air
      || MovingRegistry.isMoving(getWorldObj, pos.x, pos.y, pos.z)
      || !movingTileHandler.canMove(getWorldObj, pos.x, pos.y, pos.z)
    ) return false

    //log.info(s"Activated! meta: $meta, pos: $pos, dirTo: $dirTo, side: ${EffectiveSide(getWorldObj)}")
    val blocks = bfs(Queue(pos))

    val moveEnergy = moveCost + moveCostMultiplier * blocks.size
    if(energy < moveEnergy - eps) return false

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

    val canMove = (blocks.size <= structureLimit) && !strips.exists { (pair) =>
      val c = pair._1
      if(c.y < 0 || c.y >= 256) true
      else {
        val block = getWorldObj.getBlock(c.x, c.y, c.z)
        //log.info(s"block: $block")
        if(block == null) false
        else
          !block.isReplaceable(getWorldObj, c.x, c.y, c.z)
      }
    }

    if(canMove) {
      isMoving = true
      for ((c, size) <- strips) {
        //log.info(s"c: $c")
        //blockMovingStrip.create(getWorldObj, this, c.x, c.y, c.z, dirTo, size)
        getWorldObj.setBlock(c.x, c.y, c.z, blockMovingStrip, 0, 3)
        getWorldObj.getTileEntity(c.x, c.y, c.z) match {
          case te: TileEntityMovingStrip => te.parentPos = Some(this)
          case _ =>
        }
        getWorldObj.markBlockForUpdate(c.x, c.y, c.z)
        this += StripData(c, dirTo, size)
      }
      getWorldObj.markBlockForUpdate(xCoord, yCoord, zCoord)
    }
    //println(s"Motor activation took: ${System.currentTimeMillis - t}")
    //getWorldObj.markBlockForUpdate(xCoord, yCoord, zCoord)
    if(canMove) energy -= moveEnergy
    canMove
  }

  @tailrec private def bfs(
      greyBlocks: Seq[WorldPos], // frames to visit
      blackBlocks: Set[WorldPos] = Set.empty): Set[WorldPos] = { // all blocks to move
    greyBlocks match {
      case _ if blackBlocks.size > structureLimit => blackBlocks
      case Seq() => blackBlocks
      case Seq(next, rest@ _*) => getWorldObj.getBlock(next.x, next.y, next.z) match {
        case block: Block =>
          val toCheck = for { // TODO: prettify
            dir <- ForgeDirection.VALID_DIRECTIONS.toList
            if (
              (block.isInstanceOf[Frame]
                && block.asInstanceOf[Frame].isSideSticky(getWorldObj, next.x, next.y, next.z, dir))
              || {
                val te = getWorldObj.getTileEntity(next.x, next.y, next.z)
                (te.isInstanceOf[Frame]
                && te.asInstanceOf[Frame].isSideSticky(getWorldObj, next.x, next.y, next.z, dir))
              } || MovingTileRegistry.stickyHook(getWorldObj, next.x, next.y, next.z, dir))
            c = next + dir
            if !(c == WorldPos(this))
            if !blackBlocks(c)
            if !greyBlocks.contains(c)
            if !(MovingRegistry.isMoving(getWorldObj, c.x, c.y, c.z))
            if !getWorldObj.isAirBlock(c.x, c.y, c.z)
            if movingTileHandler.canMove(getWorldObj, c.x, c.y, c.z)
            /*if !(id == blockMotorId && {
              val meta = getWorldObj.getBlockMetadata(c.x, c.y, c.z)
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

@Optional.InterfaceList(Array(
  new Optional.Interface(iface = CIPowerReceptor, modid = bcid),
  //new Optional.Interface(iface = CBuildcraftPowerReceptor, modid = bcid),
  new Optional.Interface(iface = CIEnergyHandler, modid = cofhid),
  //new Optional.Interface(iface = CCofhEnergyHandler, modid = cofhid),
  new Optional.Interface(iface = CIEnergySink, modid = icid)
))
class TileEntityMotor(
    var energy: Double
  ) extends StripHolderTile with SimpleSerialTile[TileEntityMotor] with MotorTile with PowerTile {

  def this() = this(0)
  final def channel = tileChannel
  final def Repr = TileEntityMotor.serialInstance

  val maxEnergy = motorCapacity

  val bcRatio = TrussMod.bcRatio
  val cofhRatio = TrussMod.cofhRatio
  val ic2Ratio = TrussMod.ic2Ratio
}

@SideOnly(Side.CLIENT)
object TileEntityMotorRenderer extends TileEntitySpecialRenderer {
  var rb: RenderBlocks = null
  override def func_147496_a(world: World) {
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
    val block = blockMotor
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
    tes.setBrightness(blockMotor.getMixedBrightnessForBlock(tile.world, pos.x, pos.y, pos.z))
    tes.setColorOpaque_F(1, 1, 1)
    model.render("Motor", "Base")
    model.render("Motor", "Frame")
    tes.draw()*/

    glTranslatef(0, 3F/14F, 0)
    if(angle != 0) {
      glRotatef(angle, -1, 0, 0)
    }
    tes.startDrawingQuads()
    //tes.setBrightness(blockMotor.getMixedBrightnessForBlock(tile.getWorldObj, pos.x, pos.y, pos.z))
    //tes.setColorOpaque_F(1, 1, 1)
    model.render(getLightMatrix(te.getWorldObj, pos.x, pos.y, pos.z).get, "Motor", "Gear", model.getIcon("block", "MotorGear")) // slightly incorrect
    tes.draw()
    glPopMatrix()
    RenderHelper.enableStandardItemLighting()
  }
}

object BlockMotorRenderer extends ISimpleBlockRenderingHandler {
  blockMotor.renderType = getRenderId

  override def renderInventoryBlock(
      block: Block,
      metadata: Int,
      modelId: Int,
      rb: RenderBlocks) {
    glPushMatrix()
    RenderHelper.disableStandardItemLighting()
    tes.startDrawingQuads()
    //tes.setColorOpaque_F(1, 1, 1)
    model.render(dummyLightMatrix, "Motor", "Base", model.getIcon("block", "MotorBase"))
    model.render(dummyLightMatrix, "Motor", "Frame", model.getIcon("block", "MotorFrame"))
    tes.draw()
    glTranslatef(0, 3F/14F, 0)
    tes.startDrawingQuads()
    //tes.setColorOpaque_F(1, 1, 1)
    model.render(dummyLightMatrix, "Motor", "Gear", model.getIcon("block", "MotorGear"))
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
    world.getTileEntity(x, y, z) match {
      case te: TileEntityMotor =>
        val meta = te.getBlockMetadata
        val or = te.orientation
        val m = getLightMatrix(world, x, y, z).get
        //tes.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z))
        //tes.setColorOpaque_F(1, 1, 1)
        tes.addTranslation(x + .5F, y + .5F, z + .5F)
        model.renderTransformed(m, "Motor", "Base", model.getIcon("block", "MotorBase"), rotator2(meta, or))
        model.renderTransformed(m, "Motor", "Frame", model.getIcon("block", "MotorFrame"), rotator2(meta, or))
        tes.addTranslation(-x - .5F, -y - .5F, -z - .5F)
        true
      case _ => false
    }
  }
  override def shouldRender3DInInventory(modelId: Int) = true
  override lazy val getRenderId = RenderingRegistry.getNextAvailableRenderId()
}
