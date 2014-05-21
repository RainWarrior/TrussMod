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

import net.minecraft.{ block, item },
  block.material,
  block.{ Block, BlockContainer},
  item.{ Item, ItemBlock },
  material.Material
import collection.mutable.ListBuffer
import collection.JavaConversions._
import annotation.meta.{ companionClass, companionObject }
//import java.util.logging.Logger
import org.apache.logging.log4j.{ Logger, LogManager }
import java.io.File
import cpw.mods.fml.{ common, relauncher }
import common.{ Mod, event, eventhandler, Loader, Optional, FMLCommonHandler, SidedProxy }
import eventhandler.SubscribeEvent
import relauncher.{ FMLRelaunchLog, Side }
import net.minecraftforge.common.config.{ Configuration, Property }
import rainwarrior.hooks.{
  ITileHandler,
  MovingRegistry,
  MovingTileRegistry,
  TileHandlerIdDispatcher,
  TMultipartTileHandler,
  HelperRenderer
}
import rainwarrior.utils._
import TrussMod._

object TrussMod {
  import TrussModInstance.proxy

  val log = LogManager.getLogger(modId)

  log.info("Copyright (C) 2012 - 2014 RainWarrior")
  log.info("See included LICENSE file for the license (GPLv3+ with additional terms)")
  log.info("TrussMod is free software: you are free to change and redistribute it.")
  log.info("There is NO WARRANTY, to the extent permitted by law.")

  @inline final val modId = "TrussMod"
  @inline final val tileChannel = "TrussModTileData"
  @inline final val modName = modId

  import cpw.mods.fml.common.registry._

  val config = new Configuration(new File(Loader.instance.getConfigDir, modId + ".cfg"))
  config.load()
  
  val structureLimit = config.get("Main", "structure_limit", 4096, "Maximum number of blocks in one structure").getInt()

  //val blockFrameId = config.getBlock("frame", 501).getInt()
  val frameItemClass = proxy.genFrameItem()
  val frameBlock = proxy.genFrameBlock()
  proxy.postFrameHook()

  //val blockMotorId = config.getBlock("motor", 502).getInt()
  val blockMotor = new BlockMotor

  //val blockMovingStripId = config.getBlock("movingStrip", 503, "Util block, shouldn't be used in the normal game").getInt()
  val blockMovingStrip = new BlockMovingStrip(Material.iron)

  //val debugItemId = config.getItem("debug", 504).getInt
  val debugItem = new DebugItem

  val handlers = config.getCategory("Mod Handlers")
  handlers.setComment("""
Per-mod moving handlers configuration (ADVANCED)
Default values:
  "default-hard" - Performs NBT read-write combo. Robust but expensive, known to cause time bugs.
  "default-soft" - Performs invalidate-move-validate combo. Works well for mods that access TileEntity coords only via *Coods fields, and don't cache them elsewhere. Also works fine for vanilla blocks.
  "immovable" - For mods that shouldn't move their blocks for some reason
Default keys:
  "default" - Default handler, if no other found
  "mod:minecraft" - Handler for vanilla blocks
Other keys can be:
  mod IDs
  block names
  name-metadata pairs in form of <name>m<meta> (example: minecraft:woolm1 or woolm1 - orange wool)
""")

  val defaulthandlers = List(
    ("default", "default-hard"),
    ("mod:minecraft", "default-soft"),
    ("mod:TrussMod", "default-soft"),
    ("mod:ComputerCraft", "default-soft"),
    ("mod:EnderStorage", "default-soft"),
    ("mod:ChickenChunks", "default-soft"),
    ("mod:Translocator", "default-soft")
  )

  if(!handlers.containsKey("default")) {
    for((k, v) <- defaulthandlers if !handlers.containsKey(k))
      handlers.put(k, new Property(k, v, Property.Type.STRING))
  }

  for(k <- handlers.keySet) {
    val v = handlers.get(k).getString
    MovingTileRegistry.setHandler(k, v)
  }

  //MovingTileRegistry.setHandler(blockMovingStripId.toString, "immovable")

  val sets = config.getCategory("Sticky Sets")
  sets.setComment("""
Sets of blocks that move together (multiblock structures) (ADVANCED)
""")

  val defaultSets = Map(
    "bed" -> "bed",
    "wooden_door" -> "wooden_door",
    "iron_door" -> "iron_door"
  )

  for((k, v) <- defaultSets  if !sets.containsKey(k))
    sets.put(k, new Property(k, v, Property.Type.STRING))

  for(k <- sets.keySet) {
    val v = sets.get(k).getString
    //println(s"$k, $v")
    MovingTileRegistry.addStickySet(v)
  }

