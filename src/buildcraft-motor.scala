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

import java.util.{ List => JList, ArrayList, Set => JSet, TreeSet => JTreeSet }
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
  entity.Entity,
  entity.player.EntityPlayer,
  item.{ Item, ItemStack },
  nbt.{ NBTTagCompound, NBTTagList },
  network.INetworkManager,
  network.packet.{ Packet, Packet132TileEntityData },
  tileentity.TileEntity,
  util.{ AxisAlignedBB, MovingObjectPosition, Vec3 },
  world.{ ChunkPosition, chunk, EnumSkyBlock, IBlockAccess, NextTickListEntry, World, WorldServer },
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

import buildcraft.api.power.{ PowerHandler, IPowerReceptor }

/*class BuildcraftTileMotorProxy extends TileMotorBlockProxy {
  override def init() = {
      extends BlockContainer(CommonProxy.blockFrameId + 5, Material.ground)
      with BlockBuildcraftMotor
    blockBuildcraftMotor
  }
}*/

trait BuildcraftPowerReceptor extends TileEntity with IPowerReceptor {
  log.info("New BuildcraftPowerReceptor")
  val powerHandler = new PowerHandler(this, PowerHandler.Type.MACHINE)
  powerHandler.configure(
    30, // minEnergyReceived
    30, // maxEnergyReceived
    30, // activationEnergy
    30 // maxStoredEnergy
  )
  powerHandler.configurePowerPerdition(
    1, // powerLoss
    100 // powerLossRegularity
  )

  abstract override def readFromNBT(cmp: NBTTagCompound) {
    super.readFromNBT(cmp)
    powerHandler.readFromNBT(cmp)
  }

  abstract override def writeToNBT(cmp: NBTTagCompound) {
    super.writeToNBT(cmp)
    powerHandler.writeToNBT(cmp)
  }

  override def getPowerReceiver(side: ForgeDirection): PowerHandler#PowerReceiver = 
    powerHandler.getPowerReceiver

  override def doWork(workProvider: PowerHandler) = if(!worldObj.isRemote) {
    assert(workProvider == powerHandler)
    if(powerHandler.getEnergyStored >= 30) {
      if(worldObj.isBlockIndirectlyGettingPowered(this.x, this.y, this.z)) {
        if(powerHandler.useEnergy(30, 30, true) == 30) {
          log.info("Moving!")
        }
      }
    }
  }

  override def getWorld = worldObj

  abstract override def updateEntity() {
    super.updateEntity()
    getPowerReceiver(null).update()
  }
}

class TileEntityBuildcraftMotor extends TileEntityMotor with BuildcraftPowerReceptor
