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

import collection.JavaConversions._
import net.minecraftforge.common.ForgeDirection
import net.minecraft.tileentity.TileEntity
import net.minecraft.block.Block
import net.minecraft.world.World
import net.minecraft.nbt.NBTTagCompound
import rainwarrior.utils._
import rainwarrior.trussmod.TrussMod.log // Hmm

trait ITileHandler {
  def canMove(world: World, x: Int, y: Int, z: Int): Boolean
  def move(world: World, x: Int, y: Int, z: Int, dirTo: ForgeDirection): Unit
}

object MovingTileRegistry extends ITileHandler {
  lazy val blockRegistry = {
    import cpw.mods.fml.common.{ ModContainer, registry } 
    import registry.{ BlockProxy, GameRegistry }
    import com.google.common.collect.Multimap
    val field = classOf[GameRegistry].getDeclaredField("blockRegistry")
    field.setAccessible(true)
    field.get(null).asInstanceOf[Multimap[ModContainer, BlockProxy]]
  }

  lazy val blockMap = 
    (for(e <- blockRegistry.entries; k = e.getKey; v = e.getValue) yield {
      (v.asInstanceOf[Block], k.getModId)
    }).toMap

  var handlerMap = Map.empty[Option[String], ITileHandler]

  handlerMap += None -> DefaultTileHandler
  handlerMap += Some("TrussMod") -> DefaultTileHandler
  /*try {
    Class.forName("buildcraft.transport.PipeTransportItems")
    handlerMap += (Some("BuildCraft|Transport") -> BuildCraftTileHandler)
  } catch {
    case e: ClassNotFoundException =>
  }*/

  def getHandler(block: Block) = 
    handlerMap.getOrElse(blockMap.get(block), DefaultModTileHandler)

  override def canMove(world: World, x: Int, y: Int, z: Int) = {
    //log.info(s"canMove: ($x, $y, $z)")
    val id = world.getBlockId(x, y, z)
    Block.blocksList(id) match {
      case block: Block =>
        getHandler(block).canMove(world, x, y, z)
      case block => log.severe(s"canMove: invalid block: $block"); false
    }
  }

  override def move(world: World, x: Int, y: Int, z: Int, dirTo: ForgeDirection) {
    val id = world.getBlockId(x, y, z)
    Block.blocksList(id) match {
      case block: Block =>
        getHandler(block).move(world, x, y, z, dirTo)
      case block => log.severe(s"move: invalid block: $block")
    }
  }
}

object DefaultTileHandler extends ITileHandler {
  override def canMove(world: World, x: Int, y: Int, z: Int) = true

  override def move(world: World, x: Int, y: Int, z: Int, dirTo: ForgeDirection) {
    val (id, meta, te) = getBlockInfo(world, x, y, z)
    val WorldPos(nx, ny, nz) = (x, y, z) + dirTo
    if(te != null) {
      te.invalidate()
      uncheckedRemoveTileEntity(world, x, y, z)
    }
    uncheckedSetBlock(world, x, y, z, 0, 0)
    uncheckedSetBlock(world, nx, ny, nz, id, meta)
    if(te != null) {
      te.xCoord = nx
      te.yCoord = ny
      te.zCoord = nz
      te.validate()
      uncheckedAddTileEntity(world, nx, ny, nz, te)
    }
  }
}

object DefaultModTileHandler extends ITileHandler {
  override def canMove(world: World, x: Int, y: Int, z: Int) = true

  override def move(world: World, x: Int, y: Int, z: Int, dirTo: ForgeDirection) {
    val (id, meta, te) = getBlockInfo(world, x, y, z)
    val WorldPos(nx, ny, nz) = (x, y, z) + dirTo
    val cmp = if(te != null) {
      val cmp = new NBTTagCompound
      te.writeToNBT(cmp)
      cmp.setInteger("x", nx)
      cmp.setInteger("y", ny)
      cmp.setInteger("z", nz)
      //te.invalidate()
      //uncheckedRemoveTileEntity(world, x, y, z)
      world.removeBlockTileEntity(x, y, z)
      cmp
    } else null
    uncheckedSetBlock(world, x, y, z, 0, 0)
    uncheckedSetBlock(world, nx, ny, nz, id, meta)
    if(cmp != null) {
      TileEntity.createAndLoadEntity(cmp) match {
        case te: TileEntity =>
          //uncheckedAddTileEntity(world, nx, ny, nz, te)
          //te.validate()
          world.setBlockTileEntity(nx, ny, nz, te)
        case _ =>
      }
    }
  }
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
