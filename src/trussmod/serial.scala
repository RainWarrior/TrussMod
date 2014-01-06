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

  // should probably have primitives + string
  def fromSerial[A: ClassTag](f: F): A

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
        for(i <- 0 until (list.getTags.size - 1)) {
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
        for(i <- 0 until ((map.getTags.size - 1) / 2)) {
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

    def fromSerial[A](t: NBTBase)(implicit A: ClassTag[A]): A = try {
      (A.runtimeClass match { // uglyyyy
        case java.lang.Byte.TYPE      => Some(t) collect { case t: NBTTagByte   => t.data }
        case java.lang.Short.TYPE     => Some(t) collect { case t: NBTTagShort  => t.data }
        case java.lang.Integer.TYPE   => Some(t) collect { case t: NBTTagInt    => t.data }
        case java.lang.Long.TYPE      => Some(t) collect { case t: NBTTagLong   => t.data }
        case java.lang.Float.TYPE     => Some(t) collect { case t: NBTTagFloat  => t.data }
        case java.lang.Double.TYPE    => Some(t) collect { case t: NBTTagDouble => t.data }
        case java.lang.Boolean.TYPE   => Some(t) collect { case t: NBTTagByte   => t.data > 0 }
        case java.lang.Character.TYPE => Some(t) collect { case t: NBTTagString => t.data(0) }
        case `stringClass`            => Some(t) collect { case t: NBTTagString => t.data }
      }).asInstanceOf[A]
    } catch {
      case e: MatchError => throw new IllegalArgumentException(s"can't read type $A from tag $t")
    }

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
        val len = getNextInputLength(f.tail)
        val (n, nt) = tail.splitAt(len)
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

    def fromSerial[A](f: Vector[Byte])(implicit A: ClassTag[A]): A = try {
      (A.runtimeClass match {
        case java.lang.Byte.TYPE      if f.length == 2 && f.head == 'B'.toByte => f(1)
        case java.lang.Short.TYPE     if f.length == 3 && f.head == 'W'.toByte => ByteBuffer.wrap(f.toArray, 1, 2).getShort
        case java.lang.Integer.TYPE   if f.length == 5 && f.head == 'I'.toByte => ByteBuffer.wrap(f.toArray, 1, 4).getInt
        case java.lang.Long.TYPE      if f.length == 9 && f.head == 'L'.toByte => ByteBuffer.wrap(f.toArray, 1, 8).getLong
        case java.lang.Float.TYPE     if f.length == 5 && f.head == 'F'.toByte => ByteBuffer.wrap(f.toArray, 1, 4).getFloat
        case java.lang.Double.TYPE    if f.length == 9 && f.head == 'D'.toByte => ByteBuffer.wrap(f.toArray, 1, 8).getDouble
        case java.lang.Boolean.TYPE   if f.length == 1 && (f.head == 't'.toByte || f.head == 'f'.toByte) => f(0) == 't'.toByte
        case java.lang.Character.TYPE if f.length == 3 && f.head == 'C'.toByte => ByteBuffer.wrap(f.toArray, 1, 2).getChar
        case `stringClass`            if f.head == 's' || f.head == 'S' =>
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
      }).asInstanceOf[A]
    } catch {
      case e: MatchError => throw new IllegalArgumentException(s"can't read type $A from vector $f")
    }

    def removeTag(t: Vector[Byte]) = {
      val (tag, v) = t.splitAt(getNextInputLength(t))
      (v, fromSerial[String](tag))
    }
  }
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

import net.minecraft.nbt.{ NBTBase, NBTTagCompound }
import net.minecraft.network.INetworkManager
import net.minecraft.network.packet.{ Packet, Packet250CustomPayload }
import net.minecraft.tileentity.TileEntity
import cpw.mods.fml.common.network.{ IPacketHandler, Player }

trait SerialTileEntityLike[Repr] extends TileEntity with IPacketHandler {

  def channel: String
  implicit def Repr: IsSerializable[Repr]
  implicit def CopyRepr: Copyable[Repr]

  def repr: Repr = this.asInstanceOf[Repr]
  implicit def ByteVector: IsSerialFormat[Vector[Byte]] = SerialFormats.vectorSerialInstance
  implicit def NBT: IsSerialFormat[NBTBase] = SerialFormats.nbtSerialInstance

  override def readFromNBT(cmp: NBTTagCompound): Unit = {
    super.readFromNBT(cmp)
    val realCmp = cmp.getTag("$serial.data")
    CopyRepr.copy(Repr.unpickle(realCmp), repr)
  }

  override def writeToNBT(cmp: NBTTagCompound): Unit = {
    super.writeToNBT(cmp)
    val realCmp = Repr.pickle[NBTBase](repr).asInstanceOf[NBTTagCompound]
    cmp.setTag("$serial.data", realCmp)
  }

  override def getDescriptionPacket(): Packet = {
    val header = ByteBuffer allocate 12 putInt xCoord putInt yCoord putInt zCoord
    val data = header.array.to[Vector] ++ Repr.pickle[Vector[Byte]](repr)
    new Packet250CustomPayload(channel, data.toArray)
  }

  def onPacketData(manager: INetworkManager, packet: Packet250CustomPayload, player: Player): Unit = {
    CopyRepr.copy(Repr.unpickle(packet.data.to[Vector].drop(12)), repr)
  }
}