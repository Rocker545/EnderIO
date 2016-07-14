package crazypants.enderio.machine.farm.farmers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.enderio.core.common.util.BlockCoord;

import crazypants.enderio.machine.farm.FarmNotification;
import crazypants.enderio.machine.farm.TileFarmStation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStem;
import net.minecraft.block.IGrowable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.EnumPlantType;
import net.minecraftforge.common.IPlantable;

public class PlantableFarmer implements IFarmerJoe {

  private Set<Block> harvestExcludes = new HashSet<Block>();

  public void addHarvestExlude(Block block) {
    harvestExcludes.add(block);
  }

  @Override
  public boolean canPlant(ItemStack stack) {
    if(stack == null) {
      return false;
    }
    return stack.getItem() instanceof IPlantable;
  }

  @Override
  public boolean prepareBlock(TileFarmStation farm, BlockCoord bc, Block block, IBlockState meta) {
    if(block == null) {
      return false;
    }

    int slot = farm.getSupplySlotForCoord(bc.getBlockPos());
    ItemStack seedStack = farm.getSeedTypeInSuppliesFor(slot);
    if(seedStack == null) {
      if (!farm.isSlotLocked(slot)) {
        farm.setNotification(FarmNotification.NO_SEEDS);
      }
      return false;
    }

    if(!(seedStack.getItem() instanceof IPlantable)) {
      return false;
    }

    IPlantable plantable = (IPlantable) seedStack.getItem();
    EnumPlantType type = plantable.getPlantType(farm.getWorld(), bc.getBlockPos());
    if(type == null) {
      return false;
    }
    Block ground = farm.getBlock(bc.getLocation(EnumFacing.DOWN));
    if(type == EnumPlantType.Nether) {
      if(ground != Blocks.SOUL_SAND) {
        return false;
      }
      return plantFromInventory(farm, bc, plantable);
    }

    if(type == EnumPlantType.Crop) {
      farm.tillBlock(bc);        
      return plantFromInventory(farm, bc, plantable);
    }

    if (type == EnumPlantType.Water) {
      return plantFromInventory(farm, bc, plantable);
    }

    return false;
  }

  //  From BlockBush, as a reference
  //  @Override
  //  public EnumPlantType getPlantType(IBlockAccess world, int x, int y, int z)
  //  {
  //      if (this == Blocks.wheat)          return Crop;
  //      if (this == Blocks.carrots)        return Crop;
  //      if (this == Blocks.potatoes)       return Crop;
  //      if (this == Blocks.melon_stem)     return Crop;
  //      if (this == Blocks.pumpkin_stem)   return Crop;
  //      if (this == Blocks.deadbush)       return Desert;
  //      if (this == Blocks.waterlily)      return Water;
  //      if (this == Blocks.red_mushroom)   return Cave;
  //      if (this == Blocks.brown_mushroom) return Cave;
  //      if (this == Blocks.nether_wart)    return Nether;
  //      if (this == Blocks.sapling)        return Plains;
  //      if (this == Blocks.tallgrass)      return Plains;
  //      if (this == Blocks.double_plant)   return Plains;
  //      if (this == Blocks.red_flower)     return Plains;
  //      if (this == Blocks.yellow_flower)  return Plains;        
  //      return Plains;
  //  }

  protected boolean plantFromInventory(TileFarmStation farm, BlockCoord bc, IPlantable plantable) {
    World worldObj = farm.getWorld();
    if(canPlant(worldObj, bc, plantable) && farm.takeSeedFromSupplies(bc) != null) {
      return plant(farm, worldObj, bc, plantable);
    }
    return false;
  }

  protected boolean plant(TileFarmStation farm, World worldObj, BlockCoord bc, IPlantable plantable) {
    worldObj.setBlockState(bc.getBlockPos(), Blocks.AIR.getDefaultState(), 1 | 2);
    IBlockState target = plantable.getPlant(null, new BlockPos(0, 0, 0));    
    worldObj.setBlockState(bc.getBlockPos(), target, 1 | 2);
    farm.actionPerformed(false);
    return true;
  }

  protected boolean canPlant(World worldObj, BlockCoord bc, IPlantable plantable) {
    IBlockState target = plantable.getPlant(null, new BlockPos(0, 0, 0));
    BlockPos groundPos = bc.getBlockPos().down();
    IBlockState groundBS = worldObj.getBlockState(groundPos);
    Block ground = groundBS.getBlock();
    if(target != null && target.getBlock().canPlaceBlockAt(worldObj, bc.getBlockPos()) &&        
        ground.canSustainPlant(groundBS, worldObj, groundPos, EnumFacing.UP, plantable)) {
      return true;
    }
    return false;
  }

  @Override
  public boolean canHarvest(TileFarmStation farm, BlockCoord bc, Block block, IBlockState meta) {
    if(!harvestExcludes.contains(block) && block instanceof IGrowable && !(block instanceof BlockStem)) {
      return !((IGrowable) block).canGrow(farm.getWorld(), bc.getBlockPos(),meta, true);
    }
    return false;
  }

  @Override
  public IHarvestResult harvestBlock(TileFarmStation farm, BlockCoord bc, Block block, IBlockState meta) {
    if(!canHarvest(farm, bc, block, meta)) {
      return null;
    }
    if(!farm.hasHoe()) {
      farm.setNotification(FarmNotification.NO_HOE);
      return null;
    }

    World worldObj = farm.getWorld();
    List<EntityItem> result = new ArrayList<EntityItem>();

    ItemStack removedPlantable = null;

    List<ItemStack> drops = block.getDrops(worldObj, bc.getBlockPos(), meta, farm.getMaxLootingValue());
    farm.damageHoe(1, bc);
    farm.actionPerformed(false);
    boolean removed = false;
    if(drops != null) {
      for (ItemStack stack : drops) {
        if (stack != null && stack.stackSize > 0) {
          if (!removed && isPlantableForBlock(stack, block)) {
            removed = true;
            removedPlantable = stack.copy();
            removedPlantable.stackSize = 1;
            stack.stackSize--;
            if (stack.stackSize > 0) {
              result.add(new EntityItem(worldObj, bc.x + 0.5, bc.y + 0.5, bc.z + 0.5, stack.copy()));
            }
          } else {
            result.add(new EntityItem(worldObj, bc.x + 0.5, bc.y + 0.5, bc.z + 0.5, stack.copy()));
          }
        }
      }
    }

    if(removed) {
      if(!plant(farm, worldObj, bc, (IPlantable) removedPlantable.getItem())) {
        result.add(new EntityItem(worldObj, bc.x + 0.5, bc.y + 0.5, bc.z + 0.5, removedPlantable.copy()));
        worldObj.setBlockState(bc.getBlockPos(), Blocks.AIR.getDefaultState(), 1 | 2);
      }
    } else {
      worldObj.setBlockState(bc.getBlockPos(), Blocks.AIR.getDefaultState(), 1 | 2);
    }

    return new HarvestResult(result, bc.getBlockPos());
  }

  private boolean isPlantableForBlock(ItemStack stack, Block block) {
    if(!(stack.getItem() instanceof IPlantable)) {
      return false;
    }
    IPlantable plantable = (IPlantable) stack.getItem();
    return plantable.getPlant(null, new BlockPos(0, 0, 0)) == block;
  }

}
