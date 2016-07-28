package crazypants.enderio.machine.farm.farmers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.enderio.core.common.util.BlockCoord;

import crazypants.enderio.machine.farm.FarmNotification;
import crazypants.enderio.machine.farm.TileFarmStation;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

public class StemFarmer extends CustomSeedFarmer {

  private static final HeightCompatator COMP = new HeightCompatator();

  public StemFarmer(Block plantedBlock, ItemStack seeds) {
    super(plantedBlock, seeds);
  }

  @Override
  public boolean prepareBlock(TileFarmStation farm, BlockCoord bc, Block block, IBlockState meta) {
    if(plantedBlock == block) {
      return true;
    }
    return plantFromInventory(farm, bc);
  }

  @Override
  public boolean canHarvest(TileFarmStation farm, BlockCoord bc, Block block, IBlockState meta) {
    BlockCoord up = bc.getLocation(EnumFacing.UP);
    Block upBLock = farm.getBlock(up);
    return upBLock == plantedBlock;
  }

  @Override
  public boolean canPlant(ItemStack stack) {
    return seeds.isItemEqual(stack);
  }

  @Override
  public IHarvestResult harvestBlock(TileFarmStation farm, BlockCoord bc, Block block, IBlockState meta) {

    
    World worldObj = farm.getWorld();
    final EntityPlayerMP fakePlayer = farm.getFakePlayer();
    final int fortune = farm.getMaxLootingValue();
    HarvestResult result = new HarvestResult();
    BlockPos harvestCoord = bc.getBlockPos();
    boolean done = false;
    do{
      harvestCoord = harvestCoord.offset(EnumFacing.UP);
      boolean hasHoe = farm.hasHoe();
      if(plantedBlock == farm.getBlock(harvestCoord) && hasHoe) {
        result.harvestedBlocks.add(harvestCoord);
        List<ItemStack> drops = plantedBlock.getDrops(worldObj, harvestCoord, meta, fortune);
        float chance = ForgeEventFactory.fireBlockHarvesting(drops, worldObj, harvestCoord, meta, fortune, 1.0F, false, fakePlayer);
        if(drops != null) {
          for(ItemStack drop : drops) {
            if (worldObj.rand.nextFloat() <= chance) {
              result.drops.add(new EntityItem(worldObj, bc.x + 0.5, bc.y + 0.5, bc.z + 0.5, drop.copy()));
            }
          }
        }
        farm.damageHoe(1, new BlockCoord(harvestCoord));
        farm.actionPerformed(false);

        ItemStack[] inv = fakePlayer.inventory.mainInventory;
        for (int slot = 0; slot < inv.length; slot++) {
          ItemStack stack = inv[slot];
          if (stack != null) {
            inv[slot] = null;
            EntityItem entityitem = new EntityItem(worldObj, bc.x + 0.5, bc.y + 1, bc.z + 0.5, stack);
            result.drops.add(entityitem);
          }
        }
      } else {
        if (!hasHoe) {
          farm.setNotification(FarmNotification.NO_HOE);
        } else {
          farm.clearNotification();
        }
        done = true;
      }
    } while(!done);

    List<BlockPos> toClear = new ArrayList<BlockPos>(result.getHarvestedBlocks());
    Collections.sort(toClear, COMP);
    for (BlockPos coord : toClear) {
      farm.getWorld().setBlockToAir(coord);
    }

    return result;
  }

  @Override
  protected boolean plantFromInventory(TileFarmStation farm, BlockCoord bc) {
    World worldObj = farm.getWorld();
    if (canPlant(farm, worldObj, bc) && farm.takeSeedFromSupplies(seeds, bc) != null) {
      return plant(farm, worldObj, bc);
    }
    return false;
  }

  private static class HeightCompatator implements Comparator<BlockPos> {

    @Override
    public int compare(BlockPos o1, BlockPos o2) {
      return -compare(o1.getY(), o2.getY());
    }

    public static int compare(int x, int y) {
      return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }

  }

}
