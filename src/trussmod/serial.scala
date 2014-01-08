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
import scala.reflect._
import annotation.tailrec
import language.higherKinds

import java.util.Collection
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
        list.foreach { case t: NBTTagInt => arr += t.data }
        new NBTTagIntArray(null, arr.result)
      } else if(list.forall(t => t.isInstanceOf[NBTTagByte])) {
        val arr = new ArrayBuilder.ofByte
        list.foreach { case t: NBTTagByte => arr += t.data }
        new NBTTagByteArray(null, arr.result)
      } else {
        val res = new NBTTagCompound
        res.setString("$serial.type", "list")
        var i = 0
        list.foreach { t =>
          res.setString(s"t$i", t.getName)
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
          res.setString(s"tk$i", k.getName)
          res.setTag(s"k$i", k)
          res.setString(s"tv$i", v.getName)
          res.setTag(s"v$i", v)
          i += 1
        }
      }
      res
    }

    def toSerial[A](v: A): NBTBase = v match { // ugly-ish
      case v: Byte    => new NBTTagByte(null, v)
      case v: Short   => new NBTTagShort(null, v)
      case v: Int     => new NBTTagInt(null, v)
      case v: Long    => new NBTTagLong(null, v)
      case v: Float   => new NBTTagFloat(null, v)
      case v: Double  => new NBTTagDouble(null, v)
      case v: Boolean => new NBTTagByte(null, if(v) 1 else 0)
      case v: Char    => new NBTTagString(null, v.toString)
      case v: String  => new NBTTagString(null, v)
      case _ => ???
    }

    def addTag(t: NBTBase, tag: String) = {
      if (t.getName == "") {
        val res = t.copy
        res.setName(tag)
        res
      } else ???
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
        list.byteArray.map(t => new NBTTagByte(null, t).asInstanceOf[NBTBase]).toVector
      case list: NBTTagIntArray =>
        list.intArray.map(t => new NBTTagInt(null, t).asInstanceOf[NBTBase]).toVector
      case list: NBTTagCompound if list.getString("$serial.type") == "list" =>
        var res = Seq.empty[NBTBase]
        //for(tag <- list.getTags.asInstanceOf[Collection[NBTBase]]) println(tag.getName)
        for(i <- 0 until ((list.getTags.size - 1) / 2)) {
          //println(i)
          val t = list.getString(s"t$i")
          val v = list.getTag(s"$i").copy
          v.setName(t)
          res :+= v
        }
        res
      case _ =>
        throw new IllegalArgumentException(s"can't read list from tag $list")
    }

    def fromSerialMap(map: NBTBase): Seq[(NBTBase, NBTBase)] = map match {
      case map: NBTTagCompound if map.getString("$serial.type") == "map" =>
        var res = Seq.empty[(NBTBase, NBTBase)]
        for(i <- 0 until ((map.getTags.size - 1) / 4)) {
          val kn = map.getString(s"tk$i")
          val k = map.getTag(s"k$i").copy
          k.setName(kn)
          val vn = map.getString(s"tv$i")
          val v = map.getTag(s"v$i").copy
          v.setName(vn)
          res :+= (k -> v)
        }
        res
      case map: NBTTagCompound if map.getString("$serial.type") == "mapCompact" =>
        map.getTags.asInstanceOf[Collection[NBTBase]].map { t =>
          new NBTTagString(null, t.getName).asInstanceOf[NBTBase] -> t.copy
        }.toVector
      case _ =>
        throw new IllegalArgumentException(s"can't read map from tag $map")
    }

    def fromSerialByte(t: NBTBase): Byte = t.asInstanceOf[NBTTagByte].data
    def fromSerialShort(t: NBTBase): Short = t.asInstanceOf[NBTTagShort].data
    def fromSerialInt(t: NBTBase): Int = t.asInstanceOf[NBTTagInt].data
    def fromSerialLong(t: NBTBase): Long = t.asInstanceOf[NBTTagLong].data
    def fromSerialFloat(t: NBTBase): Float = t.asInstanceOf[NBTTagFloat].data
    def fromSerialDouble(t: NBTBase): Double = t.asInstanceOf[NBTTagDouble].data
    def fromSerialBoolean(t: NBTBase): Boolean = t.asInstanceOf[NBTTagByte].data > 0
    def fromSerialChar(t: NBTBase): Char = t.asInstanceOf[NBTTagString].data(0)
    def fromSerialString(t: NBTBase): String = t.asInstanceOf[NBTTagString].data

    def removeTag(t: NBTBase) = {
      if(t.getName != "") (t.copy.setName(null), t.getName)
      else ???
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
    def getNextInputLength(f: Vector[Byte]): Int = f.head.toChar match {
      case 'B' => 2
      case 'W' => 3
      case 'I' => 5
      case 'L' => 9
      case 'F' => 5
      case 'D' => 9
      case 't' => 1
      case 'f' => 1
      case 'C' => 3
      case 'S' | 's' => getNextStringLength(f)
      case 'V' => getNextListLength(f)
      case 'M' => getNextMapLength(f)
      case _ => throw new IllegalArgumentException(s"Illegal start symbol: ${f.head} (${f.head.toChar})")
    }

    @tailrec final def getNextStringLength(f: Vector[Byte], res: Int = 0): Int = f.head.toChar match {
      case 's' => getNextStringLength(f.drop(0x10001), res + 0x10000)
      case 'S' => res + ByteBuffer.wrap(f.toArray, 1, 2).getShort
    }

    def getNextListLength(f: Vector[Byte]): Int = {
      assert(f.head == 'V'.toByte)
      var length = 1
      var tail = f.tail
      while(tail.head != 'z') {
        val len = getNextInputLength(tail)
        length += len
        tail = tail.drop(len)
      }
      length + 1
    }

    def getNextMapLength(f: Vector[Byte]): Int = {
      assert(f.head == 'V'.toByte)
      var length = 1
      var tail = f.tail
      while(tail.head != 'z') {
        assert(tail.head == '('.toByte)
        length += 1
        tail = tail.tail
        val lenk = getNextInputLength(tail)
        length += lenk
        tail = tail.drop(lenk)
        val lenv = getNextInputLength(tail)
        length += lenv
        tail = tail.drop(lenv)
        assert(tail.head == ')'.toByte)
        length += 1
        tail = tail.tail
      }
      length + 1
    }

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
        val len = getNextInputLength(tail)
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
        val lenk = getNextInputLength(tail)
        val (k, nt1) = tail.splitAt(lenk)
        tail = nt1
        val lenv = getNextInputLength(tail)
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
      val (tag, v) = t.splitAt(getNextInputLength(t))
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

import net.minecraft.entity.Entity
import net.minecraft.nbt.{ NBTBase, NBTTagCompound }
import net.minecraft.network.INetworkManager
import net.minecraft.network.packet.{ Packet, Packet132TileEntityData }
import net.minecraft.tileentity.TileEntity
import cpw.mods.fml.common.network.{ IPacketHandler, NetworkRegistry, Player => DPlayer}
import com.google.common.collect.Multimap

trait SerialTileEntityLike[Repr] extends TileEntity /*with IPacketHandler */{

  def channel: String
  implicit def WriteRepr: IsSerialWritable[Repr]
  implicit def ReadRepr: IsSerialReadable[Repr]
  implicit def CopyRepr: Copyable[Repr]

  def repr: Repr = this.asInstanceOf[Repr]
  implicit def ByteVector: IsSerialFormat[Vector[Byte]] = SerialFormats.vectorSerialInstance
  implicit def NBT: IsSerialFormat[NBTBase] = SerialFormats.nbtSerialInstance

  //NetworkRegistry.instance.registerChannel(this, channel)

  override def readFromNBT(cmp: NBTTagCompound): Unit = {
    super.readFromNBT(cmp)
    val realCmp = cmp.getTag("$serial.data")
    CopyRepr.copy(ReadRepr.unpickle(realCmp), repr)
  }

  override def writeToNBT(cmp: NBTTagCompound): Unit = {
    super.writeToNBT(cmp)
    val realCmp = P(NBT, repr)
    cmp.setTag("$serial.data", realCmp)
  }

  override def getDescriptionPacket(): Packet = { // TODO maybe split if too big
    /*val header = ByteBuffer allocate 12 putInt xCoord putInt yCoord putInt zCoord
    val data = header.array.to[Vector] ++ P(ByteVector, repr)
    new Packet250CustomPayload(channel, data.toArray)*/
    val cmp = new NBTTagCompound
    cmp.setByteArray("$", P(ByteVector, repr).toArray)
    new Packet132TileEntityData(xCoord, yCoord, zCoord, 0, cmp)
  }

  override def onDataPacket(manager: INetworkManager, packet: Packet132TileEntityData): Unit = {
    CopyRepr.copy(ReadRepr.unpickle(packet.data.getByteArray("$").to[Vector]), repr)
  }

  /*override def onPacketData(manager: INetworkManager, packet: Packet250CustomPayload, player: DPlayer): Unit = {
    val buf = ByteBuffer.wrap(packet.data, 0, 12)
    val coords = (buf.getInt, buf.getInt, buf.getInt)
    if((xCoord, yCoord, zCoord) == coords && player.asInstanceOf[Entity].worldObj == worldObj) {
      CopyRepr.copy(ReadRepr.unpickle(packet.data.to[Vector].drop(12)), repr)
    }
  }

  abstract override def validate(): Unit = {
    super.validate()
    NetworkRegistry.instance.registerChannel(this, channel)
  }

  abstract override def invalidate(): Unit = {
    super.invalidate()
    NetworkRegistryProxy.universalPacketHandlers.remove(channel, this)
  }*/

}
object NetworkRegistryProxy {
  private[this] lazy val universalPacketHandlersField = {
    val field = classOf[NetworkRegistry].getDeclaredField("universalPacketHandlers")
    field.setAccessible(true)
    field
  }

  def universalPacketHandlers =
    universalPacketHandlersField.get(NetworkRegistry.instance).asInstanceOf[Multimap[String, IPacketHandler]]
}

trait SimpleSerialTile[Repr] extends SerialTileEntityLike[Repr] {

  implicit def Repr: IsCopySerial[Repr]

  implicit def WriteRepr: IsSerialWritable[Repr] = Repr
  implicit def ReadRepr: IsSerialReadable[Repr] = Repr
  implicit def CopyRepr: Copyable[Repr] = Repr
}
