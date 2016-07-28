package crazypants.enderio;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.enderio.core.common.BlockEnder;

import crazypants.enderio.api.tool.ITool;
import crazypants.enderio.machine.AbstractMachineEntity;
import crazypants.enderio.tool.ToolUtil;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public abstract class BlockEio<T extends TileEntityEio> extends BlockEnder<T> {

  protected BlockEio(@Nonnull String name, @Nullable Class<T> teClass) {
    super(name, teClass);
    setCreativeTab(EnderIOTab.tabEnderIO);
  }

  protected BlockEio(@Nonnull String name, @Nullable Class<T> teClass, @Nonnull Material mat) {
    super(name, teClass, mat);
    setCreativeTab(EnderIOTab.tabEnderIO);
  }

  @Override
  public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer entityPlayer, EnumHand hand, @Nullable ItemStack heldItem,
      EnumFacing side,
      float hitX, float hitY, float hitZ) {
    if (shouldWrench(world, pos, entityPlayer, side) && ToolUtil.breakBlockWithTool(this, world, pos, entityPlayer, heldItem)) {
      return true;
    }

    TileEntity te = getTileEntity(world, pos);
    if (te instanceof AbstractMachineEntity) {
      ITool tool = ToolUtil.getToolFromStack(heldItem);
      if (tool != null && !entityPlayer.isSneaking() && tool.canUse(heldItem, entityPlayer, pos)) {
        ((AbstractMachineEntity) te).toggleIoModeForFace(side);
        IBlockState bs = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, bs, bs, 3);
        return true;
      }
    }

    return super.onBlockActivated(world, pos, state, entityPlayer, hand, heldItem, side, hitX, hitY, hitZ);
  }

  public boolean shouldWrench(World world, BlockPos pos, EntityPlayer entityPlayer, EnumFacing side) {
    return true;
  }
}
