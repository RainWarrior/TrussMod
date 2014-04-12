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

import java.util.{ Set => JSet }
import java.security.InvalidKeyException
import collection.JavaConversions._
import cpw.mods.fml.common.registry.GameData
import net.minecraftforge.common.util.ForgeDirection
import net.minecraft.tileentity.TileEntity
import net.minecraft.block.Block
import net.minecraft.init.Blocks
import net.minecraft.world.World
import net.minecraft.nbt.NBTTagCompound
import rainwarrior.utils._
import rainwarrior.trussmod.TrussMod.log // Hmm

trait ITileHandler {
  // before moving, starts animation
  def canMove(world: World, x: Int, y: Int, z: Int): Boolean
  // after moving, does actual move, for each moving block, sequentially
  def move(world: World, x: Int, y: Int, z: Int, dirTo: ForgeDirection): Unit
  // after all blocks from group moved, new position
  def postMove(world: World, x: Int, y: Int, z: Int): Unit
}

object MovingTileRegistry {

  val rName = raw"([\w:]+)".r
  val rNameMeta = raw"([\w:]+):(\d+)".r
  val rNameMetaM = raw"([\w:]+)m(\d+)".r

  var stickyMap = Map.empty[(String, Int), Set[(String, Int)]]

  def addStickySet(set: Set[(String, Int)]) {
    for(i <- set) stickyMap += i -> set
  }

  def addStickySet(string: String) {
    addStickySet(parseStickyString(string))
  }

  def parseStickyString(string: String) = {
    var set = Set.empty[(String, Int)]
    for(s <- string.stripPrefix("\"").stripSuffix("\"").split(',')) s match {
      case rName(name) =>
        for(m <- 0 until 16) set += ((name, m))
      case rNameMeta(name, ms) =>
        set += ((name, ms.toInt))
      case s =>
        throw new MatchError(s"Illegal set part: $s")
    }
    //println((set map unpackIdMeta).mkString)
    set
  }

  def stickyHook(world: World, x: Int, y: Int, z: Int, dirTo: ForgeDirection) = {
    val c = (x, y, z) + dirTo
    val block1 = (Block.blockRegistry.getNameForObject(world.getBlock(x, y, z)), world.getBlockMetadata(x, y, z))
    val block2 = (Block.blockRegistry.getNameForObject(world.getBlock(c.x, c.y, c.z)), world.getBlockMetadata(c.x, c.y, c.z))
    stickyMap.get(block1) match {
      case Some(set) => set(block2)
      case None => false
    }
  }

  lazy val modBlockMap = 
    (for(k <- GameData.getBlockRegistry.getKeys.asInstanceOf[JSet[String]]) yield {
      (GameData.getBlockRegistry.getObject(k).asInstanceOf[Block], k.take(k.indexOf(':')))
    }).toMap

  var blockMap = Map.empty[String, ITileHandler]
  var blockMetaMap = Map.empty[(String, Int), ITileHandler]
  var modMap = Map.empty[Option[String], ITileHandler]

  var handlerNameMap = Map(
    "default-soft" -> new DefaultTileHandler,
    "default-hard" -> new DefaultModTileHandler,
    "immovable" -> new ImmovableTileHandler)

  var defaultHandler = resolveHandler("default-hard")

  def resolveHandler(handler: String) =
    handlerNameMap.getOrElse(handler, throw new InvalidKeyException(s"No mod handler named '$handler'"))

  /*try {
    Class.forName("buildcraft.transport.PipeTransportItems")
    modMap += (Some("BuildCraft|Transport") -> BuildCraftTileHandler)
  } catch {
    case e: ClassNotFoundException =>
  }*/

  def setDefaultHandler(handler: String) {
    defaultHandler = resolveHandler(handler)
  }

  def setHandler(mod: String, handler: String) {
    val h = resolveHandler(handler)
    mod match {
      case rName(name) =>
        blockMap += name -> h
      case rNameMetaM(name, meta) =>
        blockMetaMap += (name, meta.toInt) -> h
      case "default" =>
        defaultHandler = h
      case "vanilla" =>
        modMap += None -> h
      case mod =>
        modMap += Some(mod) -> h
    }
  }

