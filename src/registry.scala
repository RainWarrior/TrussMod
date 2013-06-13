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

package rainwarrior.hooks

import scala.collection.mutable.OpenHashMap
import net.minecraftforge.event.ForgeSubscribe
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.common.ForgeDirection
import net.minecraft.tileentity.TileEntity
import net.minecraft.block.Block
import net.minecraft.world.{ World, IBlockAccess, EnumSkyBlock }
import net.minecraft.client.Minecraft
import Minecraft.{ getMinecraft => mc }
import net.minecraft.client.renderer.{ tileentity, RenderBlocks, RenderHelper, Tessellator, OpenGlHelper }
import tileentity.{ TileEntityRenderer, TileEntitySpecialRenderer }
import Tessellator.{ instance => tes }
import java.util.{ EnumSet, Set, HashSet }
import cpw.mods.fml.{ common, relauncher }
import common.{ ITickHandler, TickType, registry }
import relauncher.{ Side, SideOnly }
import rainwarrior.utils._

object HelperRenderer {
  import org.lwjgl.opengl.GL11._
  def world = mc.theWorld
  var oldWorld: World = null
  var renderBlocks: RenderBlocks = null // mc.renderGlobal.globalRenderBlocks
  //val eps = 1F / 0x10000
  //var x1, x2, y1, y2, z1, z2 = 0F;

  def render(c: WorldPos, d: BlockData) {
    if(oldWorld != world) {
      oldWorld = world
      renderBlocks = new MovingRenderBlocks(new MovingWorldProxy(world))
      renderBlocks.renderAllFaces = true
    }

    val block = Block.blocksList(world.getBlockId(c.x, c.y, c.z))
    if(block == null) return

    val engine = TileEntityRenderer.instance.renderEngine
    if(engine != null) engine.bindTexture("/terrain.png")
    mc.entityRenderer.enableLightmap(MovingRegistry.partialTickTime)
    val light = world.getLightBrightnessForSkyBlocks(c.x, c.y, c.z, 0)
    val l1 = light % 65536
    val l2 = light / 65536
    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l1, l2)
    glColor4f(0, 0, 0, 0)
    //RenderHelper.disableStandardItemLighting()
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
    glEnable(GL_BLEND)
    glDisable(GL_CULL_FACE)
    glShadeModel(if(Minecraft.isAmbientOcclusionEnabled) GL_SMOOTH else GL_FLAT)

    tes.startDrawingQuads()
    tes.setTranslation(
      -TileEntityRenderer.staticPlayerX + d.x,
      -TileEntityRenderer.staticPlayerY + d.y,
      -TileEntityRenderer.staticPlayerZ + d.z)
    tes.setColorOpaque(1, 1, 1)

    //x1 = block.getBlockBoundsMinX.toFloat
    //x2 = block.getBlockBoundsMaxX.toFloat
    //y1 = block.getBlockBoundsMinY.toFloat
    //y2 = block.getBlockBoundsMaxY.toFloat
    //z1 = block.getBlockBoundsMinZ.toFloat
    //z2 = block.getBlockBoundsMaxZ.toFloat
    //block.setBlockBounds(x1 + eps, y1 + eps, z1 + eps, x2 - eps, y2 - eps, z2 - eps)
    //renderBlocks.overrideBlockBounds(x1 + eps, y1 + eps, z1 + eps, x2 - eps, y2 - eps, z2 - eps)
    val oldOcclusion = mc.gameSettings.ambientOcclusion
    mc.gameSettings.ambientOcclusion = 0
    renderBlocks.do_renderBlockByRenderType(block, c.x, c.y, c.z)
    mc.gameSettings.ambientOcclusion = oldOcclusion

    //renderBlocks.unlockBlockBounds()
    //block.setBlockBounds(x1, y1, z1, x2, y2, z2)

    tes.setTranslation(0, 0, 0)
    tes.draw()

    //RenderHelper.enableStandardItemLighting()
    mc.entityRenderer.disableLightmap(MovingRegistry.partialTickTime)
  }
}

object MovingRegistry {
  case class Key(world: World, pos: WorldPos)
  final val eps = 1.0 / 0x10000
  var moving = Map.empty[Key, BlockData]
  val debugOffset = new BlockData(0, 0, 0)
  var isRendering = false
  var partialTickTime: Float = 0

