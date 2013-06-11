package rainwarrior.trussmod

import scala.collection.mutable.ListBuffer
import java.util.logging.Logger
import cpw.mods.fml.{ common, relauncher }
import common.{ Mod, event, network, FMLCommonHandler, SidedProxy }
import relauncher.{ FMLRelaunchLog, Side }
import network.NetworkMod
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

  val debugItemId = 5000
  object debugItem
    extends Item(debugItemId)
    with DebugItem
  debugItem

  val blockFrameId = 501
  object blockFrame
    extends Block(blockFrameId, Material.ground)
    with BlockFrame
  blockFrame

  val blockMotorId = 502
  object blockMotor
    extends BlockContainer(blockMotorId, Material.ground)
    with BlockMotor
  blockMotor

  val movingStripBlockId = 503
  object blockMovingStrip
    extends BlockContainer(movingStripBlockId, Material.ground)
    with BlockMovingStrip
  blockMovingStrip

  TickRegistry.registerTickHandler(rainwarrior.hooks.RenderTickHandler, Side.CLIENT)
}
object ClientProxy extends LoadLater {
  import cpw.mods.fml.client.registry._
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
  version = "0.01"
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

  def isServer() = FMLCommonHandler.instance.getEffectiveSide.isServer

  @SidedProxy(
    clientSide="rainwarrior.trussmod.ClientProxy",
    serverSide="rainwarrior.trussmod.CommonProxy")
  var proxy: CommonProxy = null

  CommonProxy.delayedInit {
    println("Hello, World from TrussMod AND FRIENDS!")
  }

  @Mod.PreInit def preinit(e: event.FMLPreInitializationEvent) {}
  @Mod.Init def init(e: event.FMLInitializationEvent) = CommonProxy.init()
}

