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

package rainwarrior.hooks

import gnu.trove.map.hash.{
  TIntObjectHashMap,
  TLongObjectHashMap
}
import gnu.trove.set.hash.TLongHashSet
import gnu.trove.impl.sync.{
  TSynchronizedIntObjectMap,
  TSynchronizedLongObjectMap,
  TSynchronizedLongSet
}
//import gnu.trove.set.TLongHashSet
import scala.collection.mutable.OpenHashMap
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.common.util.ForgeDirection
import net.minecraft.tileentity.TileEntity
import net.minecraft.block.Block
import net.minecraft.world.{ World, IBlockAccess, EnumSkyBlock }
import net.minecraft.client.Minecraft
import Minecraft.{ getMinecraft => mc }
import net.minecraft.client.renderer.{ tileentity, texture, RenderBlocks, RenderHelper, Tessellator, OpenGlHelper }
import texture.TextureMap
import tileentity.{ TileEntityRendererDispatcher, TileEntitySpecialRenderer }
import Tessellator.{ instance => tes }
import java.util.{ EnumSet, Set, HashSet }
import cpw.mods.fml.{ common, relauncher }
import common.{ eventhandler, gameevent, registry }
import eventhandler.SubscribeEvent
import gameevent.TickEvent
import relauncher.{ Side, SideOnly }
import Math.max
import rainwarrior.utils._

object HelperRenderer {
  import org.lwjgl.opengl.GL11._
  var partialTickTime: Float = 0
  var isRendering = false
  var check = true

  def world: World = mc.theWorld
  var oldWorld: World = null
  var renderBlocks: RenderBlocks = null // mc.renderGlobal.globalRenderBlocks
  //val eps = 1F / 0x10000
  //var x1, x2, y1, y2, z1, z2 = 0F;

  def render(x: Int, y: Int, z: Int, d: BlockData, tick: Float) {

    val oldOcclusion = mc.gameSettings.ambientOcclusion
    mc.gameSettings.ambientOcclusion = 0

    val block = world.getBlock(x, y, z)
    if(block == null) return

    val engine = TileEntityRendererDispatcher.instance.field_147553_e
    if(engine != null) engine.bindTexture(TextureMap.locationBlocksTexture)
    mc.entityRenderer.enableLightmap(partialTickTime)
    val light = world.getLightBrightnessForSkyBlocks(x, y, z, block.getLightValue(world, x, y, z))
    val l1 = light % 65536
    val l2 = light / 65536
    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, l1, l2)
    glColor4f(0, 0, 0, 0)
    RenderHelper.disableStandardItemLighting()
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
    glEnable(GL_BLEND)
    glDisable(GL_CULL_FACE)
    glShadeModel(if(Minecraft.isAmbientOcclusionEnabled) GL_SMOOTH else GL_FLAT)

    for(pass <- 0 to 1) {
      tes.startDrawingQuads()
      tes.setTranslation(
        -TileEntityRendererDispatcher.staticPlayerX + clamp(-1F, 1F, d.x + d.dirTo.x * tick / 16F),
        -TileEntityRendererDispatcher.staticPlayerY + clamp(-1F, 1F, d.y + d.dirTo.y * tick / 16F),
        -TileEntityRendererDispatcher.staticPlayerZ + clamp(-1F, 1F, d.z + d.dirTo.z * tick / 16F))
      tes.setColorOpaque(1, 1, 1)

      block.getRenderBlockPass()
      if(block.canRenderInPass(pass)) {
        check = false
        renderBlocks.renderBlockByRenderType(block, x, y, z)
        check = true
      }

      tes.setTranslation(0, 0, 0)
      tes.draw()

    }
    RenderHelper.enableStandardItemLighting()
    mc.entityRenderer.disableLightmap(partialTickTime)

