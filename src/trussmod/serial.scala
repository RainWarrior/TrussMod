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
package rainwarrior.serial

import collection.mutable.ArrayBuilder
import collection.JavaConversions._
import scala.reflect.{io => _, _}
import annotation.tailrec
import language.higherKinds

import java.util.{ Collection, List => JList }
import java.nio.ByteBuffer
import java.nio.charset.Charset

trait IsSerialSink[F] {

  def toSerialList(list: F*): F

  def toSerialMap(map: (F, F)*): F

  // should probably have primitives + string
  def toSerial[A](v: A): F

  def addTag(f: F, tag: String): F // name for nbt, tag for edn

  //def append(list: F, f: F): F

  //def append(map: F, k: F, v: F): F
}

trait IsSerialSource[F] {

  def fromSerialList(f: F): Seq[F]

  def fromSerialMap(f: F): Seq[(F, F)]

  def fromSerialByte(f: F): Byte
  def fromSerialShort(f: F): Short
  def fromSerialInt(f: F): Int
  def fromSerialLong(f: F): Long
  def fromSerialFloat(f: F): Float
  def fromSerialDouble(f: F): Double
  def fromSerialBoolean(f: F): Boolean
  def fromSerialChar(f: F): Char
  def fromSerialString(f: F): String

  def removeTag(f: F): (F, String)

  //def extract(list: F): (F, F) // f, rest; works like stack, efficiency reasons

  //def extract(map: F, k: F): (F, F) // v, rest; useful?
}

trait IsSerialFormat[F] extends IsSerialSink[F] with IsSerialSource[F]

object SerialFormats {
  val stringClass = classTag[String].runtimeClass

  import net.minecraft.nbt._

  object nbtSerialInstance extends IsSerialFormat[NBTBase] {

    def toSerialList(list: NBTBase*): NBTBase = {
      if(list.forall(t => t.isInstanceOf[NBTTagInt])) {
        val arr = new ArrayBuilder.ofInt
        list.foreach { case t: NBTTagInt => arr += t.func_150287_d }
        new NBTTagIntArray(arr.result)
      } else if(list.forall(t => t.isInstanceOf[NBTTagByte])) {
        val arr = new ArrayBuilder.ofByte
        list.foreach { case t: NBTTagByte => arr += t.func_150290_f }
        new NBTTagByteArray(arr.result)
      } else {
        val res = new NBTTagCompound
        res.setString("$serial.type", "list")
        var i = 0
        list.foreach { t =>
          //res.setString(s"t$i", t.getName)
          res.setTag(s"$i", t)
          i += 1
        }
        res
      }
    }

    def toSerialMap(map: (NBTBase, NBTBase)*): NBTBase = {
      val res = new NBTTagCompound
      if(map.forall(_._1.isInstanceOf[NBTTagString])) { // loses tags, direct nbt compound
        res.setString("$serial.type", "mapCompact")
        map.foreach { case (k, v) =>
          res.setTag(k.toString, v)
        }
      } else {
        res.setString("$serial.type", "map")
        var i = 0
        map.foreach { case (k, v) =>
          //res.setString(s"tk$i", k.getName)
          res.setTag(s"k$i", k)
          //res.setString(s"tv$i", v.getName)
          res.setTag(s"v$i", v)
          i += 1
        }
      }
      res
    }

    def toSerial[A](v: A): NBTBase = v match { // ugly-ish
      case v: Byte    => new NBTTagByte(v)
      case v: Short   => new NBTTagShort(v)
      case v: Int     => new NBTTagInt(v)
      case v: Long    => new NBTTagLong(v)
      case v: Float   => new NBTTagFloat(v)
      case v: Double  => new NBTTagDouble(v)
      case v: Boolean => new NBTTagByte(if(v) 1 else 0)
      case v: Char    => new NBTTagString(v.toString)
      case v: String  => new NBTTagString(v)
      case _ => ???
    }

    def addTag(t: NBTBase, tag: String) = {
      /*if (t.getName == "") {
        val res = t.copy
        res.setName(tag)
        res
      } else*/ ???
    }

