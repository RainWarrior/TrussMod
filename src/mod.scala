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

import scala.collection.mutable.ListBuffer
import java.util.logging.Logger
import java.io.File
import cpw.mods.fml.{ common, relauncher }
import common.{ Mod, event, Loader, network, FMLCommonHandler, SidedProxy }
import relauncher.{ FMLRelaunchLog, Side }
import network.NetworkMod
import net.minecraftforge.common.Configuration
import TrussMod._

trait LoadLater extends DelayedInit {
  var stuff = new ListBuffer[() => Unit]
  var fired = false

  def delayedInit(code: => Unit) {
    println(f"onInit: ${getClass.getName}")
    //Thread.dumpStack
    if(!fired) {
      stuff += (() => code)
    } else {
      code
    }
  }

  def init() {
    println(f"doInit: ${getClass.getName}")
    fired = true
    stuff.toList.foreach(_())
  }
}

object CommonProxy extends LoadLater {
  import net.minecraft.{ block, item },
    block.material,
    block.{ Block, BlockContainer},
    item.Item,
    material.Material
  import cpw.mods.fml.common.registry._

  config.load()
  
  val debugItemId = config.getItem("debugItem", 5000).getInt()
  object debugItem
    extends Item(debugItemId)
    with DebugItem
  debugItem

  val blockFrameId = config.getBlock("frame", 501).getInt()
  object blockFrame
    extends Block(blockFrameId, Material.ground)
    with BlockFrame
  blockFrame

  val blockMotorId = config.getBlock("motor", 502).getInt()
  object blockMotor
    extends BlockContainer(blockMotorId, Material.ground)
    with BlockMotor
  blockMotor

  val blockMovingStripId = config.getBlock("movingStrip", 503, "Util block, shouldn't be used in the normal game").getInt()
  object blockMovingStrip
    extends BlockContainer(blockMovingStripId, Material.ground)
    with BlockMovingStrip
  blockMovingStrip

  config.save()

  TickRegistry.registerTickHandler(rainwarrior.hooks.RenderTickHandler, Side.CLIENT)
}
object ClientProxy extends LoadLater {
  import cpw.mods.fml.client.registry._
  rainwarrior.hooks.MovingTileEntityRenderer
  ClientRegistry.bindTileEntitySpecialRenderer(classOf[TileEntityMotor], TileEntityMotorRenderer)
  RenderingRegistry.registerBlockHandler(BlockFrameRenderer)
  net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(rainwarrior.hooks.MovingRegistry)
}

sealed class CommonProxy
class ClientProxy extends CommonProxy {
  CommonProxy.delayedInit(ClientProxy.init())
}

@Mod(
  modLanguage = "scala",
  modid = modId,
  name = modName,
  version = "0.01",
  dependencies = "required-after:Forge@[7.8.0.701,);required-after:FML@[5.2.10,)"
)
@NetworkMod(
//  channels = Array(modId),
  clientSideRequired = true,
  serverSideRequired = false
)
object TrussMod {
  final val modId = "TrussMod"
  final val modName = "Truss Mod"

  val log = Logger.getLogger(modId)
  log.setParent(FMLRelaunchLog.log.getLogger)

  lazy val config = new Configuration(new File(Loader.instance.getConfigDir, modId + ".cfg"))
  def isServer() = FMLCommonHandler.instance.getEffectiveSide.isServer

  @SidedProxy(
    clientSide="rainwarrior.trussmod.ClientProxy",
    serverSide="rainwarrior.trussmod.CommonProxy")
  var proxy: CommonProxy = null

  CommonProxy.delayedInit {
    log.info("Copyright (C) 2013 RainWarrior")
    log.info("See included LICENSE file for the license (GPLv3+ with additional terms)")
    log.info("TrussMod is free software: you are free to change and redistribute it.")
    log.info("There is NO WARRANTY, to the extent permitted by law.")
  }

  @Mod.PreInit def preinit(e: event.FMLPreInitializationEvent) {}
  @Mod.Init def init(e: event.FMLInitializationEvent) = CommonProxy.init()
}