    mc.gameSettings.ambientOcclusion = oldOcclusion
  }

  @SubscribeEvent
  def onRenderWorld(e: RenderWorldLastEvent) {
    //mc.gameSettings.showDebugInfo = false
    //MovingRegistry.debugOffset.y = Math.sin(System.nanoTime / 0x4000000).toFloat / 2 + .5F
    if(oldWorld != world) {
      oldWorld = world
      renderBlocks = new MovingRenderBlocks(new MovingWorldProxy(world))
      renderBlocks.renderAllFaces = true
      MovingRegistry.clientMap.clear() // TODO probably should change to FML events
    }
    MovingRegistry.clientMap.synchronized {
      val it = MovingRegistry.clientMap.iterator
      while(it.hasNext) {
        it.advance()
        val k = it.key
        val v = it.value
        render(unpackX(k), unpackY(k), unpackZ(k), v, partialTickTime)
      }
    }
  }

  def onPreRenderTick(time: Float) {
    isRendering = true
    partialTickTime = time
  }

  def onPostRenderTick() {
    isRendering = false
  }

  def getRenderType(block: Block, x: Int, y: Int, z: Int): Int = {
    if(check && MovingRegistry.isMoving(mc.theWorld, x, y, z)) -1
    else block.getRenderType
  }
}

object MovingRegistry {
  //case class Key(world: World, pos: WorldPos)
  final val eps = 1.0 / 0x10000
  //var moving = Map.empty[Key, BlockData]
  // from packet client coordinates to rendering offset data
  val clientMap = new TSynchronizedLongObjectMap(new TLongObjectHashMap[BlockData])
  // from dimension id to a set of coords of moving blocks
  val serverMap = new TSynchronizedIntObjectMap(new TIntObjectHashMap[TSynchronizedLongSet])
  val debugOffset = new BlockData(0, 0, 0, ForgeDirection.UNKNOWN)

  def isMoving(world: World, x: Int, y: Int, z: Int): Boolean = if(world.isRemote) {
    //moving isDefinedAt Key(world, (x, y, z))
    clientMap.containsKey(packCoords(x, y, z))
  } else {
    val dim = world.provider.dimensionId
    if(serverMap.containsKey(dim)) {
      val worldSet = serverMap.get(dim)
      worldSet.contains(packCoords(x, y, z))
    } else false
  }

  def getData(world: World, c: WorldPos): BlockData = if(world.isRemote) {
    //moving(Key(world, c))
    clientMap.get(packCoords(c.x, c.y, c.z))
  } else null

  def addMoving(world: World, c: WorldPos, offset: BlockData) = this.synchronized {
    if(world.isRemote) {
      //moving += ((Key(world, pos), offset))
      clientMap.put(packCoords(c.x, c.y, c.z), offset)
      world.func_147451_t(c.x, c.y, c.z)
      for(d <- ForgeDirection.values) {
        world.func_147479_m(c.x + d.offsetX, c.y + d.offsetY, c.z + d.offsetZ)
        //println(f"Update: (${c.x + d.offsetX}, ${c.y + d.offsetY}, ${c.z + d.offsetZ})")
      }
    } else {
      val dim = world.provider.dimensionId
      if(!serverMap.containsKey(dim)) {
        serverMap.put(dim, new TSynchronizedLongSet(new TLongHashSet))
      }
      val worldSet = serverMap.get(dim)
      worldSet.add(packCoords(c.x, c.y, c.z))
    }
  }

  def delMoving(world: World, c: WorldPos) = this.synchronized {
    if(world.isRemote) {
      //moving -= Key(world, pos)
      clientMap.remove(packCoords(c.x, c.y, c.z))
      world.func_147451_t(c.x, c.y, c.z)
      for(d <- ForgeDirection.values) {
        world.func_147479_m(c.x + d.offsetX, c.y + d.offsetY, c.z + d.offsetZ)
      }
    } else {
      val dim = world.provider.dimensionId
      if(serverMap.containsKey(dim)) {
        val worldSet = serverMap.get(dim)
        worldSet.remove(packCoords(c.x, c.y, c.z))
      }
    }
  }
}

object RenderTickHandler {
  @SubscribeEvent
  def onRenderTick(e: TickEvent.RenderTickEvent) {
    if(e.phase == TickEvent.Phase.START)
      HelperRenderer.onPreRenderTick(e.renderTickTime)
    else
      HelperRenderer.onPostRenderTick()
  }
}

