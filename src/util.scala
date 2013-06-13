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

package rainwarrior

import language.implicitConversions
import net.minecraft.tileentity.TileEntity
import net.minecraft.block.Block
import net.minecraft.world.World
import net.minecraft.client.renderer.RenderBlocks
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.ForgeDirection
import cpw.mods.fml.relauncher.{ SideOnly, Side }

object utils {
  val moveDir = Array(
    Array(3, 2, 0, 1, 2, 2),
    Array(4, 4, 4, 4, 0, 1),
    Array(2, 3, 1, 0, 3, 3),
    Array(5, 5, 5, 5, 1, 0))

  def splitLine(xs: Seq[Int], shift:Int) = {
    if(xs.isEmpty) Seq()
    else {
      var start = 0
      val ret = for {
        (x, i) <- xs.zipWithIndex
        if i > 0
        if x != xs(i - 1) + shift
      } yield {
        val size = i - start
        start = i
        (xs(i - 1), size)
      }
      ret :+ (xs.last, xs.length - start)
    }
  }

  //@SideOnly(Side.CLIENT)
  def renderInventoryBlock(rb: RenderBlocks, block: Block, metadata: Int) {
    import net.minecraft.client.renderer.Tessellator.{ instance => tes }
    import org.lwjgl.opengl.GL11._
    rb.useInventoryTint = true
    block.setBlockBoundsForItemRender()
    rb.setRenderBoundsFromBlock(block)
    glPushMatrix()
    glRotatef(90, 0, 1, 0)
    glTranslatef(-.5F, -.5F, -.5F)
    tes.startDrawingQuads()
    tes.setNormal(0, -1, 0)
    rb.renderFaceYNeg(block, 0, 0, 0, rb.getBlockIconFromSideAndMetadata(block, 0, metadata))

    if (rb.useInventoryTint) {
      val c = block.getRenderColor(metadata)
      val r = c >> 16 & 0xFF
      val g = c >> 8 & 0xFF
      val b = c & 0xFF
      tes.setColorRGBA(r, g, b, 0xFF)
    }

    tes.setNormal(0, 1, 0)
    rb.renderFaceYPos(block, 0, 0, 0, rb.getBlockIconFromSideAndMetadata(block, 1, metadata))

    if (rb.useInventoryTint) {
      tes.setColorRGBA_F(1, 1, 1, 1)
    }

    tes.setNormal(0, 0, -1)
    rb.renderFaceZNeg(block, 0, 0, 0, rb.getBlockIconFromSideAndMetadata(block, 2, metadata))
    tes.setNormal(0, 0, 1)
    rb.renderFaceZPos(block, 0, 0, 0, rb.getBlockIconFromSideAndMetadata(block, 3, metadata))
    tes.setNormal(-1, 0, 0)
    rb.renderFaceXNeg(block, 0, 0, 0, rb.getBlockIconFromSideAndMetadata(block, 4, metadata))
    tes.setNormal(1, 0, 0)
    rb.renderFaceXPos(block, 0, 0, 0, rb.getBlockIconFromSideAndMetadata(block, 5, metadata))
    tes.draw()
    glPopMatrix()
  }

  class BlockData(var x: Float, var y: Float, var z: Float) {
    def writeToNBT(cmp: NBTTagCompound) {
      cmp.setFloat("x", x)
      cmp.setFloat("y", y)
      cmp.setFloat("z", z)
    }
    def readFromNBT(cmp: NBTTagCompound) {
      x = cmp.getFloat("x")
      y = cmp.getFloat("y")
      z = cmp.getFloat("z")
    }
    override def toString = s"BlockData($x,$y,$z)"
  }

  object EffectiveSide {
    def apply(world: World) = (world.isRemote) match {
      case true => Client
      case false => Server
    }
    def apply(side: Side) = (side: @unchecked) match { // ignore Side.BUKKIT error
      case Side.CLIENT => Client
      case Side.SERVER => Server
      //case Side.BUKKIT => Bukkit
    }
  }
  implicit def toEffectiveSide(world: World) = EffectiveSide(world)
  implicit def toEffectiveSide(side: Side) = EffectiveSide(side)
  implicit def toSide(side: EffectiveSide) = side match {
    case Client => Side.CLIENT
    case Server => Side.SERVER
    //case Bukkit => Side.BUKKIT
  }
  sealed abstract class EffectiveSide {
    def isClient = this match {
      case Client => true
      case _ => false
    }
    def isServer = !isClient
    override def toString = this match {
      case Client => "Client"
      case Server => "Server"
      //case Bukkit => "Bukkit"
    }
  }
  object Client extends EffectiveSide
  object Server extends EffectiveSide
  //object Bukkit extends EffectiveSide
  
  
  implicit def worldPosFromTileEntity(te: TileEntity) = WorldPos(te.xCoord, te.yCoord, te.zCoord)
  implicit def worldPosFromProduct3(tup: Product3[Int, Int, Int]) = WorldPos(tup._1, tup._2, tup._3)
  implicit def worldPosFromForgeDirection(dir: ForgeDirection) = WorldPos(dir.offsetX, dir.offsetY, dir.offsetZ)

