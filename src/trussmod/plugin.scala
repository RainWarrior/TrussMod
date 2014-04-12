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

package rainwarrior.hooks.plugin

import net.minecraft.launchwrapper.IClassTransformer
import cpw.mods.fml.relauncher.{ FMLRelaunchLog => log, IFMLCallHook, IFMLLoadingPlugin }
import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper.{ INSTANCE => mapper }
import cpw.mods.fml.common.{ Loader, ModAPIManager }
import cpw.mods.fml.common.discovery.ASMDataTable
import ASMDataTable.ASMData
import org.objectweb.asm
import asm.{ ClassReader, ClassWriter, Opcodes }
import asm.tree._
import Opcodes._
import asm.util.{ ASMifier, TraceClassVisitor }
import collection.JavaConversions._
import java.util.{ List => JList, Map => JMap }
import java.io.PrintWriter

@IFMLLoadingPlugin.TransformerExclusions(value = Array("rainwarrior.hooks.plugin", "scala"))
class Plugin extends IFMLLoadingPlugin with IFMLCallHook {
  //override def getLibraryRequestClass: Array[String] = null
  override def getASMTransformerClass = Array("rainwarrior.hooks.plugin.Transformer")
  override def getModContainerClass: String = null
  override def getSetupClass = "rainwarrior.hooks.plugin.Plugin"
  override def injectData(data: JMap[String, AnyRef]) {}
  override def getAccessTransformerClass: String = null
  override def call(): Void = {
    //println("Hello, World! From CoreMod!")
    null
  }
}
object Plugin {
  private[this] lazy val dataTableField = {
    val field = classOf[ModAPIManager].getDeclaredField("dataTable")
    field.setAccessible(true)
    field
  }

  def dataTable =
    dataTableField.get(ModAPIManager.INSTANCE).asInstanceOf[ASMDataTable]

  private[this] lazy val _optionals = {
    (dataTable.getAll("cpw.mods.fml.common.Optional$Interface") ++
    dataTable.getAll("cpw.mods.fml.common.Optional$Method") ++ (for {
      data <- dataTable.getAll("cpw.mods.fml.common.Optional$InterfaceList")
      packed <- data.getAnnotationInfo.get("value").asInstanceOf[JList[JMap[String, AnyRef]]]
    } yield data.copy(packed))).foldLeft(Map.empty[String, Set[ASMData]]) { (map, data) =>
      val name = data.getClassName
      map + (name -> map.get(name).map(_ + data).getOrElse(Set(data)))
    }
  }

  def optionals = Option(dataTable) match {
    case Some(table) => _optionals
    case None => Map.empty[String, Set[ASMData]]
  }
}