    /*def append(seq: NBTBase, f: NBTBase): NBTBase = seq match {
      case seq: NBTTagCompound if seq.getString("$serial.type") == "list" =>
        val res = seq.copy.asInstanceOf[NBTTagCompound]
        val i = res.getTags.size - 1
        res.setString(s"t$i", f.getName)
        res.setTag(s"$i", f)
        res
      case _ => throw new IllegalArgumentException(s"tag isn't a compound list: $seq")
    }

    def append(map: NBTBase, k: NBTBase, v: NBTBase): NBTBase = map match {
      case map: NBTTagCompound if map.getString("$serial.type") == "map" =>
        val res = seq.copy.asInstanceOf[NBTTagCompound]
        val i = (res.getTags.size - 1) / 2
        res.setString(s"tk$i", k.getName)
        res.setTag(s"k$i", k)
        res.setString(s"tv$i", v.getName)
        res.setTag(s"v$i", v)
        res
      case map: NBTTagCompound if map.getString("$serial.type") == "mapCompact" && k.isInstanceOf[NBTTagString] =>
        val res = seq.copy.asInstanceOf[NBTTagCompound]
        res.setTag(k.toString, v)
        res
      case _ => throw new IllegalArgumentException(s"tag isn't a compound map, or key isn't string: $map")
    }*/

    def fromSerialList(list: NBTBase): Seq[NBTBase] = list match {
      case list: NBTTagByteArray =>
        list.func_150292_c.map(t => new NBTTagByte(t).asInstanceOf[NBTBase]).toVector
      case list: NBTTagIntArray =>
        list.func_150302_c.map(t => new NBTTagInt(t).asInstanceOf[NBTBase]).toVector
      case list: NBTTagCompound if list.getString("$serial.type") == "list" =>
        var res = Seq.empty[NBTBase]
        //for(tag <- list.getTags.asInstanceOf[Collection[NBTBase]]) println(tag.getName)
        for(i <- 0 until (list.func_150296_c.size - 1)) {
          //println(i)
          //val t = list.getString(s"t$i")
          val v = list.getTag(s"$i").copy
          //v.setName(t)
          res :+= v
        }
        res
      case _ =>
        throw new IllegalArgumentException(s"can't read list from tag $list")
    }

    def fromSerialMap(map: NBTBase): Seq[(NBTBase, NBTBase)] = map match {
      case map: NBTTagCompound if map.getString("$serial.type") == "map" =>
        var res = Seq.empty[(NBTBase, NBTBase)]
        for(i <- 0 until ((map.func_150296_c.size - 1) / 2)) {
          //val kn = map.getString(s"tk$i")
          val k = map.getTag(s"k$i").copy
          //k.setName(kn)
          //val vn = map.getString(s"tv$i")
          val v = map.getTag(s"v$i").copy
          //v.setName(vn)
          res :+= (k -> v)
        }
        res
      case map: NBTTagCompound if map.getString("$serial.type") == "mapCompact" =>
        map.func_150296_c.asInstanceOf[Set[String]].map { k =>
          val t = map.getTag(k)
          new NBTTagString(k).asInstanceOf[NBTBase] -> t.copy
        }.toVector
      case _ =>
        throw new IllegalArgumentException(s"can't read map from tag $map")
    }

    def fromSerialByte(t: NBTBase): Byte = t.asInstanceOf[NBTTagByte].func_150290_f
    def fromSerialShort(t: NBTBase): Short = t.asInstanceOf[NBTTagShort].func_150289_e
    def fromSerialInt(t: NBTBase): Int = t.asInstanceOf[NBTTagInt].func_150287_d
    def fromSerialLong(t: NBTBase): Long = t.asInstanceOf[NBTTagLong].func_150291_c
    def fromSerialFloat(t: NBTBase): Float = t.asInstanceOf[NBTTagFloat].func_150288_h
    def fromSerialDouble(t: NBTBase): Double = t.asInstanceOf[NBTTagDouble].func_150286_g
    def fromSerialBoolean(t: NBTBase): Boolean = t.asInstanceOf[NBTTagByte].func_150290_f > 0
    def fromSerialChar(t: NBTBase): Char = t.asInstanceOf[NBTTagString].func_150285_a_()(0)
    def fromSerialString(t: NBTBase): String = t.asInstanceOf[NBTTagString].func_150285_a_

