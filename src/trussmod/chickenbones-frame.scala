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
  item.{ Item, ItemBlock, ItemStack },
  world.World,
  util.{ MovingObjectPosition, Vec3 }
import net.minecraftforge.common.ForgeDirection
import cpw.mods.fml.relauncher.{ SideOnly, Side }
import TrussMod._
import rainwarrior.utils.{ Vector3 => MyVector3, _}

import codechicken.lib.vec.{ BlockCoord, Vector3, Rotation, Cuboid6 }
import codechicken.lib.lighting.{ LazyLightMatrix, LC }
import codechicken.lib.raytracer.{ ExtendedMOP, RayTracer, IndexedCuboid6 }
import codechicken.multipart.{
  MultiPartRegistry,
  TileMultipart,
  TMultiPart,
  TItemMultiPart,
  JPartialOcclusion,
  TNormalOcclusion,
  scalatraits
}
import scalatraits.TSlottedTile
import codechicken.microblock.CommonMicroblock

class ChickenBonesFramePart(val id: Int) extends TMultiPart with Frame with JPartialOcclusion with TNormalOcclusion {
  override val getType = "Frame"

  override def getStrength(mop: MovingObjectPosition, player: EntityPlayer) = 5F

  override def getDrops: JIterable[ItemStack] = Seq(new ItemStack(id, 1, 0))
  //override val getSubParts: JIterable[IndexedCuboid6] = Seq(new IndexedCuboid6(0, new Cuboid6(-eps, -eps, -eps, 1 + eps, 1 + eps, 1 + eps)))

  //override val getSubParts: JIterable[IndexedCuboid6] = Seq(new IndexedCuboid6(0, new Cuboid6(0, 0, 0, 1, 1, 1)))
  /*override val getSubParts: JIterable[IndexedCuboid6] = Seq(
    new IndexedCuboid6(0, new Cuboid6(eps, eps, eps, 1 - eps, 1 - eps, 1 - eps))
    //new IndexedCuboid6(0, new Cuboid6(.25, .25, .25, .75, .75, .75))
  )*/

  override def collisionRayTrace(from: Vec3, to: Vec3) = {
    blockRayTrace(world, x, y, z, from, to, model.getPartFaces("Frame", "Frame")) match {
      case mop: MovingObjectPosition =>
        new ExtendedMOP(mop, 0, from.squareDistanceTo(mop.hitVec))
      case _ => null
    }
  }

  override val getCollisionBoxes: JIterable[Cuboid6] = Seq(new Cuboid6(0, 0, 0, 1, 1, 1))
  //override val getCollisionBoxes: JIterable[Cuboid6] = Seq(new Cuboid6(.25, .25, .25, .75, .75, .75))

  override def occlusionTest(part: TMultiPart) = part match {
    case part: ChickenBonesFramePart => false
    case _ => true
  }

  //override val getPartialOcclusionBoxes: JIterable[Cuboid6] = Seq(new Cuboid6(0, 0, 0, 1, 1, 1))
  override val getPartialOcclusionBoxes: JIterable[Cuboid6] = Seq()
  override val allowCompleteOcclusion = true

  override val getOcclusionBoxes: JIterable[Cuboid6] = Seq()

  override val doesTick = false

  @SideOnly(Side.CLIENT)
  override def renderStatic(pos: Vector3, olm: LazyLightMatrix, pass: Int) = if(pass == 0) {
    BlockFrameRenderer.renderWithSides(
      tile.worldObj,
      x, y, z,
      tile.blockType,
      for(s <- ForgeDirection.VALID_DIRECTIONS) yield isSideSticky(s))
  }

  override def isSideSticky(world: World, x: Int, y: Int, z: Int, side: ForgeDirection) =
    isSideSticky(side)

  def isSideSticky(side: ForgeDirection) = {
    tile.partMap(side.ordinal) match {
      case part: CommonMicroblock =>
        part.getSize != 1
      case _ => true
    }
  }
  @SideOnly(Side.CLIENT)
  override def drawHighlight(hit: MovingObjectPosition, player: EntityPlayer, frame: Float): Boolean = {
    true // TODO
  }
}

class ChickenBonesFrameItem(id: Int) extends ItemBlock(id) with ChickenBonesFrameTrait

trait ChickenBonesFrameTrait extends ItemBlock {
  setCreativeTab(CreativeTabs.tabMisc)
  setUnlocalizedName(modId + ":FrameItem")

  import cpw.mods.fml.common.registry._
  LanguageRegistry.addName(this, "FrameItem")

  override def registerIcons(reg: IconRegister) {}

  def getTile(world: World, pos: BlockCoord) = 
    TileMultipart.getOrConvertTile(world, pos) match {
      case t: TileMultipart => Some(t)
      case _ => None
    }

  override def onItemUse(
    stack: ItemStack,
    player: EntityPlayer,
    world: World,
    x: Int, y: Int, z: Int,
    side: Int,
    hitX: Float, hitY:Float, hitZ:Float
  ) = {
    //log.info(s"($x, $y, $z), $side, ($hitX, $hitY, $hitZ)")
    val newPart = new ChickenBonesFramePart(this.itemID)
    val pos = new BlockCoord(x, y, z)

    def canPlace = 
      newPart.getCollisionBoxes.forall(b => world.checkNoEntityCollision(b.toAABB().offset(x, y, z))) && (
        getTile(world, pos).exists(t => t.partList.collectFirst{ case p: Frame =>}.isEmpty)
        || TileMultipart.replaceable(world, pos)
      )

    if(!canPlace)
      pos.offset(side)

    if(TileMultipart.replaceable(world, pos)) {
      super.onItemUse(stack, player, world, x, y, z, side, hitX, hitY, hitZ)
    } else if(canPlace) {
      if(!world.isClient)
        TileMultipart.addPart(world, pos, newPart)
      if(!player.capabilities.isCreativeMode)
        stack.stackSize -= 1
      true
    } else false
  }

  override def canPlaceItemBlockOnSide(
    world: World,
    x: Int, y: Int, z: Int,
    side: Int,
    player: EntityPlayer,
    stack: ItemStack) = true
}

object ChickenBonesPartConverter extends MultiPartRegistry.IPartConverter {
  override def canConvert(blockId: Int): Boolean = blockId == CommonProxy.blockFrameId

  override def convert(world: World, pos: BlockCoord): TMultiPart = {
    world.getBlockId(pos.x, pos.y, pos.z) match {
      case CommonProxy.blockFrameId =>
        new ChickenBonesFramePart(CommonProxy.blockFrameId - 256)
      case id =>
        log.warning(s"Called CB converter for wrong block ID: $id")
        null
    }
  }
}