class Transformer extends IClassTransformer {
  type MethodChecker = (String, MethodNode) => Boolean
  type InsTransformer = MethodNode => Unit
  lazy val deobfEnv = this.getClass.getClassLoader.getResource("net/minecraft/world/World.class") != null
  lazy val blockClass = mapper.unmap("net/minecraft/block/Block")
  def transformIsBlockOpaqueCube(isChunkCache: Boolean)(m: MethodNode) {
    log.finer(s"TrussMod: FOUND isBlockOpaqueCube")
    val l1 = m.instructions.toArray.collectFirst{case i: JumpInsnNode if i.getOpcode == GOTO => i.label}.get
    val pos = m.instructions.toArray.collectFirst{case i: FrameNode => i}.get
    val list = new InsnList
    list.add(new VarInsnNode(ALOAD, 0))
    if(isChunkCache) {
      list.add(new FieldInsnNode(
        GETFIELD,
        "net/minecraft/world/ChunkCache",
        if(deobfEnv) "worldObj" else "field_72815_e",
        "Lnet/minecraft/world/World;"))
    }
    list.add(new VarInsnNode(ILOAD, 1))
    list.add(new VarInsnNode(ILOAD, 2))
    list.add(new VarInsnNode(ILOAD, 3))
    list.add(new MethodInsnNode(
      INVOKESTATIC,
      "rainwarrior/hooks/MovingRegistry",
      "isMoving",
      "(Lnet/minecraft/world/World;III)Z"))
    val l2 = new LabelNode
    list.add(new JumpInsnNode(IFEQ, l2))
    list.add(new InsnNode(ICONST_0))
    list.add(new JumpInsnNode(GOTO, l1))
    list.add(l2)
    list.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null))
    m.instructions.insert(pos, list)
  }
  def transformRenderBlockByRenderType(m: MethodNode) {
    log.finer(s"TrussMod: FOUND renderBlockByRenderType")
    //for(inst <- m.instructions.toArray) log.finer(inst)
    val old = m.instructions.toArray.collectFirst{case i: MethodInsnNode => i}.get
    val list = new InsnList
    list.add(new VarInsnNode(ILOAD, 2))
    list.add(new VarInsnNode(ILOAD, 3))
    list.add(new VarInsnNode(ILOAD, 4))
    list.add(new MethodInsnNode(
      INVOKESTATIC,
      "rainwarrior/hooks/HelperRenderer",
      "getRenderType",
      "(Lnet/minecraft/block/Block;III)I"))
    m.instructions.insert(old, list)
    m.instructions.remove(old)
  }
  val classData = Map[String, Tuple3[MethodChecker, MethodChecker, InsTransformer]](
    "net.minecraft.world.World" -> ((
      (_, m) => m.name == "isBlockOpaqueCube",
      (n, m) => mapper.mapMethodName(n, m.name, "(III)Z") == "func_72804_r",
      transformIsBlockOpaqueCube(false)
    )),
    "net.minecraft.world.ChunkCache" -> ((
      (_, m) => m.name == "isBlockOpaqueCube",
      (n, m) => mapper.mapMethodName(n, m.name, "(III)Z") == "func_72804_r",
      transformIsBlockOpaqueCube(true)
    )),
    "net.minecraft.client.renderer.RenderBlocks" -> ((
      (_, m) => m.name == "renderBlockByRenderType",
      (n, m) => mapper.mapMethodName(n, m.name, s"(L$blockClass;III)Z") == "func_78612_b" && m.desc == s"(L$blockClass;III)Z",
      transformRenderBlockByRenderType
    )),
    "net.minecraft.client.renderer.tileentity.TileEntityRenderer" -> ((
      (_, m) => m.name == "<init>",
      (_, m) => m.name == "<init>",
      { m: MethodNode =>
        log.finer("TrussMod: FOUND <init>")
        m.access &= ~ACC_PRIVATE
        m.access &= ~ACC_PROTECTED
        m.access |= ACC_PUBLIC
      }
    )))
  override def transform(name: String, tName: String, data: Array[Byte]) = {
    log.finer(s"checking: $name, $tName")
    //val getMethodMap = mapper.getClass.getDeclaredMethod("getMethodMap", classOf[String])
    //getMethodMap.setAccessible(true)
    //val classNameBiMap = mapper.getClass.getDeclaredField("classNameBiMap")
    //classNameBiMap.setAccessible(true)
    if(classData.keys.contains(tName)) { // patch table transformer
      println(s"TrussMod: transforming: $tName")
      val (ch1, ch2, tr) = classData(tName)
      val node = new ClassNode
      val reader = new ClassReader(data)
      reader.accept(node, 0)

      for(m@(_m: MethodNode) <- node.methods) {
        if((deobfEnv && ch1(name, m)) || (!deobfEnv && ch2(name, m))) {
          tr(m)
        }
      }

      val writer = new ClassWriter(ClassWriter.COMPUTE_MAXS)
      node.accept(writer)
      //val checker = new TraceClassVisitor(writer, new ASMifier, new PrintWriter(System.out))
      //node.accept(checker)
      writer.toByteArray
      //data
    /*} else if(tName == "net.minecraft.world.WorldServer") { // access transformer FIXME
      log.finer(s"TrussMod: transforming: $tName")
      val node = new ClassNode
      val reader = new ClassReader(data)
      reader.accept(node, 0)

      for(f@(_f: FieldNode) <- node.fields) {
        if(f.name == "pendingTickListEntriesHashSet"
        || mapper.mapFieldName(name, f.name, "Ljava/util/Set;") == "field_73064_N") {
          log.finer("TrussMod: FOUND pendingTickListEntriesHashSet")
          f.access &= ~ACC_PRIVATE
          f.access &= ~ACC_PROTECTED
          f.access |= ACC_PUBLIC
        } else if(f.name == "pendingTickListEntriesTreeSet"
        || mapper.mapFieldName(name, f.name, "Ljava/util/TreeSet;") == "field_73065_O") {
          log.finer("TrussMod: FOUND pendingTickListEntriesTreeSet")
          f.access &= ~ACC_PRIVATE
          f.access &= ~ACC_PROTECTED
          f.access |= ACC_PUBLIC
        } else if(f.name == "pendingTickListEntriesThisTick"
        || mapper.mapFieldName(name, f.name, "Ljava/util/List;") == "field_94579_S") {
          log.finer("TrussMod: FOUND pendingTickListEntriesThisTick")
          f.access &= ~ACC_PRIVATE
          f.access &= ~ACC_PROTECTED
          f.access |= ACC_PUBLIC
        }
      }
      
      val writer = new ClassWriter(ClassWriter.COMPUTE_MAXS)
      node.accept(writer)
      //val checker = new TraceClassVisitor(writer, new ASMifier, new PrintWriter(System.out))
      //node.accept(checker)
      writer.toByteArray
      //data*/
    } else if(name endsWith "$class") { // Optional trait transformer
      val lookupName = name.substring(0, name.length - 6)
      if(Plugin.optionals contains lookupName) {
        val node = new ClassNode
        val reader = new ClassReader(data)
        reader.accept(node, 0)

        val toRemove = for {
          optional <- Plugin.optionals(lookupName)
          annInfo = optional.getAnnotationInfo
          if !annInfo.containsKey("iface")
          modId = annInfo.get("modid").asInstanceOf[String]
          if !(Loader.isModLoaded(modId) || ModAPIManager.INSTANCE.hasAPI(modId))
          desc = optional.getObjectName.asInstanceOf[String]
          pos = desc.indexOf('(') + 1
        } yield desc.patch(pos, s"L${lookupName.replace('.', '/')};", 0)

        val oldMethods = node.methods.asInstanceOf[JList[MethodNode]]
        val newMethods = oldMethods filterNot { m => toRemove(m.name + m.desc) }
        node.methods = newMethods

        //log.finer(s"TrussMod Optional removing: ${node.name} $toRemove ${oldMethods.map(m => m.name + m.desc)}, ${newMethods.map(m => m.name + m.desc)}")

        val writer = new ClassWriter(ClassWriter.COMPUTE_MAXS)
        node.accept(writer)
        writer.toByteArray
      } else data
    } else data
  }
}
