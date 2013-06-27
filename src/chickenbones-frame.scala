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

package rainwarrior.trussmod

import java.lang.{ Iterable => JIterable }
import collection.JavaConversions._
import net.minecraft._,
  block.{ Block, BlockContainer },
  block.material.Material,
  client.Minecraft.{ getMinecraft => mc },
  client.renderer.texture.IconRegister,
  client.renderer.Tessellator.{ instance => tes },
  creativetab.CreativeTabs,
  entity.player.EntityPlayer,
  item.{ Item, ItemStack },
  world.World
import net.minecraftforge.common.ForgeDirection
import cpw.mods.fml.relauncher.{ SideOnly, Side }
import TrussMod._
import rainwarrior.utils._

import codechicken.core.vec.{ BlockCoord, Vector3, Rotation, Cuboid6 }
import codechicken.core.lighting.LazyLightMatrix
import codechicken.core.raytracer.{ RayTracer, IndexedCuboid6 }
import codechicken.multipart.{ TileMultipart, TMultiPart, TItemMultiPart }
import codechicken.microblock.MicroblockClass

trait FrameTile extends TileMultipart with Frame {
  override def isSideSticky(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) = {
    (partList collectFirst {
      case part: MicroblockClass => 
    }).isEmpty
  }
}

trait FrameMarker

class ChickenBonesFramePart extends TMultiPart with FrameMarker {
  lazy val icon = CommonProxy.blockFrame.getIcon(0, 0)

  override val getType = "Frame"

  override val getSubParts: JIterable[IndexedCuboid6] = Seq(new IndexedCuboid6(0, new Cuboid6(-eps, -eps, -eps, 1 + eps, 1 + eps, 1 + eps)))

  override val getCollisionBoxes: JIterable[Cuboid6] = Seq(new Cuboid6(0, 0, 0, 1, 1, 1))

  override def occlusionTest(part: TMultiPart) = part match {
    case part: ChickenBonesFramePart => false
    case _ => true
  }

  @SideOnly(Side.CLIENT)
  override def renderStatic(pos: Vector3, olm: LazyLightMatrix, pass: Int) {
    //tes.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z))
    tes.setColorOpaque_F(1, 1, 1)
    tes.addTranslation((pos.x + .5F).toFloat, (pos.y + .5F).toFloat, (pos.z + .5F).toFloat)
    model.render("Frame", "Frame", icon)
    tes.addTranslation((-pos.x - .5F).toFloat, (-pos.y - .5F).toFloat, (-pos.z - .5F).toFloat)
  }

  override val doesTick = false
}

trait ChickenBonesFrameItem extends Item {
  setCreativeTab(CreativeTabs.tabMisc)
  setUnlocalizedName(modId + ":FrameItem")

  import cpw.mods.fml.common.registry._
  LanguageRegistry.addName(this, "FrameItem")

  override def registerIcons(reg: IconRegister) {}

  def getHitDepth(vhit: Vector3, side: Int) =
    vhit.copy.scalarProject(Rotation.axes(side)) + (side % 2 ^ 1)

  override def onItemUse(
    stack: ItemStack,
    player: EntityPlayer,
    world: World,
    x: Int, y: Int, z: Int,
    side: Int,
    hitX: Float, hitY:Float, hitZ:Float
  ) = if(!world.isClient) {
    val newPart = new ChickenBonesFramePart
    val pos = new BlockCoord(x, y, z)
    val d = getHitDepth(new Vector3(hitX, hitY, hitZ), side)

    if(d > 1 || !TileMultipart.canPlacePart(world, pos, newPart))
      pos.offset(side)

    if(TileMultipart.canPlacePart(world, pos, newPart)) {
      TileMultipart.addPart(world, pos, newPart)
      if(!player.capabilities.isCreativeMode)
        stack.stackSize -= 1
      true
    } else false
  } else true

  /*override def newPart(
    stack: ItemStack,
    player:EntityPlayer,
    world:World,
    pos:BlockCoord,
    side:Int,
    vhit:Vector3
  ) = new ChickenBonesFramePart*/
}