  def getHandler(block: Block, meta: Int) =  {
    val name = GameData.getBlockRegistry.getNameForObject(block)
    blockMetaMap.getOrElse((name, meta),
      blockMap.getOrElse(name, {
        modMap.getOrElse(modBlockMap.get(block), defaultHandler)
      })
    )
  }
}

class TileHandlerIdDispatcher extends ITileHandler {
  override def canMove(world: World, x: Int, y: Int, z: Int) = {
    //log.info(s"canMove: ($x, $y, $z), side: ${EffectiveSide(world)}")
    val meta = world.getBlockMetadata(x, y, z)
    world.getBlock(x, y, z) match {
      case block: Block =>
        MovingTileRegistry.getHandler(block, meta).canMove(world, x, y, z)
      case block => log.fatal(s"canMove: invalid block: $block"); false
    }
  }

  override def move(world: World, x: Int, y: Int, z: Int, dirTo: ForgeDirection) {
    //log.info(s"move: ($x, $y, $z), side: ${EffectiveSide(world)}")
    val meta = world.getBlockMetadata(x, y, z)
    world.getBlock(x, y, z) match {
      case block: Block =>
        MovingTileRegistry.getHandler(block, meta).move(world, x, y, z, dirTo)
      case block => log.fatal(s"move: invalid block: $block")
    }
  }

  override def postMove(world: World, x: Int, y: Int, z: Int) {
    //log.info(s"postMove: ($x, $y, $z), side: ${EffectiveSide(world)}")
    val meta = world.getBlockMetadata(x, y, z)
    world.getBlock(x, y, z) match {
      case block: Block =>
        MovingTileRegistry.getHandler(block, meta).postMove(world, x, y, z)
      case block => log.fatal(s"postMove: invalid block: $block")
    }
  }
}

class DefaultTileHandler extends ITileHandler {
  override def canMove(world: World, x: Int, y: Int, z: Int) = true

  override def move(world: World, x: Int, y: Int, z: Int, dirTo: ForgeDirection) {
    val (block, meta, te) = getBlockInfo(world, x, y, z)
    val WorldPos(nx, ny, nz) = (x, y, z) + dirTo
    if(te != null) {
      te.invalidate()
      uncheckedRemoveTileEntity(world, x, y, z)
    }
    uncheckedSetBlock(world, x, y, z, Blocks.air, 0)
    uncheckedSetBlock(world, nx, ny, nz, block, meta)
    if(te != null) {
      te.xCoord = nx
      te.yCoord = ny
      te.zCoord = nz
      te.validate()
      uncheckedAddTileEntity(world, nx, ny, nz, te)
    }
  }

  override def postMove(world: World, x: Int, y: Int, z: Int) {
  }
}

class DefaultModTileHandler extends ITileHandler {
  override def canMove(world: World, x: Int, y: Int, z: Int) = true

  override def move(world: World, x: Int, y: Int, z: Int, dirTo: ForgeDirection) {
    val (block, meta, te) = getBlockInfo(world, x, y, z)
    val WorldPos(nx, ny, nz) = (x, y, z) + dirTo
    val cmp = if(te != null) {
      val cmp = new NBTTagCompound
      te.writeToNBT(cmp)
      cmp.setInteger("x", nx)
      cmp.setInteger("y", ny)
      cmp.setInteger("z", nz)
      //te.invalidate()
      te.onChunkUnload()
      //uncheckedRemoveTileEntity(world, x, y, z)
      world.removeTileEntity(x, y, z)
      cmp
    } else null
    uncheckedSetBlock(world, x, y, z, Blocks.air, 0)
    uncheckedSetBlock(world, nx, ny, nz, block, meta)
    if(cmp != null) {
      TileEntity.createAndLoadEntity(cmp) match {
        case te: TileEntity =>
          //uncheckedAddTileEntity(world, nx, ny, nz, te)
          //te.validate()
          world.getChunkFromBlockCoords(nx, nz).addTileEntity(te)
        case _ =>
      }
    }
  }