    def removeTag(t: NBTBase) = {
      /*if(t.getName != "") (t.copy.setName(null), t.getName)
      else*/ ???
    }

    /*def extract(list: NBTBase): (NBTBase, NBTBase) = list match {
      case list: NBTTagCompound if list.getString("$serial.type") == "list" =>
        val res = list.copy.asInstanceOf[NBTTagCompound]
        val i = (res.getTags.size - 1) / 2
        val t = res.getString(s"t$i")
        val v = res.getTag(s"$i").copy
        v.setName(t.toString)
        res.removeTag(s"t$i")
        res.removeTag(s"$i")
        (v, res)
      case _ => throw new IllegalArgumentException(s"tag $list is not a list")
    }

    def extract(map: NBTBase, k: NBTBase): (NBTBase, NBTBase) = map match {
      case map: NBTTagCompound if map.getString("$serial.type") == "map" =>
        val res = list.copy.asInstanceOf[NBTTagCompound]
        val t = res.getString(s"t$i")
        val v = res.getTag(s"$i").copy
      case map: NBTTagCompound if map.getString("$serial.type") == "mapCompact" =>
      case _ => throw new IllegalArgumentException(s"tag $map is not a map")
    }*/
  }

  object vectorSerialInstance extends IsSerialFormat[Vector[Byte]] {
    def getNextInputLength(f: Vector[Byte]): Option[Int] = f.headOption.flatMap(_.toChar match {
      case 'B' => Some(2)
      case 'W' => Some(3)
      case 'I' => Some(5)
      case 'L' => Some(9)
      case 'F' => Some(5)
      case 'D' => Some(9)
      case 't' => Some(1)
      case 'f' => Some(1)
      case 'C' => Some(3)
      case 'S' | 's' => getNextStringLength(f)
      case 'V' => getNextListLength(f)
      case 'M' => getNextMapLength(f)
      case _ => throw new IllegalArgumentException(s"Illegal start symbol: ${f.head} (${f.head.toChar})")
    })

    def getNextStringLength(f: Vector[Byte], res: Int = 0): Option[Int] = f.headOption.flatMap(_.toChar match {
      case 's' if f.length >= 0x10001 => getNextStringLength(f.drop(0x10001), res + 0x10000)
      case 'S' if f.length >= 3 => Some(res + ByteBuffer.wrap(f.toArray, 1, 2).getShort)
      case 'S' | 's' => None
      case _ => throw new IllegalArgumentException(s"Illegal start symbol: ${f.head} (${f.head.toChar})")
    })

    def getNextListLength(f: Vector[Byte]): Option[Int] = for {
      h <- f.headOption
      res <- {
        assert(h == 'V'.toByte)
        def r(f: Vector[Byte], len: Int): Option[Int] = for {
          h2 <- f.headOption
          res <- {
            if(h2 == 'z') Some(len + 1)
            else for {
              l <- getNextInputLength(f)
              res <- r(f.drop(l), len + l)
            } yield res
          }
        } yield res
        r(f.tail, 1)
      }
    } yield res

    def getNextMapLength(f: Vector[Byte]): Option[Int] = for {
      h <- f.headOption
      res <- {
        assert(h == 'M'.toByte)
        def r(f: Vector[Byte], len: Int): Option[Int] = for {
          h2 <- f.headOption
          res <- {
            val t = f.tail
            if(h2 == 'z') Some(len + 1)
            else for {
              lenk <- {
                assert(h2 == '('.toByte)
                getNextInputLength(t)
              }
              t1 = t.drop(lenk)
              lenv <- getNextInputLength(t1)
              t2 = t1.drop(lenv)
              h3 <- t2.headOption
              res <- {
                assert(h3 == ')'.toByte)
                r(t2.tail, len + 2 + lenk + lenv)
              }
            } yield res
          }
        } yield res
        r(f.tail, 1)
      }
    } yield res

    def empty = Vector.empty[Byte]

    def toSerialList(l: Vector[Byte]*): Vector[Byte] = {
      ('V'.toByte +: l.fold(Vector.empty[Byte])(_ ++ _)) :+ 'z'.toByte
    }