  def isMoving(world: World, x: Int, y: Int, z: Int): Boolean = moving isDefinedAt Key(world, (x, y, z))
  def getData(world: World, c: WorldPos): BlockData = {
    moving(Key(world, c))
  }
  def xOffset(world: World, x: Int, y: Int, z: Int): Float = getData(world, WorldPos(x, y, z)).x
  def yOffset(world: World, x: Int, y: Int, z: Int): Float = getData(world, WorldPos(x, y, z)).y
  def zOffset(world: World, x: Int, y: Int, z: Int): Float = getData(world, WorldPos(x, y, z)).z

  def onPreRenderTick(time: Float) {
    isRendering = true
    partialTickTime = time
  }
  def onPostRenderTick() {
    isRendering = false
  }
  @ForgeSubscribe
  def onRenderWorld(e: RenderWorldLastEvent) {
    //mc.gameSettings.showDebugInfo = false
    debugOffset.y = Math.sin(System.nanoTime / 0x4000000).toFloat / 2 + .5F
    for((Key(world, coords), data) <- moving; if(world.isClient)) {
      HelperRenderer.render(coords, data)
    }
  }
  def addMoving(world: World, pos: WorldPos, offset: BlockData) {
    moving += ((Key(world, pos), offset))
    val (x, y, z) = pos.toTuple
    world.updateAllLightTypes(x, y, z)
    for(d <- ForgeDirection.values) {
      world.markBlockForRenderUpdate(x + d.offsetX, y + d.offsetY, z + d.offsetZ)
      //println(f"Update: (${x + d.offsetX}, ${y + d.offsetY}, ${z + d.offsetZ})")
    }
  }
  def delMoving(world: World, pos: WorldPos) = {
    moving -= Key(world, pos)
    val (x, y, z) = pos.toTuple
    world.updateAllLightTypes(x, y, z)
    for(d <- ForgeDirection.values) {
      world.markBlockForRenderUpdate(x + d.offsetX, y + d.offsetY, z + d.offsetZ)
    }
  }
}

object RenderTickHandler extends ITickHandler {
  override def ticks = EnumSet.of(TickType.RENDER)
  override def getLabel = "MovingRegistry Render Handler"
  override def tickStart(tp: EnumSet[TickType], tickData: AnyRef*) {
    if(tp contains TickType.RENDER) MovingRegistry.onPreRenderTick(tickData(0).asInstanceOf[Float])
  }
  override def tickEnd(tp: EnumSet[TickType], tickData: AnyRef*) {
    if(tp contains TickType.RENDER) MovingRegistry.onPostRenderTick()
  }
}

@SideOnly(Side.CLIENT)
object MovingTileEntityRenderer extends TileEntityRenderer {
  import org.lwjgl.opengl.GL11._
  import TileEntityRenderer._
  import collection.JavaConversions._
  val oldRenderer = TileEntityRenderer.instance
  specialRendererMap = oldRenderer.specialRendererMap
  for(tesr <- specialRendererMap.asInstanceOf[java.util.HashMap[Class[_], TileEntitySpecialRenderer]].values) {
    tesr.setTileEntityRenderer(this)
  }
  TileEntityRenderer.instance = this
  override def renderTileEntity(te: TileEntity, parTick: Float) {
    if (te.getDistanceFrom(this.playerX, this.playerY, this.playerZ) < te.getMaxRenderDistanceSquared()) {
        val i = this.worldObj.getLightBrightnessForSkyBlocks(te.xCoord, te.yCoord, te.zCoord, 0)
        val j = i % 0x10000
        val k = i / 0x10000
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, j / 1.0F, k / 1.0F)
        glColor4f(1.0F, 1.0F, 1.0F, 1.0F)
        if(MovingRegistry.isMoving(te.worldObj, te.xCoord, te.yCoord, te.zCoord)) {
          this.renderTileEntityAt(
            te,
            te.xCoord.toDouble - staticPlayerX + MovingRegistry.xOffset(te.worldObj, te.xCoord, te.yCoord, te.zCoord),
            te.yCoord.toDouble - staticPlayerY + MovingRegistry.yOffset(te.worldObj, te.xCoord, te.yCoord, te.zCoord),
            te.zCoord.toDouble - staticPlayerZ + MovingRegistry.zOffset(te.worldObj, te.xCoord, te.yCoord, te.zCoord),
            parTick)
      } else {
        this.renderTileEntityAt(te, te.xCoord.toDouble - staticPlayerX, te.yCoord.toDouble - staticPlayerY, te.zCoord.toDouble - staticPlayerZ, parTick)
      }
    }
  }
}