  object WorldPos {
  
    import ForgeDirection._
    val downStep  = DOWN
    val upStep    = UP
    val northStep = NORTH
    val southStep = SOUTH
    val westStep  = WEST
    val eastStep  = EAST
    def apply(te: TileEntity): WorldPos = apply(te.xCoord, te.yCoord, te.zCoord)
    def apply(tup: Product3[Int, Int, Int]): WorldPos = apply(tup._1, tup._2, tup._3)
    def apply(dir: ForgeDirection): WorldPos = apply(dir.offsetX, dir.offsetY, dir.offsetZ)
    def apply(dir: ForgeDirection, normal: Tuple2[Int, Int], basis: Int): WorldPos = dir match {
      case DOWN  => (normal._1, basis, normal._2)
      case UP    => (normal._1, basis, normal._2)
      case NORTH => (normal._1, normal._2, basis)
      case SOUTH => (normal._1, normal._2, basis)
      case WEST  => (basis, normal._1, normal._2)
      case EAST  => (basis, normal._1, normal._2)
      case _ => throw new RuntimeException("WorldPos.apply from UNKNOWN direction")
    }
  }
  
  case class WorldPos(_1: Int, _2: Int, _3: Int) extends Product3[Int, Int, Int] {
    import ForgeDirection._

    @inline def x = _1
    @inline def y = _2
    @inline def z = _3
  
    def +(that: Product3[Int, Int, Int]) = WorldPos(_1 + that._1, _2 + that._2, _3 + that._3)
    def -(that: Product3[Int, Int, Int]) = WorldPos(_1 - that._1, _2 - that._2, _3 - that._3)
    def *(that: Int) = WorldPos(_1 * that, _2 * that, _3 * that)
  
    def incx(inc: Int) = WorldPos(_1 + inc, _2, _3)
    def decx(inc: Int) = WorldPos(_1 - inc, _2, _3)
    def incy(inc: Int) = WorldPos(_1, _2 + inc, _3)
    def decy(inc: Int) = WorldPos(_1, _2 - inc, _3)
    def incz(inc: Int) = WorldPos(_1, _2, _3 + inc)
    def decz(inc: Int) = WorldPos(_1, _2, _3 - inc)
  
    def toTuple = (_1, _2, _3)
    def toSeq = Seq[Int](_1, _2, _3)
    override def toString = s"WorldPos(${_1},${_2},${_3})"

    def normal(dir: ForgeDirection) = dir match {
      case DOWN  => (_1, _3)
      case UP    => (_1, _3)
      case NORTH => (_1, _2)
      case SOUTH => (_1, _2)
      case WEST  => (_2, _3)
      case EAST  => (_2, _3)
      case _ => throw new RuntimeException("WorldPos.normal from UNKNOWN direction")
    }
    def basis(dir: ForgeDirection) = dir match {
      case DOWN  => _2
      case UP    => _2
      case NORTH => _3
      case SOUTH => _3
      case WEST  => _1
      case EAST  => _1
      case _ => throw new RuntimeException("WorldPos.basis from UNKNOWN direction")
    }
  }
  
  /*object Util {
    object SmallCoords {
      final val shift = 10
      final val mask = Math.pow(2, shift)
      def apply(x: Int) = new SmallCoords(x)
    }
    class SmallCoords(val c: Int) {
      override def hashCode = c.hashCode
      override def equals(other: Any) = other match {
        case that: SmallCoords => this.c == c
        case _ => false
      }
      def +(other: SmallCoords) {
          val x1, x2, y1, y2, z1, z2 = for(i <- 0 to 2; c <- (this.c, other.c)) yield {
            (c & mask) >> (shift & i)
          }
        }
        return SmallCoords(this.c + other.c)
      }
    }
  }*/
}