@SideOnly(Side.CLIENT)
class MovingTileEntityRenderer extends TileEntityRendererDispatcher {
  import org.lwjgl.opengl.GL11._
  import TileEntityRendererDispatcher._
  import collection.JavaConversions._
  val oldRenderer = instance
  mapSpecialRenderers = oldRenderer.mapSpecialRenderers
  for(tesr <- mapSpecialRenderers.asInstanceOf[java.util.HashMap[Class[_], TileEntitySpecialRenderer]].values) {
    tesr.func_147497_a(this)
  }
  TileEntityRendererDispatcher.instance = this
  override def renderTileEntity(te: TileEntity, tick: Float) {
    if (te.getDistanceFrom(this.field_147560_j, this.field_147561_k, this.field_147558_l) < te.getMaxRenderDistanceSquared()) {
      val i = this.field_147550_f.getLightBrightnessForSkyBlocks(te.xCoord, te.yCoord, te.zCoord, 0)
      val j = i % 0x10000
      val k = i / 0x10000
      OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, j / 1.0F, k / 1.0F)
      glColor4f(1.0F, 1.0F, 1.0F, 1.0F)
      if(MovingRegistry.isMoving(te.getWorldObj, te.xCoord, te.yCoord, te.zCoord)) {
        val o = MovingRegistry.getData(te.getWorldObj, (te.xCoord, te.yCoord, te.zCoord))
        this.renderTileEntityAt(
          te,
          te.xCoord.toDouble - staticPlayerX + clamp(-1F, 1F, o.x + o.dirTo.x * tick / 16F),
          te.yCoord.toDouble - staticPlayerY + clamp(-1F, 1F, o.y + o.dirTo.y * tick / 16F),
          te.zCoord.toDouble - staticPlayerZ + clamp(-1F, 1F, o.z + o.dirTo.z * tick / 16F),
          tick)
      } else {
        this.renderTileEntityAt(
          te,
          te.xCoord.toDouble - staticPlayerX,
          te.yCoord.toDouble - staticPlayerY,
          te.zCoord.toDouble - staticPlayerZ,
          tick)
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
  override def getBlock(x: Int, y: Int, z: Int) = world.getBlock(x, y, z)

  override def getTileEntity(x: Int, y: Int, z: Int) = world.getTileEntity(x, y, z)

  def computeLightValue(x: Int, y: Int, z: Int, tpe: EnumSkyBlock) = {
    (for(dir <- ForgeDirection.VALID_DIRECTIONS; c = (x, y, z) + dir)
      yield world.getSavedLightValue(tpe, c.x, c.y, c.z)).max
  }

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

  /*@SideOnly(Side.CLIENT)
  override def getBrightness(x: Int, y: Int, z: Int, light: Int) = world.getBrightness(x, y, z, light)*/

  /*@SideOnly(Side.CLIENT)
  override def getLightBrightness(x: Int, y: Int, z: Int) = world.getLightBrightness(x, y, z)*/

  //override def getBlockMaterial(x: Int, y: Int, z: Int) = world.getBlockMaterial(x, y, z)

  /*@SideOnly(Side.CLIENT)
  override def isBlockOpaqueCube(x: Int, y: Int, z: Int) = MovingRegistry.isMoving(mc.theWorld, x, y, z) match {
    case true => false
    case false => world.isBlockOpaqueCube(x, y, z)
  }*/

  //override def isBlockNormalCube(x: Int, y: Int, z: Int) = world.isBlockNormalCube(x, y, z)

  @SideOnly(Side.CLIENT)
  override def isAirBlock(x: Int, y: Int, z: Int) = world.isAirBlock(x, y, z)

  @SideOnly(Side.CLIENT)
  override def getBiomeGenForCoords(x: Int, z: Int) = world.getBiomeGenForCoords(x, z)

  @SideOnly(Side.CLIENT)
  override def getHeight() = world.getHeight()

  @SideOnly(Side.CLIENT)
  override def extendedLevelsInChunkCache() = world.extendedLevelsInChunkCache()

  /*@SideOnly(Side.CLIENT)
  override def doesBlockHaveSolidTopSurface(x: Int, y: Int, z: Int) =
    world.doesBlockHaveSolidTopSurface(x, y, z)*/

  override def getWorldVec3Pool() = world.getWorldVec3Pool()

  override def isBlockProvidingPowerTo(x: Int, y: Int, z: Int, side: Int) =
    world.isBlockProvidingPowerTo(x, y, z, side)

  override def isSideSolid(x: Int, y:Int, z:Int, side: ForgeDirection, _default: Boolean) = 
    world.isSideSolid(x, y, z, side, _default)
}