  val power = config.getCategory("Power")
  power.setComment("Power consumption and conversion coefficients. The cost of moving n blocks is moveCost + n * moveCostMultiplier")

  val defaultPowers = Map(
    "motorCapacity" -> 5000D,
    "moveCost" -> 200D,
    "moveCostMultiplier" -> 50D,
    "bcRatio" -> 1.0,
    "cofhRatio" -> 0.1,
    "ic2Ratio" -> 0.5
  )

  for((k, v) <- defaultPowers if !power.containsKey(k))
    power.put(k, new Property(k, v.toString, Property.Type.DOUBLE))

  val Seq(motorCapacity, moveCost, moveCostMultiplier, bcRatio, cofhRatio, ic2Ratio) =
    Seq("motorCapacity", "moveCost", "moveCostMultiplier", "bcRatio", "cofhRatio", "ic2Ratio") map { k =>
      val p = power.get(k).getDouble(defaultPowers(k)).max(0D)
      power.put(k, new Property(k, p.toString, Property.Type.DOUBLE))
      p
    }

  config.save()

  proxy.updateTileHandler()

  model.loadModel(log, modId, "Frame")
  model.loadModel(log, modId, "Motor")

  //StatePacketHandler
}

object TrussModClient {
  import cpw.mods.fml.common.registry._
  import cpw.mods.fml.client.registry._
  import net.minecraftforge.client.event.TextureStitchEvent
  import net.minecraftforge.common.MinecraftForge.EVENT_BUS

  FMLCommonHandler.instance.bus.register(rainwarrior.hooks.RenderTickHandler)
  ClientRegistry.bindTileEntitySpecialRenderer(classOf[TileEntityMotor], TileEntityMotorRenderer)
  RenderingRegistry.registerBlockHandler(BlockMotorRenderer)
  RenderingRegistry.registerBlockHandler(BlockFrameRenderer)
  EVENT_BUS.register(HelperRenderer)
  EVENT_BUS.register(this)

  val motorIconNames = Array(List("Base", "Gear", "Frame").map("Motor" + _): _*)

  @SubscribeEvent
  def registerIcons(e: TextureStitchEvent.Pre) = if(e.map.getTextureType == 0) {
    for (name <- motorIconNames) model.loadIcon(log, e.map, modId, name)
    model.loadIcon(log, e.map, modId, "BlockFrame")
  }
}

class ProxyParent {
  def genFrameBlock(): Block =
    new BlockFrame

  def genFrameItem(): Class[_ <: ItemBlock] =
    classOf[ItemBlock]

  def postFrameHook(): Unit = {}

  def updateTileHandler(): Unit = {}
}

@Optional.InterfaceList(Array())
class CommonProxy extends ProxyParent {

  def preInit(): Unit = TrussMod

  @Optional.Method(modid = "ImmibisMicroblocks")
  override def genFrameBlock(): Block =
    new BlockImmibisFrame

  @Optional.Method(modid = "ForgeMultipart")
  override def genFrameItem(): Class[_ <: ItemBlock] =
    classOf[ChickenBonesFrameItem]

  @Optional.Method(modid = "ForgeMultipart")
  override def postFrameHook(): Unit = {
    import codechicken.multipart.{ MultiPartRegistry, MultipartGenerator }
    MultipartGenerator.registerPassThroughInterface("rainwarrior.trussmod.Frame")
    MultiPartRegistry.registerParts((_, _) => new ChickenBonesFramePart, "Frame")
    MultiPartRegistry.registerConverter(ChickenBonesPartConverter)
  }

  @Optional.Method(modid = "ForgeMultipart")
  override def updateTileHandler(): Unit = {
    MovingTileRegistry.rootHandler = new TMultipartTileHandler(MovingTileRegistry.rootHandler)
  }
}

@Optional.InterfaceList(Array())
class ClientProxy extends CommonProxy {
  override def preInit(): Unit = TrussModClient
}

@Mod(
  modLanguage = "scala",
  modid = modId,
  name = modName,
  version = "beta",
  dependencies = "required-after:Forge@[7.8.0.923,);required-after:FML@[5.2.6.923,);after:ImmibisMicroblocks;after:ForgeMultipart"
)
object TrussModInstance {
  @SidedProxy(
    clientSide = "rainwarrior.trussmod.ClientProxy",
    serverSide = "rainwarrior.trussmod.CommonProxy",
    modId = modId)
  var proxy: CommonProxy = null

  @Mod.EventHandler def preinit(e: event.FMLPreInitializationEvent) = proxy.preInit()
}