    def toSerialMap(l: (Vector[Byte], Vector[Byte])*): Vector[Byte] = {
      ('M'.toByte +: l.map { case (k, v) =>
        ('('.toByte +: k) ++ v :+ ')'.toByte
      }.fold(Vector.empty[Byte])(_ ++ _)) :+ 'z'.toByte
    }

    def toSerial[A](v: A): Vector[Byte] = (v match {
      case v: Byte    => Array('B'.toByte, v)
      case v: Short   => (ByteBuffer allocate 3 put 'W'.toByte putShort v).array
      case v: Int     => (ByteBuffer allocate 5 put 'I'.toByte putInt v).array
      case v: Long    => (ByteBuffer allocate 9 put 'L'.toByte putLong v).array
      case v: Float   => (ByteBuffer allocate 5 put 'F'.toByte putFloat v).array
      case v: Double  => (ByteBuffer allocate 9 put 'D'.toByte putDouble v).array
      case v: Boolean => Array((if(v) 't' else 'f').toByte)
      case v: Char    => (ByteBuffer allocate 3 put 'C'.toByte putChar v).array
      case v: String  =>
        val chunks = v.getBytes(Charset.forName("UTF-8")).grouped(0x10000).toSeq
        chunks.init.map('s'.toByte +: _).fold(Array.emptyByteArray)(_ ++ _) ++
        ('S'.toByte +: ((ByteBuffer allocate 2 putShort chunks.last.length.toShort).array ++ chunks.last))
      case _ => ???
    }).to[Vector]

    def addTag(t: Vector[Byte], tag: String) = {
      toSerial(tag) ++ t
    }
      
    def fromSerialList(f: Vector[Byte]): Seq[Vector[Byte]] = {
      assert(f.head == 'V'.toByte)
      var res = Seq.empty[Vector[Byte]]
      var tail = f.tail
      while(tail.head != 'z') {
        val len = getNextInputLength(tail).get
        //println(s"nextlen: $len")
        val (n, nt) = tail.splitAt(len)
        //println(n)
        //println(nt)
        res :+= n
        tail = nt
      }
      assert(tail.length == 1)
      res
    }

    def fromSerialMap(f: Vector[Byte]): Seq[(Vector[Byte], Vector[Byte])] = {
      assert(f.head == 'M'.toByte)
      var res = Seq.empty[(Vector[Byte], Vector[Byte])]
      var tail = f.tail
      while(tail.head != 'z') {
        assert(tail.head == '('.toByte)
        tail = tail.tail
        val lenk = getNextInputLength(tail).get
        val (k, nt1) = tail.splitAt(lenk)
        tail = nt1
        val lenv = getNextInputLength(tail).get
        val (v, nt2) = tail.splitAt(lenv)
        tail = nt2
        assert(tail.head == ')'.toByte)
        tail = tail.tail

        res :+= (k -> v)
      }
      assert(tail.length == 1)
      res
    }

    def fromSerialByte(f: Vector[Byte]): Byte = {
      assert(f.length == 2 && f.head == 'B'.toByte)
      f(1)
    }
    def fromSerialShort(f: Vector[Byte]): Short = {
      assert(f.length == 3 && f.head == 'W'.toByte)
      ByteBuffer.wrap(f.toArray, 1, 2).getShort
    }
    def fromSerialInt(f: Vector[Byte]): Int = {
      assert(f.length == 5 && f.head == 'I'.toByte)
      ByteBuffer.wrap(f.toArray, 1, 4).getInt
    }
    def fromSerialLong(f: Vector[Byte]): Long = {
      assert(f.length == 9 && f.head == 'L'.toByte)
      ByteBuffer.wrap(f.toArray, 1, 8).getLong
    }
    def fromSerialFloat(f: Vector[Byte]): Float = {
      assert(f.length == 5 && f.head == 'F'.toByte)
      ByteBuffer.wrap(f.toArray, 1, 4).getFloat
    }
    def fromSerialDouble(f: Vector[Byte]): Double = {
      assert(f.length == 9 && f.head == 'D'.toByte)
      ByteBuffer.wrap(f.toArray, 1, 8).getDouble
    }
    def fromSerialBoolean(f: Vector[Byte]): Boolean = {
      assert(f.length == 1 && (f.head == 't'.toByte || f.head == 'f'.toByte))
      f(0) == 't'.toByte
    }
    def fromSerialChar(f: Vector[Byte]): Char = {
      assert(f.length == 3 && f.head == 'C'.toByte)
      ByteBuffer.wrap(f.toArray, 1, 2).getChar
    }
    def fromSerialString(f: Vector[Byte]): String = {
      assert(f.head == 's' || f.head == 'S')
      var bytes = Vector.empty[Byte]
      var tail = f
      while(tail.head == 's') {
        tail = tail.tail
        val (b, nt) = tail.splitAt(0x10000)
        bytes ++= b
        tail = nt
      }
      tail = tail.tail
      val len = ByteBuffer.wrap(tail.take(2).toArray).getShort
      val (b, nt) = tail.splitAt(2 + len)
      bytes ++= b
      assert(nt.isEmpty)
      new String(bytes.toArray, Charset.forName("UTF-8"))
    }

