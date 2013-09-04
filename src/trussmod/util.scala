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

package rainwarrior

import language.implicitConversions
import scala.collection.mutable.ListBuffer
import net.minecraft._,
  block.Block,
  client.renderer.RenderBlocks,
  nbt.NBTTagCompound,
  tileentity.TileEntity,
  world.{ ChunkPosition, chunk, World },
  chunk.storage.ExtendedBlockStorage,
  util.{ Vec3, Vec3Pool }
import net.minecraftforge.common.ForgeDirection
import net.minecraftforge.client.model.obj.{ Face, Vertex }
import ForgeDirection._
import cpw.mods.fml.relauncher.{ SideOnly, Side }

object utils {
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

  val eps = 1F/4096F
  
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
      ret :+ ((xs.last, xs.length - start))
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

  class BlockData(var x: Float, var y: Float, var z: Float, var dirTo: ForgeDirection) {
    def writeToNBT(cmp: NBTTagCompound) {
      cmp.setFloat("x", x)
      cmp.setFloat("y", y)
      cmp.setFloat("z", z)
      cmp.setInteger("dirTo", dirTo.ordinal)
    }
    def readFromNBT(cmp: NBTTagCompound) {
      x = cmp.getFloat("x")
      y = cmp.getFloat("y")
      z = cmp.getFloat("z")
      dirTo = ForgeDirection.values()(cmp.getInteger("dirTo"))
    }
    override def toString = s"BlockData($x,$y,$z:$dirTo)"
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
  
  @inline def packCoords(x: Int, y: Int, z: Int): Long =
    (x + 30000000).toLong | (z + 30000000).toLong << 26 | y.toLong << 52

  @inline def unpackX(c: Long): Int =
    (c & ((1 << 26) - 1)).toInt - 30000000

  @inline def unpackZ(c: Long): Int =
    ((c >> 26) & ((1 << 26) - 1)).toInt - 30000000

  @inline def unpackY(c: Long): Int =
    ((c >> 52) & ((1 << 12) - 1)).toInt

  def uncheckedSetBlock(world: World, x: Int, y: Int, z: Int, id: Int, meta: Int) {
    val ch = world.getChunkFromChunkCoords(x >> 4, z >> 4)
    val arr = ch.getBlockStorageArray()
    if(arr(y >> 4) == null)
      arr(y >> 4) = new ExtendedBlockStorage(y & (~0xF), !world.provider.hasNoSky)
    arr(y >> 4).setExtBlockID(x & 0xF, y & 0xF, z & 0xF, id)
    arr(y >> 4).setExtBlockMetadata(x & 0xF, y & 0xF, z & 0xF, meta)
  }

  def uncheckedRemoveTileEntity(world: World, x: Int, y: Int, z: Int) = {
    val ch = world.getChunkFromChunkCoords(x >> 4, z >> 4)
    ch.chunkTileEntityMap.remove(new ChunkPosition(x & 0xF, y, z & 0xF))
  }

  def uncheckedAddTileEntity(world: World, x: Int, y: Int, z: Int, te: TileEntity) = {
    val ch = world.getChunkFromChunkCoords(x >> 4, z >> 4)
    ch.chunkTileEntityMap.asInstanceOf[java.util.Map[ChunkPosition, TileEntity]].
      put(new ChunkPosition(x & 0xF, y, z & 0xF), te)
  }

  def getBlockInfo(world: World, x: Int, y: Int, z: Int) = (
    world.getBlockId(x, y, z),
    world.getBlockMetadata(x, y, z),
    world.getBlockTileEntity(x, y, z))

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

  @inline def rotator(meta: Int)(x: Float, y: Float, z: Float) = ForgeDirection.values()(meta) match {
    case DOWN  => ( x, -y, -z)
    case UP    => ( x,  y,  z)
    case NORTH => ( x,  z, -y)
    case SOUTH => ( x, -z,  y)
    case WEST  => (-y,  x,  z)
    case EAST  => ( y, -x,  z)
    case _ => throw new RuntimeException("rotate to UNKNOWN direction")
  }

  @inline def rotHelper(or: Int)(x: Float, y: Float, z: Float) = (or & 3) match {
    case 0 => ( x,  y,  z)
    case 1 => ( z,  y, -x)
    case 2 => (-x,  y, -z)
    case 3 => (-z,  y,  x)
  }

  @inline def rotator2(meta: Int, or: Int)(x: Float, y: Float, z: Float) = {
    val (nx, ny, nz) = rotHelper(or)(x, y, z)
    rotator(meta)(nx, ny, nz)
  }

  final val offset = 1F/256F

