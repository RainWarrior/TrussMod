/*

Copyright © 2012 - 2014 RainWarrior

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

import net.minecraft._,
  nbt.NBTTagCompound,
  tileentity.TileEntity
import cpw.mods.fml.common.{ Loader, Optional }
import net.minecraftforge.common.{ MinecraftForge, util }
import util.ForgeDirection

import TrussMod._
import rainwarrior.utils._

import buildcraft.api.power.{ PowerHandler, IPowerReceptor }
import cofh.api.energy.IEnergyHandler
import ic2.api.energy.tile.IEnergySink
import ic2.api.energy.event.{ EnergyTileLoadEvent, EnergyTileUnloadEvent }

object Power {
  final val bcid = "BuildCraftAPI|power"
  final val CIPowerReceptor = "buildcraft.api.power.IPowerReceptor"

  final val cofhid = "CoFHAPI"
  final val CIEnergyHandler = "cofh.api.energy.IEnergyHandler"

  final val icid = "IC2"
  final val CIEnergySink = "ic2.api.energy.tile.IEnergySink"
}
import Power._

trait CommonTilePower extends TileEntity {
  def energy: Double
  protected[this] def energy_=(en: Double)

  def maxEnergy: Double

  abstract override def readFromNBT(cmp: NBTTagCompound) {
    super.readFromNBT(cmp)
    energy = cmp.getDouble("energy")
  }

  abstract override def writeToNBT(cmp: NBTTagCompound) {
    super.writeToNBT(cmp)
    cmp.setDouble("energy", energy)
  }
}

@Optional.InterfaceList(Array(
  new Optional.Interface(iface = CIPowerReceptor, modid = bcid)
))
trait BuildcraftPowerReceptor extends CommonTilePower with IPowerReceptor {
  val bcRatio: Double

  def minBcStored: Float = 1
  def maxBcStored: Float = 100

  private[this] var _powerHandler: AnyRef = null

  @Optional.Method(modid = bcid)
  def powerHandler = if(_powerHandler == null) {
    // TODO
    val ph = new PowerHandler(this, PowerHandler.Type.MACHINE)
    ph.configure(
      minBcStored, // minEnergyReceived
      maxBcStored, // maxEnergyReceived
      minBcStored, // activationEnergy
      maxBcStored // maxStoredEnergy
    )
    ph.setPerdition(new PowerHandler.PerditionCalculator(0.01F))
    _powerHandler = ph
    ph
  } else _powerHandler.asInstanceOf[PowerHandler]

  // will lose internal amount on save/load, oh well

  abstract override def readFromNBT(cmp: NBTTagCompound) {
    super.readFromNBT(cmp)
    if(Loader.isModLoaded(bcid)) powerHandler.readFromNBT(cmp)
  }

  abstract override def writeToNBT(cmp: NBTTagCompound) {
    super.writeToNBT(cmp)
    if(Loader.isModLoaded(bcid)) powerHandler.writeToNBT(cmp)
  }

  @Optional.Method(modid = bcid)
  override def getPowerReceiver(side: ForgeDirection): PowerHandler#PowerReceiver = 
    powerHandler.getPowerReceiver

  @Optional.Method(modid = bcid)
  override def doWork(workProvider: PowerHandler) = if(!getWorldObj.isRemote) {
    assert(workProvider == powerHandler)
    if(powerHandler.getEnergyStored >= minBcStored) {
      val avail = powerHandler.useEnergy(minBcStored, maxBcStored, false)
      val d1 = avail.min(((maxEnergy - energy) * bcRatio).toFloat)
      val delta = powerHandler.useEnergy(d1 min minBcStored, d1, true).toDouble / bcRatio
      energy += delta
    }
  }

  @Optional.Method(modid = bcid)
  override def getWorld = getWorldObj

  abstract override def updateEntity() {
    super.updateEntity()
    if(getWorldObj.isServer && Loader.isModLoaded(bcid)) powerHandler.getPowerReceiver.update()
  }
}

@Optional.InterfaceList(Array(
  new Optional.Interface(iface = CIEnergyHandler, modid = cofhid)
))
trait CofhEnergyHandler extends CommonTilePower with IEnergyHandler {
  val cofhRatio: Double

  @Optional.Method(modid = cofhid)
  override def receiveEnergy(from: ForgeDirection, max: Int, simulate: Boolean) = {
    val delta = (max.toDouble / cofhRatio).min(maxEnergy - energy).floor.toInt
    if(!simulate) energy += delta
    (delta * cofhRatio).ceil.toInt
  }

  @Optional.Method(modid = cofhid)
  override def extractEnergy(from: ForgeDirection, max: Int, simulate: Boolean) = {
    val delta = (max.toDouble / cofhRatio).min(energy).floor.toInt
    if(!simulate) energy -= delta
    (delta * cofhRatio).floor.toInt
  }

  @Optional.Method(modid = cofhid)
  override def canConnectEnergy(from: ForgeDirection) = true

  @Optional.Method(modid = cofhid)
  override def getEnergyStored(from: ForgeDirection) =
    (energy * cofhRatio).floor.toInt

  @Optional.Method(modid = cofhid)
  override def getMaxEnergyStored(from: ForgeDirection) =
    (maxEnergy * cofhRatio).floor.toInt
}

@Optional.InterfaceList(Array(
  new Optional.Interface(iface = CIEnergySink, modid = icid)
))
trait Ic2EnergySink extends CommonTilePower with IEnergySink {
  val ic2Ratio: Double

  var registered = false

  @Optional.Method(modid = icid)
  override def acceptsEnergyFrom(emitter: TileEntity, from: ForgeDirection): Boolean = true

  @Optional.Method(modid = icid)
  override def getDemandedEnergy: Double = {
    //log.info(s"IC2 demand: $energy, $maxEnergy, ${(maxEnergy - energy) * ic2Ratio}")
    (maxEnergy - energy) * ic2Ratio
  }

  @Optional.Method(modid = icid)
  override def injectEnergy(from: ForgeDirection, amount: Double, voltage: Double): Double = {
    val delta = (amount / ic2Ratio).min(maxEnergy - energy)
    //log.info(s"IC2 inject: $energy, $maxEnergy, $delta, $amount")
    energy += delta
    amount - delta * ic2Ratio
  }

  @Optional.Method(modid = icid)
  override def getSinkTier: Int = Int.MaxValue

  @Optional.Method(modid = icid)
  def load(): Unit = if(getWorldObj.isServer) {
    MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent(this))
  }

  @Optional.Method(modid = icid)
  def unload(): Unit = if(getWorldObj.isServer) {
    MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(this))
  }

  abstract override def updateEntity(): Unit = {
    super.updateEntity()
    if(!registered) {
      if(Loader.isModLoaded(icid)) load()
      registered = true
    }
  }

  abstract override def invalidate(): Unit = {
    super.invalidate()
    if(Loader.isModLoaded(icid)) {
      unload()
      registered = false
    }
  }
}

@Optional.InterfaceList(Array(
  new Optional.Interface(iface = CIPowerReceptor, modid = bcid),
  new Optional.Interface(iface = CIEnergyHandler, modid = cofhid),
  new Optional.Interface(iface = CIEnergySink, modid = icid)
))
trait PowerTile extends CommonTilePower
  with BuildcraftPowerReceptor
  with CofhEnergyHandler
  with Ic2EnergySink