    def removeTag(t: Vector[Byte]) = {
      val (tag, v) = t.splitAt(getNextInputLength(t).get)
      (v, fromSerialString(tag))
    }
  }
}

trait PickleInstances {
  implicit object ByteInstance extends IsSerializable[Byte] {
    def pickle[F](t: Byte)(implicit F: IsSerialSink[F]): F = F.toSerial[Byte](t)
    def unpickle[F](f: F)(implicit F: IsSerialSource[F]): Byte = F.fromSerialByte(f)
  }
  implicit object ShortInstance extends IsSerializable[Short] {
    def pickle[F](t: Short)(implicit F: IsSerialSink[F]): F = F.toSerial[Short](t)
    def unpickle[F](f: F)(implicit F: IsSerialSource[F]): Short = F.fromSerialShort(f)
  }
  implicit object IntInstance extends IsSerializable[Int] {
    def pickle[F](t: Int)(implicit F: IsSerialSink[F]): F = F.toSerial[Int](t)
    def unpickle[F](f: F)(implicit F: IsSerialSource[F]): Int = F.fromSerialInt(f)
  }
  implicit object LongInstance extends IsSerializable[Long] {
    def pickle[F](t: Long)(implicit F: IsSerialSink[F]): F = F.toSerial[Long](t)
    def unpickle[F](f: F)(implicit F: IsSerialSource[F]): Long = F.fromSerialLong(f)
  }
  implicit object FloatInstance extends IsSerializable[Float] {
    def pickle[F](t: Float)(implicit F: IsSerialSink[F]): F = F.toSerial[Float](t)
    def unpickle[F](f: F)(implicit F: IsSerialSource[F]): Float = F.fromSerialFloat(f)
  }
  implicit object DoubleInstance extends IsSerializable[Double] {
    def pickle[F](t: Double)(implicit F: IsSerialSink[F]): F = F.toSerial[Double](t)
    def unpickle[F](f: F)(implicit F: IsSerialSource[F]): Double = F.fromSerialDouble(f)
  }
  implicit object BooleanInstance extends IsSerializable[Boolean] {
    def pickle[F](t: Boolean)(implicit F: IsSerialSink[F]): F = F.toSerial[Boolean](t)
    def unpickle[F](f: F)(implicit F: IsSerialSource[F]): Boolean = F.fromSerialBoolean(f)
  }
  implicit object CharInstance extends IsSerializable[Char] {
    def pickle[F](t: Char)(implicit F: IsSerialSink[F]): F = F.toSerial[Char](t)
    def unpickle[F](f: F)(implicit F: IsSerialSource[F]): Char = F.fromSerialChar(f)
  }
  implicit object StringInstance extends IsSerializable[String] {
    def pickle[F](t: String)(implicit F: IsSerialSink[F]): F = F.toSerial[String](t)
    def unpickle[F](f: F)(implicit F: IsSerialSource[F]): String = F.fromSerialString(f)
  }
}

object Serial extends PickleInstances

