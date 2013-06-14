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

package rainwarrior.hooks

import cpw.mods.fml.relauncher.{ IClassTransformer, IFMLCallHook, IFMLLoadingPlugin }
import org.objectweb.asm
import asm.{ ClassReader, ClassWriter, Opcodes }
import asm.tree._
import Opcodes._
import asm.util.{ ASMifier, TraceClassVisitor }
import collection.JavaConversions._
import java.util.{ Map => JMap }
import java.io.PrintWriter

@IFMLLoadingPlugin.TransformerExclusions(value = Array("rainwarrior", "scala", "java", "cpw"))
class Plugin extends IFMLLoadingPlugin with IFMLCallHook{
//  type unit = typeOf[runtime.BoxedUnit]
  override val getLibraryRequestClass = null
  override val getASMTransformerClass = Array(classOf[Transformer].getName)
  override val getModContainerClass: String = null
  override val getSetupClass = classOf[Plugin].getName
  override def injectData(data: JMap[String, AnyRef]) {}
  override def call() = {
    println("Hello, World! From CoreMod!")
    null
  }
}

class Transformer extends IClassTransformer {
  override def transform(name: String, transformedName: String, data: Array[Byte]) = {
    val node = new ClassNode
    val reader = new ClassReader(data)
    reader.accept(node, 0)

    name match {
      case n  if (n == "net.minecraft.world.World" || n == "net.minecraft.world.ChunkCache") =>
        println(s"transforming: $name");
        node.methods = for (m <- node.methods) yield m.name match {
          case "isBlockOpaqueCube" =>
            println("FOUND isBlockOpaqueCube")
            //for(inst <- m.instructions.toArray) println(inst)
            val l1 = m.instructions.toArray.collectFirst{case i: JumpInsnNode if i.getOpcode == GOTO => i.label}.get
            val pos = m.instructions.toArray.collectFirst{case i: FrameNode => i}.get
            val list = new InsnList
            list.add(new VarInsnNode(ALOAD, 0))
            if(name == "net.minecraft.world.ChunkCache") {
              list.add(new FieldInsnNode(
                GETFIELD,
                "net/minecraft/world/ChunkCache",
                "worldObj",
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
            m
          case _ => m
        }
        val writer = new ClassWriter(ClassWriter.COMPUTE_MAXS)
        node.accept(writer)
        //val checker = new TraceClassVisitor(writer, new ASMifier, new PrintWriter(System.out))
        //node.accept(checker)
        writer.toByteArray
        //data
      case "net.minecraft.client.renderer.RenderBlocks" =>
        println(s"transforming: $name")
        node.methods = for (m <- node.methods) yield m.name match {
          case "renderBlockByRenderType" =>
            println("FOUND renderBlockByRenderType")
            for(inst <- m.instructions.toArray) println(inst)
            val old = m.instructions.toArray.collectFirst{case i: MethodInsnNode => i}.get
            val list = new InsnList
            list.add(new VarInsnNode(ILOAD, 2))
            list.add(new VarInsnNode(ILOAD, 3))
            list.add(new VarInsnNode(ILOAD, 4))
            list.add(new MethodInsnNode(
              INVOKESTATIC,
              "rainwarrior/hooks/MovingRegistry",
              "getRenderType",
              "(Lnet/minecraft/block/Block;III)I"))
            m.instructions.insert(old, list)
            m.instructions.remove(old)
            println("transformed")
            for(inst <- m.instructions.toArray) println(inst)
            m
          case _ => m
        }
        val writer = new ClassWriter(ClassWriter.COMPUTE_MAXS)
        node.accept(writer)
        //val checker = new TraceClassVisitor(writer, new ASMifier, new PrintWriter(System.out))
        //node.accept(checker)
        writer.toByteArray
        //data
      case "net.minecraft.client.renderer.TileEntityRenderer" =>
        println(s"transforming: $name")
        data
      case _ => data
    }
  }
}