@SideOnly(Side.CLIENT)
class MovingRenderBlocks(world: IBlockAccess) extends RenderBlocks(world)
{
  import MovingRegistry.{ isMoving, eps }
  override def renderStandardBlock(block: Block, x: Int, y: Int, z: Int) = {
    if(isMoving(mc.theWorld, x, y, z)) {
      if(renderMinX == 0.0D) renderMinX += eps;
      if(renderMinY == 0.0D) renderMinY += eps;
      if(renderMinZ == 0.0D) renderMinZ += eps;
      if(renderMaxX == 1.0D) renderMaxX -= eps;
      if(renderMaxY == 1.0D) renderMaxY -= eps;
      if(renderMaxZ == 1.0D) renderMaxZ -= eps;
    }
    super.renderStandardBlock(block, x, y, z)
  }
}

class MovingWorldProxy(val world: World) extends IBlockAccess {
  def computeLightValue(x: Int, y: Int, z: Int, tpe: EnumSkyBlock) = {
    (for(dir <- ForgeDirection.VALID_DIRECTIONS; c = (x, y, z) + dir)
      yield world.getSavedLightValue(tpe, c.x, c.y, c.z)).max
  }
  override def getBlockId(x: Int, y: Int, z: Int) = world.getBlockId(x, y, z)

  override def getBlockTileEntity(x: Int, y: Int, z: Int) = world.getBlockTileEntity(x, y, z)

  @SideOnly(Side.CLIENT)
  override def getLightBrightnessForSkyBlocks(x: Int, y: Int, z: Int, light: Int) = 
    MovingRegistry.isMoving(mc.theWorld, x, y, z) match {
    case true =>
      val l1 = computeLightValue(x, y, z, EnumSkyBlock.Sky)
      val l2 = computeLightValue(x, y, z, EnumSkyBlock.Block)
      l1 << 20 | Seq(l2, light).max << 4
    case false => world.getLightBrightnessForSkyBlocks(x, y, z, light)
  }

  override def getBlockMetadata(x: Int, y: Int, z: Int) = world.getBlockMetadata(x, y, z)

  @SideOnly(Side.CLIENT)
  override def getBrightness(x: Int, y: Int, z: Int, light: Int) = world.getBrightness(x, y, z, light)

  @SideOnly(Side.CLIENT)
  override def getLightBrightness(x: Int, y: Int, z: Int) = world.getLightBrightness(x, y, z)

  override def getBlockMaterial(x: Int, y: Int, z: Int) = world.getBlockMaterial(x, y, z)

  @SideOnly(Side.CLIENT)
  override def isBlockOpaqueCube(x: Int, y: Int, z: Int) = MovingRegistry.isMoving(mc.theWorld, x, y, z) match {
    case true => false
    case false => world.isBlockOpaqueCube(x, y, z)
  }

  override def isBlockNormalCube(x: Int, y: Int, z: Int) = world.isBlockNormalCube(x, y, z)

  @SideOnly(Side.CLIENT)
  override def isAirBlock(x: Int, y: Int, z: Int) = world.isAirBlock(x, y, z)

  @SideOnly(Side.CLIENT)
  override def getBiomeGenForCoords(x: Int, z: Int) = world.getBiomeGenForCoords(x, z)

  @SideOnly(Side.CLIENT)
  override def getHeight() = world.getHeight()

  @SideOnly(Side.CLIENT)
  override def extendedLevelsInChunkCache() = world.extendedLevelsInChunkCache()

  @SideOnly(Side.CLIENT)
  override def doesBlockHaveSolidTopSurface(x: Int, y: Int, z: Int) =
    world.doesBlockHaveSolidTopSurface(x, y, z)

  override def getWorldVec3Pool() = world.getWorldVec3Pool()

  override def isBlockProvidingPowerTo(x: Int, y: Int, z: Int, side: Int) =
    world.isBlockProvidingPowerTo(x, y, z, side)

  override def isBlockSolidOnSide(x: Int, y:Int, z:Int, side: ForgeDirection, _default: Boolean) = 
    world.isBlockSolidOnSide(x, y, z, side, _default)
}
