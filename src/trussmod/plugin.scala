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

class Transformer extends IClassTransformer {
  type MethodChecker = (String, MethodNode) => Boolean
  type InsTransformer = MethodNode => Unit
  lazy val deobfEnv = this.getClass.getClassLoader.getResource("net/minecraft/world/World.class") != null
  lazy val blockClass = mapper.unmap("net/minecraft/block/Block")
  lazy val teClass = mapper.unmap("net/minecraft/tileentity/TileEntity")
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
  def transformRenderTileEntity(m: MethodNode) {
    log.finer(s"TrussMod: FOUND renderTileEntity")
    val old = m.instructions.toArray.collect { case i: MethodInsnNode => i }.last
    m.instructions.insert(old, new InsnNode(POP))
    m.instructions.insert(old, new MethodInsnNode(
      INVOKESTATIC,
      "rainwarrior/hooks/TileEntityDispatcherHook",
      "renderTileEntityAt",
      old.desc
    ))
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
    "net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher" -> ((
      (_, m) => m.name == "renderTileEntity",
      (n, m) => mapper.mapMethodName(n, m.name, s"(L$teClass;F)V") == "func_147544_a",
      transformRenderTileEntity
    )))
  override def transform(name: String, tName: String, data: Array[Byte]) = {
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
    } else data
  }
}
