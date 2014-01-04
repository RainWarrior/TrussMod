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
    def empty = Vector.empty[Byte]

    def toSerialList(l: Vector[Byte]*): Vector[Byte] = {
      ??? // TODO
    }

    def toSerialMap(l: (Vector[Byte], Vector[Byte])*): Vector[Byte] = {
      ??? // TODO
    }

    def toSerial[A](v: A): Vector[Byte] = (v match {
      case v: Byte    => Array(v)
      case v: Short   => ByteBuffer.allocate(2).putShort(v).array
      case v: Int     => ByteBuffer.allocate(4).putInt(v).array
      case v: Long    => ByteBuffer.allocate(8).putLong(v).array
      case v: Float   => ByteBuffer.allocate(4).putFloat(v).array
      case v: Double  => ByteBuffer.allocate(8).putDouble(v).array
      case v: Boolean => Array((if(v) 1 else 0).toByte)
      case v: Char    => ByteBuffer.allocate(2).putChar(v).array
      case v: String  => v.getBytes(Charset.forName("UTF-8"))
      case _ => ???
    }).to[Vector]

    def addTag(t: Vector[Byte], tag: String) = t
      
    /*def append(seq: Vector[Byte], f: Vector[Byte]): Vector[Byte] = {
      ??? // TODO
    }*/

    def fromSerialMap(f: Vector[Byte]): Seq[(Vector[Byte], Vector[Byte])] = {
      ??? // TODO
    }

    def fromSerialList(f: Vector[Byte]): Seq[Vector[Byte]] = {
      ??? // TODO
    }

    def fromSerial[A](f: Vector[Byte])(implicit A: ClassTag[A]): A = try {
      (A.runtimeClass match {
        case java.lang.Byte.TYPE      if f.length == 1 => f(0)
        case java.lang.Short.TYPE     if f.length == 2 => ByteBuffer.wrap(f.toArray).getShort
        case java.lang.Integer.TYPE   if f.length == 4 => ByteBuffer.wrap(f.toArray).getInt
        case java.lang.Long.TYPE      if f.length == 8 => ByteBuffer.wrap(f.toArray).getLong
        case java.lang.Float.TYPE     if f.length == 4 => ByteBuffer.wrap(f.toArray).getFloat
        case java.lang.Double.TYPE    if f.length == 8 => ByteBuffer.wrap(f.toArray).getDouble
        case java.lang.Boolean.TYPE   if f.length == 1 => f(0) > 0
        case java.lang.Character.TYPE if f.length == 2 => ByteBuffer.wrap(f.toArray).getChar
        case `stringClass`            => new String(f.toArray, Charset.forName("UTF-8"))
      }).asInstanceOf[A]
    } catch {
      case e: MatchError => throw new IllegalArgumentException(s"can't read type $A from vector $f")
    }

    def removeTag(t: Vector[Byte]) = ???

    /*def extract(seq: Vector[Byte]): (Vector[Byte], Vector[Byte]) = {
      ??? // TODO
    }*/
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
