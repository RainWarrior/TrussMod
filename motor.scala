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

import net.minecraft._,
  block.{ Block, BlockContainer },
  block.material.Material,
  client.renderer.tileentity.TileEntitySpecialRenderer,
  client.renderer.Tessellator.{ instance => tes },
  client.renderer.{ OpenGlHelper, RenderHelper },
  creativetab.CreativeTabs,
  entity.player.EntityPlayer,
  nbt.NBTTagCompound,
  network.INetworkManager,
  network.packet.{ Packet, Packet132TileEntityData },
  tileentity.TileEntity,
  world.World
import org.lwjgl.opengl.GL11._
import cpw.mods.fml.relauncher.{ SideOnly, Side}
import cpw.mods.fml.common.FMLCommonHandler
import net.minecraftforge.common.MinecraftForge
import Scalamod._


trait BlockMotor extends BlockContainer {
  setHardness(.5f)
  setStepSound(Block.soundGravelFootstep)
  setUnlocalizedName("Scalamod:BlockMotor")
  setCreativeTab(CreativeTabs.tabBlock)

  import cpw.mods.fml.common.registry._
  LanguageRegistry.addName(this, "Motor Block")
  net.minecraftforge.common.MinecraftForge.setBlockHarvestLevel(this, "shovel", 0)
  GameRegistry.registerBlock(this, "Motor_Block")
  GameRegistry.registerTileEntity(classOf[TileEntityMotor], "Motor_TileEntity")

  override def createNewTileEntity(world: World): TileEntity = new TileEntityMotor
  override def isOpaqueCube = false
  override def renderAsNormalBlock = false
  override def getRenderType = -1

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
    if(world.isRemote) {
      val te = world.getBlockTileEntity(x, y, z).asInstanceOf[TileEntityMotor]
      if(te == null)
        throw new RuntimeException("no tile entity!")
//      FMLCommonHandler.instance.showGuiScreen(te.openGui())
    }
    true
  }
}

class TileEntityMotor extends TileEntity {
  val isServer: Boolean = Scalamod.isServer

  if(isServer) {
  } else {
  }
  log.info(s"new TileEntityMotor, isServer: $isServer")

  override def getDescriptionPacket(): Packet = {
    assert(isServer)
    val cmp = new NBTTagCompound
    writeToNBT(cmp);
    log.info("getPacket, world: " + worldObj)
    new Packet132TileEntityData(xCoord, yCoord, zCoord, 255, cmp)
  }

  override def onDataPacket(netManager: INetworkManager, packet: Packet132TileEntityData) {
    assert(!isServer)
    readFromNBT(packet.customParam1)
  }

  override def readFromNBT(cmp: NBTTagCompound) {
    super.readFromNBT(cmp)
    log.info(s"Motor readFromNBT: ($xCoord,$yCoord,$zCoord), $isServer")
  }

  override def writeToNBT(cmp: NBTTagCompound) {
    super.writeToNBT(cmp);
    log.info(s"Motor writeToNBT: ($xCoord,$yCoord,$zCoord), $isServer")
  }

  override def updateEntity() {
  }
}


object TileEntityMotorRenderer extends TileEntitySpecialRenderer 
{
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
    val te = tile.asInstanceOf[TileEntityMotor]
    if(te == null) return

    glColor4f(0F, 0F, 0F, 0F)
    glPushMatrix()
    glTranslated(x, y, z)
    glScaled(-1D/16D, -1D/16D, -1D/16D)
    glTranslatef(-15, -14, 0)
    RenderHelper.disableStandardItemLighting()
    glTranslatef(7, 6, -8)
    glDisable(GL_TEXTURE_2D)

    tes.startDrawingQuads()
    tes.setColorRGBA(0xB4, 0x8E, 0x4F, 0xFF)
    val w1 = 6
    val w2 = 3

    addSquareZ(-7, 8, 8, 6, 8)
    addSquareZ(7, 8, 6, -8, 8)
    addSquareZ(-8, 7, -6, -8, 8)
    addSquareZ(-8, -7, 8, -6, 8)

    addSquareZ(-w1, 8, w1, 8, w2)
    addSquareZ(w1, 8, -8, w1, w2)
    addSquareZ(-8, w1, -8, -w1, w2)
    addSquareZ(-8, -w1, -w1, 8, w2)

    addSquareZ(-w1, w1, -w1, w1, -8)

    addSquareX(-8, -8, 8, w2, 8)
    addSquareX(8, 8, -8, w2, 8)
    addSquareY(-8, 8, -8, w2, 8)
    addSquareY(8, -8, 8, w2, 8)

    addSquareX(-w1, -w1, w1, -8, w2)
    addSquareX(w1, w1, -w1, -8, w2)
    addSquareY(-w1, w1, -w1, -8, w2)
    addSquareY(w1, -w1, w1, -8, w2)

    tes.draw()

    glEnable(GL_TEXTURE_2D)
    RenderHelper.enableStandardItemLighting()
    glPopMatrix()
  }
}