  @inline def sideFixer(sideOffsets: Array[Int])(x: Float, y: Float, z: Float) = {
    (x match {
      case x if (x + .5).abs < eps => x - sideOffsets(4) * offset
      case x if (x - .5).abs < eps => x + sideOffsets(5) * offset
      case _ => x
    },
    y match {
      case y if (y + .5).abs < eps => y - sideOffsets(0) * offset
      case y if (y - .5).abs < eps => y + sideOffsets(1) * offset
      case _ => y
    },
    z match {
      case z if (z + .5).abs < eps => z - sideOffsets(2) * offset
      case z if (z - .5).abs < eps => z + sideOffsets(3) * offset
      case _ => z
    })
  }

  def packIdMeta(id: Int, meta: Int): Int = id | (meta << 12)
  def unpackIdMeta(pair: Int) = (pair & ((1 << 12) - 1), pair >> 12)

  case class Vector3(_1: Double, _2: Double, _3: Double) extends Product3[Double, Double, Double] {

    @inline def x = _1
    @inline def y = _2
    @inline def z = _3
  
    override def toString = s"Vector3(${_1},${_2},${_3})"

    def +(that: Product3[Double, Double, Double]) = Vector3(_1 + that._1, _2 + that._2, _3 + that._3)
    def -(that: Product3[Double, Double, Double]) = Vector3(_1 - that._1, _2 - that._2, _3 - that._3)
    def *(that: Double) = Vector3(_1 * that, _2 * that, _3 * that)
    def /(that: Double) = Vector3(_1 / that, _2 / that, _3 / that)
    def x(that: Product3[Double, Double, Double]) = Vector3(
      _2 * that._3 - _3 * that._2,
      _3 * that._1 - _1 * that._3,
      _1 * that._2 - _2 * that._1)
    def dot(that: Product3[Double, Double, Double]) = _1 * that._1 + _2 * that._2 + _3 * that._3

    def toVec3(pool: Vec3Pool) = pool.getVecFromPool(_1, _2, _3)

    def len = math.sqrt(_1 * _1 + _2 * _2 + _3 * _3)
    def toSide = {
      Seq(
        (-_2, 0),
        ( _2, 1),
        (-_3, 2),
        ( _3, 3),
        (-_1, 4),
        ( _1, 5)).maxBy(_._1)._2
    }

    def normal = this / len
  }
  implicit def vector3FromVertex(v: Vertex) = Vector3(v.x, v.y, v.z)
  implicit def vector3FromVec3(v: Vec3) = Vector3(v.xCoord, v.yCoord, v.zCoord)

  def sideHit(n: Vector3, p: Vector3) = {
    //println(s"n: $n, p: $p, n2: ${n + p * .001}, s: ${(n + p * .001).toSide}")
    (n + p * .001).toSide
  }

  def normal(v0: Vector3, v1: Vector3, v2: Vector3) = {
    ((v1 - v0) x (v2 - v0)).normal
  }

  // Möller–Trumbore intersection
  def mt(
      origin: Vector3,
      dir: Vector3,
      v0: Vector3,
      v1: Vector3,
      v2: Vector3,
      cullBack: Boolean = true,
      epsilon: Double = 1e-6) = {
    // 2 edges of a triangle
    val e1 = v1 - v0
    val e2 = v2 - v0
    // determinant of the equation
    val p = dir x e2
    val det = e1 dot p
    if(cullBack) {
      if(det < epsilon) None
      else {
        val t = origin - v0
        val du = t dot p
        if(du < 0.0 || du > det) None
        else {
          val q = t x e1
          val dv = dir dot q
          if(dv < 0.0 || du + dv > det) None
          else Some((e2 dot q) / det)
        }
      }
    } else {
      if(det < epsilon && det > -epsilon) None
      else {
        val invDet = 1.0 / det
        val t = origin - v0
        val u = (t dot p) * invDet
        if(u < 0.0 || u > 1.0) None
        else {
          val q = t x e1
          val v = (dir dot q) * invDet
          if(v < 0.0 || u + v > 1.0) None
          else Some((e2 dot q) * invDet)
        }
      }
    }
  }

  @SideOnly(Side.CLIENT)
  @inline
  def faceToTriangles(face: Face) =
    for (i <- 1 until (face.vertices.length - 1)) yield (
      face.vertices(0),
      face.vertices(i),
      face.vertices(i + 1))

  // raytrace seq of quads from origin towards dir
  @SideOnly(Side.CLIENT)
  def rayTraceObj(
      origin: Vector3,
      dir: Vector3,
      faces: Seq[Face]) = {
    faces.flatMap(faceToTriangles).flatMap { tr =>
      val (v0, v1, v2) = tr
      mt(origin, dir, v0, v1, v2).map(t => (t, normal(v0, v1, v2)))
    }.reduceOption(Ordering.by((_: Tuple2[Double, Vector3])._1).min)
  }
}
