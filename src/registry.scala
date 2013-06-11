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
import net.minecraft.world.World
import net.minecraft.client.Minecraft
import Minecraft.{ getMinecraft => mc }
import net.minecraft.client.renderer.{ tileentity, RenderBlocks, RenderHelper, Tessellator, OpenGlHelper }
import tileentity.TileEntityRenderer//.{ instance => teRenderer }
import Tessellator.{ instance => tes }
import java.util.{ EnumSet, Set, HashSet }
import cpw.mods.fml.{ common, relauncher }
import common.{ ITickHandler, TickType, registry }
import relauncher.Side
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
      renderBlocks = new RenderBlocks(world)
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
  val moving = new OpenHashMap[WorldPos, BlockData]
  val debugOffset = new BlockData(0, 0, 0)
  var isRendering = false
  var partialTickTime: Float = 0

  def isMoving(x: Int, y: Int, z: Int): Boolean = moving isDefinedAt WorldPos(x, y, z)
  def getData(c: WorldPos): BlockData = {
    (moving.get(c): @unchecked) match {
      case Some(d) => d
    }
  }
  def xOffset(x: Int, y: Int, z: Int): Float = getData(WorldPos(x, y, z)).x
  def yOffset(x: Int, y: Int, z: Int): Float = getData(WorldPos(x, y, z)).y
  def zOffset(x: Int, y: Int, z: Int): Float = getData(WorldPos(x, y, z)).z

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
    for((coords, data) <- moving) {
      HelperRenderer.render(coords, data)
    }
  }
  def addMoving(world: World, pos: WorldPos, offset: BlockData) {
    moving(pos) = offset
    val (x, y, z) = pos.toTuple
    world.updateAllLightTypes(x, y, z)
    for(d <- ForgeDirection.values) {
      world.markBlockForRenderUpdate(x + d.offsetX, y + d.offsetY, z + d.offsetZ)
      //println(f"Update: (${x + d.offsetX}, ${y + d.offsetY}, ${z + d.offsetZ})")
    }
  }
  def delMoving(world: World, pos: WorldPos) {
    MovingRegistry.moving -= pos
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