object P {
  @inline final def apply[F, T](t: T)(implicit F: IsSerialSink[F], T: IsSerialWritable[T]): F = T.pickle(t)(F)
  @inline final def apply[F, T](F: IsSerialSink[F], t: T)(implicit T: IsSerialWritable[T]): F = T.pickle(t)(F)
  @inline final def apply[F, T](T: IsSerialWritable[T], t: T)(implicit F: IsSerialSink[F]): F = T.pickle(t)(F)
  @inline final def apply[F, T](F: IsSerialSink[F], T: IsSerialWritable[T], t: T): F = T.pickle(t)(F)

  @inline final def unpickle[F, T](f: F)(implicit F: IsSerialSource[F], T: IsSerialReadable[T]): T = T.unpickle(f)(F)
  @inline final def unpickle[F, T](F: IsSerialSource[F], f: F)(T: IsSerialReadable[T]): T = T.unpickle(f)(F)
  @inline final def unpickle[F, T](T: IsSerialReadable[T], f: F)(implicit F: IsSerialSource[F]): T = T.unpickle(f)(F)
  @inline final def unpickle[F, T](F: IsSerialSource[F], T: IsSerialReadable[T], f: F): T = T.unpickle(f)(F)

  @inline final def list[F](t: F*)(implicit F: IsSerialSink[F]): F = F.toSerialList(t: _*)

  @inline final def unList[F](t: F)(implicit F: IsSerialSource[F]): Seq[F] = F.fromSerialList(t)

  @inline final def map[F](t: (F, F)*)(implicit F: IsSerialSink[F]): F = F.toSerialMap(t: _*)

  @inline final def unMap[F](t: F)(implicit F: IsSerialSource[F]): Seq[(F, F)] = F.fromSerialMap(t)
}

trait IsSerialWritable[T] {
  def pickle[F: IsSerialSink](t: T): F // write t to f
}

trait IsSerialReadable[T] {
  def unpickle[F: IsSerialSource](f: F): T // read t from f
}

trait IsSerializable[T] extends IsSerialWritable[T] with IsSerialReadable[T]

trait Copyable[T] {
  def copy(from: T, to: T): Unit
}

trait IsCopySerial[T] extends IsSerializable[T] with Copyable[T]

import net.minecraft.block.Block
import net.minecraft.entity.Entity
import net.minecraft.nbt.{ NBTBase, NBTTagCompound }
import net.minecraft.network.NetworkManager
import net.minecraft.network.Packet
import net.minecraft.network.play.server.S35PacketUpdateTileEntity
import net.minecraft.tileentity.TileEntity
import net.minecraft.world.World

trait TEDesc[TD <: TEDesc[TD]] { this: TD =>
  type Bean <: BeanTE[TD]
  type Parent <: SerialTileEntityWrapper[TD]
}

trait BeanTE[TD <: TEDesc[TD]] { this: TD#Bean =>
  type Bean = TD#Bean
  type Parent = TD#Parent

  private[this] var _parent: Parent = _
  def parent: Parent = _parent
  def parent_=(parent: Parent): Unit = _parent = parent
  def update(): Unit = {}
  def markDirty(): Unit = {}
  def getMaxRenderDistanceSquared: Double = 4096D
  def receiveClientEvent(num: Int, arg: Int): Boolean = false
  def canUpdate: Boolean = true
  def shouldRefresh(oldBlock: Block, newBlock: Block, oldMeta: Int, newMeta: Int, world: World, x: Int, y: Int, z: Int) =
    oldBlock ne newBlock
  def onChunkUnload(): Unit = {}

  @inline final def world = parent.getWorldObj()
  @inline final def x = parent.xCoord
  @inline final def y = parent.yCoord
  @inline final def z = parent.zCoord
}

trait TileEntityWrapper[TD <: TEDesc[TD]] extends TileEntity { this: TD#Parent =>
  type Bean = TD#Bean
  type Parent = TD#Parent

  def repr: Bean
  def repr_=(repr: Bean): Unit

  if(repr != null) repr.parent = this

  override def updateEntity(): Unit = {
    super.updateEntity()
    if(repr == null) println("Updating an empty TE wrapper!")
    else repr.update()
  }

  override def markDirty(): Unit = {
    super.markDirty()
    if(repr != null) repr.markDirty()
  }

  //override def getMaxRenderDistanceSquared = if(repr == null) 4096D else repr.getMaxRenderDistanceSquared

