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

import collection.mutable.ListBuffer
import collection.JavaConversions._
import java.util.logging.Logger
import java.io.File
import cpw.mods.fml.{ common, relauncher }
import common.{ Mod, event, Loader, network, FMLCommonHandler, SidedProxy }
import relauncher.{ FMLRelaunchLog, Side }
import network.NetworkMod
import net.minecraftforge.common.{ Configuration, Property }
import rainwarrior.hooks.{ MovingRegistry, MovingTileRegistry, TileHandlerIdDispatcher, HelperRenderer }
import rainwarrior.utils._
import TrussMod._

object CommonProxy extends LoadLater {
  import net.minecraft.{ block, item },
    block.material,
    block.{ Block, BlockContainer},
    item.Item,
    material.Material
  import cpw.mods.fml.common.registry._

  val hasImmibis = try {
    Class.forName("mods.immibis.core.api.multipart.util.BlockMultipartBase")
    Class.forName("mods.immibis.microblocks.api.util.TileCoverableBase")
    log.info("Found Immibis's microblocks")
    true
  } catch {
    case e: ClassNotFoundException =>
      false
  }

  val hasChickenBones = try {
    Class.forName("codechicken.multipart.TileMultipart")
    Class.forName("codechicken.microblock.CommonMicroblock")
    log.info("Found ChickenBones's microblocks")
    true
  } catch {
    case e: ClassNotFoundException =>
      false
  }
  
  val frameBlockProxy = hasImmibis match {
    case true =>
      Class.forName("rainwarrior.trussmod.ImmibisProxy").newInstance.asInstanceOf[FrameBlockProxy]
    case _ =>
      new FrameBlockProxy
  }

  val frameItemProxy = hasChickenBones match {
    case true =>
      Class.forName("rainwarrior.trussmod.ChickenBonesProxy").newInstance.asInstanceOf[FrameItemProxy]
    case _ =>
      new FrameItemProxy
  }

  import rainwarrior.hooks.{ ITileHandler, MovingTileRegistry }
  val movingTileHandler: ITileHandler = hasChickenBones match {
    case true =>
      Class.forName("rainwarrior.hooks.TileHandlerIdDispatcher").newInstance.asInstanceOf[ITileHandler]
    case false =>
      new TileHandlerIdDispatcher
  }

  config.load()
  

  val structureLimit = config.get("Main", "structure_limit", 4096, "Maximum number of blocks in one structure").getInt()

  val blockFrameId = config.getBlock("frame", 501).getInt()
  val frameBlock = frameBlockProxy.init()
  val frameItem = frameItemProxy.init()

  val blockMotorId = config.getBlock("motor", 502).getInt()
  object blockMotor
    extends BlockContainer(blockMotorId, Material.iron)
    with BlockMotor
  blockMotor

  val blockMovingStripId = config.getBlock("movingStrip", 503, "Util block, shouldn't be used in the normal game").getInt()
  object blockMovingStrip
    extends BlockContainer(blockMovingStripId, Material.iron)
    with BlockMovingStrip
  blockMovingStrip

  val handlers = config.getCategory("Mod Handlers")
  handlers.setComment("""
Per-mod moving handlers configuration (ADVANCED)
Default values:
  "default-hard" - Performs NBT read-write combo. Robust but expensive, known to cause time bugs.
  "default-soft" - Performs invalidate-move-validate combo. Works well for mods that access TileEntity coords only via *Coods fields, and don't cache them elsewhere. Also works fine for vanilla blocks.
  "immovable" - For mods that shouldn't move their blocks for some reason
Default keys:
  "default" - Default handler, if no other found
  "vanilla" - Handler for vanilla blocks
Other keys can be:
  ID strings of mods
  block IDs
  ID-metadata pairs in form of IDmMETA (example: 35m1 - orange wool)
""")

  val defaulthandlers = List(
    ("default", "default-hard"),
    ("vanilla", "default-soft"),
    ("TrussMod", "default-soft"),
    ("ComputerCraft", "default-soft"),
    ("EnderStorage", "default-soft"),
    ("ChickenChunks", "default-soft"),
    ("Translocator", "default-soft")
  )

  if(!handlers.containsKey("default")) {
    for((k, v) <- defaulthandlers if !handlers.containsKey(k))
      handlers.put(k, new Property(k, v, Property.Type.STRING))
  }

  for(k <- handlers.keySet) {
    val v = handlers.get(k).getString
    MovingTileRegistry.setHandler(k, v)
  }

  val sets = config.getCategory("Sticky Sets")
  sets.setComment("""
Sets of blocks that move together (multiblock structures) (ADVANCED)
""")

  val defaultSets = List(
    ("bed", "26"),
    ("wooden_door", "64"),
    ("iron_door", "71"))

  for((k, v) <- defaultSets  if !sets.containsKey(k))
    sets.put(k, new Property(k, v, Property.Type.STRING))

  for(k <- sets.keySet) {
    val v = sets.get(k).getString
    //println(s"$k, $v")
    MovingTileRegistry.addStickySet(v)
  }

  config.save()

}

object ClientProxy extends LoadLater {
  import cpw.mods.fml.common.registry._
  import cpw.mods.fml.client.registry._
  import net.minecraftforge.event.ForgeSubscribe
  import net.minecraftforge.client.event.TextureStitchEvent
  import net.minecraftforge.common.MinecraftForge.EVENT_BUS

  TickRegistry.registerTickHandler(rainwarrior.hooks.RenderTickHandler, Side.CLIENT)
  ClientRegistry.bindTileEntitySpecialRenderer(classOf[TileEntityMotor], TileEntityMotorRenderer)
  RenderingRegistry.registerBlockHandler(BlockMotorRenderer)
  RenderingRegistry.registerBlockHandler(BlockFrameRenderer)
  EVENT_BUS.register(HelperRenderer)
  EVENT_BUS.register(this)

  val motorIconNames = Array(List("Base", "Gear", "Frame").map("Motor" + _): _*)

  @ForgeSubscribe
  def registerIcons(e: TextureStitchEvent.Pre) = if(e.map.textureType == 0) {
    for (name <- motorIconNames) model.loadIcon(e.map, name)
    model.loadIcon(e.map, "BlockFrame")
  }
}

sealed class CommonProxy {
  def renderer() {}
}

class ClientProxy extends CommonProxy {
  override lazy val renderer: Unit = new rainwarrior.hooks.MovingTileEntityRenderer
  CommonProxy.delayedInit(ClientProxy.init())
}

@Mod(
  modLanguage = "scala",
  modid = modId,
  name = modName,
  version = "beta",
  dependencies = "required-after:Forge@[7.8.0.701,);required-after:FML@[5.2.6.701,)"
)
@NetworkMod(
//  channels = Array(modId),
  clientSideRequired = true,
  serverSideRequired = false
)
object TrussMod {
  @inline final val modId = "TrussMod"
  @inline final val modName = modId

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

  @Mod.EventHandler def preinit(e: event.FMLPreInitializationEvent) { proxy.renderer }
  @Mod.EventHandler def init(e: event.FMLInitializationEvent) = CommonProxy.init()
}

