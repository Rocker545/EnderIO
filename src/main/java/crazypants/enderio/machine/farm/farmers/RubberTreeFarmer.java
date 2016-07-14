package crazypants.enderio.machine.farm.farmers;

import java.util.List;
import java.util.Random;

import com.enderio.core.common.util.BlockCoord;

import crazypants.enderio.machine.farm.FarmNotification;
import crazypants.enderio.machine.farm.FarmStationContainer;
import crazypants.enderio.machine.farm.TileFarmStation;
import crazypants.enderio.machine.farm.TileFarmStation.ToolType;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.IShearable;

public abstract class RubberTreeFarmer extends TreeFarmer {

  protected ItemStack stickyResin;

  public RubberTreeFarmer(Block sapling, Block wood, Item treetap, ItemStack resin) {
    super(sapling, wood);
    crazypants.enderio.machine.farm.TileFarmStation.TREETAPS.add(treetap);
    stickyResin = resin;
    FarmStationContainer.slotItemsProduce.add(stickyResin);
  }

  public boolean isValid() {
    return woods != null && woods.length > 0 && sapling != null && saplingItem != null && stickyResin != null;
  }

  @Override
  public boolean prepareBlock(TileFarmStation farm, BlockCoord bc, Block blockIn, IBlockState meta) {
    if (canPlant(farm.getSeedTypeInSuppliesFor(bc))) {
      // we'll lose some spots in the center, but we can plant in the outer ring, which gives a net gain
      if (Math.abs(farm.getPos().getX() - bc.x) % 2 == 0) {
        return true;
      }
      if (Math.abs(farm.getPos().getZ() - bc.z) % 2 == 0) {
        return true;
      }
      final World world = farm.getWorld();
      for (int x = -1; x < 2; x++) {
        for (int z = -1; z < 2; z++) {
          final BlockPos pos = new BlockPos(bc.x + x, bc.y, bc.z + z);
          final IBlockState state = world.getBlockState(pos);
          final Block block = state.getBlock();
          if (!(block.isLeaves(state, world, pos) || block.isAir(state, world, pos) || block.canBeReplacedByLeaves(state, world, pos))) {
            return true;
          }
        }
      }
      return super.prepareBlock(farm, bc, blockIn, meta);
    }
    return false;
  }

  @Override
  public IHarvestResult harvestBlock(TileFarmStation farm, BlockCoord bc, Block block, IBlockState meta) {
    HarvestResult res = new HarvestResult();
    final World world = farm.getWorld();
    BlockPos pos = bc.getBlockPos();

    int noShearingPercentage = farm.isLowOnSaplings(bc);
    int shearCount = 0;

    while (pos.getY() <= 255) {
      IBlockState state = world.getBlockState(pos);
      if (isWood(state.getBlock())) {
        if (canHarvest(world, pos)) {
          if (farm.hasTool(ToolType.TREETAP)) {
            harvest(res, world, pos);
            farm.damageTool(ToolType.TREETAP, woods[0], bc, 1);
          } else {
            farm.setNotification(FarmNotification.NO_TREETAP);
          }
        }
        for (EnumFacing face : EnumFacing.Plane.HORIZONTAL) {
          shearCount = harvestLeavesBlock(farm, res, world, pos.offset(face), noShearingPercentage, shearCount);
        }
      } else if (TreeHarvestUtil.isLeaves(state)) {
        shearCount = harvestLeavesBlock(farm, res, world, pos, noShearingPercentage, shearCount);
      } else {
        return res;
      }
      pos = pos.up();
    }
    return res;
  }

  private int harvestLeavesBlock(TileFarmStation farm, HarvestResult res, final World world, final BlockPos pos, int noShearingPercentage, int shearCount) {
    IBlockState state = world.getBlockState(pos);
    if (TreeHarvestUtil.isLeaves(state)) {
      List<ItemStack> drops;
      if (state.getBlock() instanceof IShearable && farm.hasShears() && ((shearCount / (res.harvestedBlocks.size() + 1) + noShearingPercentage) < 100)) {
        drops = ((IShearable) state.getBlock()).onSheared(null, farm.getWorld(), pos, 0);
        shearCount += 100;
        farm.damageShears(state.getBlock(), new BlockCoord(pos));
      } else if (farm.hasHoe()) {
        drops = state.getBlock().getDrops(farm.getWorld(), pos, state, farm.getAxeLootingValue());
        farm.damageHoe(1, new BlockCoord(pos));
      } else {
        return shearCount;
      }
      world.setBlockToAir(pos);
      for (ItemStack drop : drops) {
        EntityItem dropEnt = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, drop);
        res.getDrops().add(dropEnt);
      }
      res.getHarvestedBlocks().add(pos);
      for (EnumFacing face : EnumFacing.Plane.HORIZONTAL) {
        shearCount = harvestLeavesBlock(farm, res, world, pos.offset(face), noShearingPercentage, shearCount);
      }
    }
    return shearCount;
  }

  private boolean canHarvest(World world, BlockPos pos) {
    return hasResin(world.getBlockState(pos));
  }

  private void harvest(HarvestResult res, World world, BlockPos pos) {
    world.setBlockState(pos, removeResin(world.getBlockState(pos)), 3);
    ItemStack drop = makeResin(world.rand);
    EntityItem dropEnt = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, drop);
    res.getDrops().add(dropEnt);
    res.getHarvestedBlocks().add(pos);
  }

  protected ItemStack makeResin(Random rand) {
    return stickyResin.copy();
  }

  protected abstract boolean hasResin(IBlockState state);

  protected abstract IBlockState removeResin(IBlockState state);

}