  override def postMove(world: World, x: Int, y: Int, z: Int) {
  }
}

class ImmovableTileHandler extends ITileHandler {
  override def canMove(world: World, x: Int, y: Int, z: Int) = false

  override def move(world: World, x: Int, y: Int, z: Int, dirTo: ForgeDirection) {}

  override def postMove(world: World, x: Int, y: Int, z: Int) {}
}

/*object BuildCraftTileHandler extends ITileHandler {
  import buildcraft.api.transport.{ IPipeTile }
  import buildcraft.transport.{ Pipe, PipeTransportItems }

  override def canMove(world: World, x: Int, y: Int, z: Int) = {
    //log.info(s"BC canMove: ($x, $y, $z)")
    world.getBlockTileEntity(x, y, z) match {
      case te: IPipeTile =>
        true
      case _ => false
    }
  }

  override def move(world: World, x: Int, y: Int, z: Int, dirTo: ForgeDirection) {
    //log.info(s"BC move: ($x, $y, $z)")
    val (id, meta, te) = getBlockInfo(world, x, y, z)
    val WorldPos(nx, ny, nz) = (x, y, z) + dirTo
    te match {
      case pte: IPipeTile =>
        te.invalidate()
        uncheckedRemoveTileEntity(world, x, y, z)
      case _ =>
    }
    uncheckedSetBlock(world, x, y, z, 0, 0)
    uncheckedSetBlock(world, nx, ny, nz, id, meta)
    te match {
      case pte: IPipeTile => 
        te.xCoord = nx
        te.yCoord = ny
        te.zCoord = nz
        te.validate()
        uncheckedAddTileEntity(world, nx, ny, nz, te)
        pte.getPipe match {
          case pipe: Pipe => pipe.transport match {
            case tr: PipeTransportItems => for (e <- tr.travelingEntities.values; i = e.item) {
              val p = i.getPosition
              i.setPosition(
                p.x + dirTo.x,
                p.y + dirTo.y,
                p.z + dirTo.z)
            }
            case _ =>
          }
          case _ =>
        }
      case _ =>
    }
  }
}*/

/*class TMultipartTileHandler extends TileHandlerIdDispatcher {
  import codechicken.multipart.TileMultipart
  override def canMove(world: World, x: Int, y: Int, z: Int) = {
    //log.info(s"TcanMove: ($x, $y, $z), side: ${EffectiveSide(world)}")
    world.getBlockTileEntity(x, y, z) match {
      case t: TileMultipart => true
      case _ => super.canMove(world, x, y, z)
    }
  }

  override def move(world: World, x: Int, y: Int, z: Int, dirTo: ForgeDirection) {
    //log.info(s"Tmove: ($x, $y, $z), side: ${EffectiveSide(world)}")
    val (id, meta, te) = getBlockInfo(world, x, y, z)
    te match {
      case t: TileMultipart =>
        val WorldPos(nx, ny, nz) = (x, y, z) + dirTo
        //te.invalidate()
        uncheckedRemoveTileEntity(world, x, y, z)
        uncheckedSetBlock(world, x, y, z, 0, 0)
        uncheckedSetBlock(world, nx, ny, nz, id, meta)
        te.xCoord = nx
        te.yCoord = ny
        te.zCoord = nz
        //te.tileEntityInvalid = false
        //t.setValid(true)
        uncheckedAddTileEntity(world, nx, ny, nz, te)
      case _ => super.move(world, x, y, z, dirTo)
    }
  }

  override def postMove(world: World, x: Int, y: Int, z: Int) {
    //log.info(s"TpostMove: ($x, $y, $z), side: ${EffectiveSide(world)}")
    uncheckedGetTileEntity(world, x, y, z) match {
      case te: TileMultipart =>
        //te.tileEntityInvalid = true
        //te.setValid(false)
        //te.validate()
        te.onMoved()
      case _ => super.postMove(world, x, y, z)
    }
  }
}*/