  //override def receiveClientEvent(num: Int, arg: Int) = if(repr == null) false else repr.receiveClientEvent(num, arg)

  override def canUpdate = repr == null || repr.canUpdate

  override def shouldRefresh(oldBlock: Block, newBlock: Block, oldMeta: Int, newMeta: Int, world: World, x: Int, y: Int, z: Int) = {
    repr == null || repr.shouldRefresh(oldBlock, newBlock, oldMeta, newMeta, world, x, y, z)
  }
  override def onChunkUnload(): Unit = {
    super.onChunkUnload()
    if(repr != null) repr.onChunkUnload()
  }
}

trait SerialTileEntityWrapper[TD <: TEDesc[TD]] extends TileEntityWrapper[TD] { this: TD#Parent =>

  implicit def WriteRepr: IsSerialWritable[Bean]
  implicit def ReadRepr: IsSerialReadable[Bean]

  implicit def ByteVector: IsSerialFormat[Vector[Byte]] = SerialFormats.vectorSerialInstance
  implicit def NBT: IsSerialFormat[NBTBase] = SerialFormats.nbtSerialInstance

  override def readFromNBT(cmp: NBTTagCompound): Unit = {
    super.readFromNBT(cmp)
    val realCmp = cmp.getTag("$serial.data")
    repr = ReadRepr.unpickle(realCmp)
    repr.parent_=(this)
  }

  override def writeToNBT(cmp: NBTTagCompound): Unit = {
    super.writeToNBT(cmp)
    if(repr == null) throw new RuntimeException("WAT")
    val realCmp = P(NBT, repr)
    cmp.setTag("$serial.data", realCmp)
  }

  override def getDescriptionPacket(): Packet = { // TODO maybe split if too big
    if(repr == null) throw new RuntimeException("WAT")
    val cmp = new NBTTagCompound
    cmp.setByteArray("$", P(ByteVector, repr).toArray)
    new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, cmp)
  }

  override def onDataPacket(manager: NetworkManager, packet: S35PacketUpdateTileEntity): Unit = {
    repr = ReadRepr.unpickle(packet.func_148857_g.getByteArray("$").to[Vector])
    repr.parent = this
  }
}

trait SimpleSerialTile[TD <: TEDesc[TD]] extends SerialTileEntityWrapper[TD] { this: TD#Parent =>

  implicit def Repr: IsSerializable[Bean]

  implicit def WriteRepr: IsSerialWritable[Bean] = Repr
  implicit def ReadRepr: IsSerialReadable[Bean] = Repr
}

import cpw.mods.fml.common.network.internal.FMLProxyPacket
import cpw.mods.fml.common.network.NetworkRegistry.FML_CHANNEL
import io.netty.channel.{ ChannelHandler, ChannelHandlerContext }
import io.netty.handler.codec.MessageToMessageCodec
import io.netty.buffer.Unpooled
import io.netty.util.AttributeKey

@ChannelHandler.Sharable
object VectorCodec extends MessageToMessageCodec[FMLProxyPacket, Vector[Byte]] {
  import SerialFormats.vectorSerialInstance.getNextInputLength

  val decodeBufKey = new AttributeKey[Vector[Byte]]("VectorCodec:decodeBuf");

  protected def decode(ctx: ChannelHandlerContext, packet: FMLProxyPacket, out: JList[Object]): Unit = {
    val decodeBuf = ctx.attr(decodeBufKey)
    val oldBuf = Option(decodeBuf.get).getOrElse(Vector.empty)
    decodeBuf set (oldBuf ++ packet.payload.array)
    var len = getNextInputLength(decodeBuf.get)
    while(!len.isEmpty) {
      out add (decodeBuf.get take len.get)
      decodeBuf set (decodeBuf.get drop len.get)
      len = getNextInputLength(decodeBuf.get)
    }
  }

  protected def encode(ctx: ChannelHandlerContext, data: Vector[Byte], out: JList[Object]): Unit = {
    for(chunk <- data grouped 0x7FFF) { // max packet size
      out add new FMLProxyPacket(Unpooled.wrappedBuffer(chunk.toArray), ctx.channel.attr(FML_CHANNEL).get)
    }
  }
}
