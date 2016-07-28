package crazypants.enderio.machine.buffer;

import java.util.List;

import com.enderio.core.common.transform.EnderCoreMethods.IOverlayRenderAware;

import cofh.api.energy.IEnergyContainerItem;
import crazypants.enderio.EnderIO;
import crazypants.enderio.capacitor.DefaultCapacitorData;
import crazypants.enderio.config.Config;
import crazypants.enderio.item.PowerBarOverlayRenderHelper;
import crazypants.enderio.paint.PainterUtil2;
import crazypants.enderio.power.PowerHandlerUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import static crazypants.enderio.capacitor.CapacitorKey.BUFFER_POWER_BUFFER;

public class BlockItemBuffer extends ItemBlock implements IEnergyContainerItem, IOverlayRenderAware {

  public BlockItemBuffer(Block block, String name) {
    super(block);
    setHasSubtypes(true);
    setMaxDamage(0);
    setRegistryName(name);
  }

  @Override
  public int getMetadata(int damage) {
    return damage;
  }

  @Override
  public String getUnlocalizedName(ItemStack stack) {
    return BufferType.values()[stack.getItemDamage()].getUnlocalizedName();
  }

  @Override
  public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ,
      IBlockState newState) {
    super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, newState);

    if (newState.getBlock() == block) {
      TileEntity te = world.getTileEntity(pos);
      if (te instanceof TileBuffer) {
        TileBuffer buffer = ((TileBuffer) te);
        BufferType t = BufferType.values()[block.getMetaFromState(newState)];
        buffer.setHasInventory(t.hasInventory);
        buffer.setHasPower(t.hasPower);
        buffer.setCreative(t.isCreative);
        buffer.markDirty();
      }
    }
    return true;
  }

  @Override
  @SideOnly(Side.CLIENT)
  public void addInformation(ItemStack stack, EntityPlayer playerIn, List<String> tooltip, boolean advanced) {
    super.addInformation(stack, playerIn, tooltip, advanced);
    tooltip.add(PainterUtil2.getTooltTipText(stack));
  }

  @Override
  public void renderItemOverlayIntoGUI(ItemStack stack, int xPosition, int yPosition) {
    if (stack.stackSize == 1 && EnderIO.blockBuffer.getStateFromMeta(stack.getMetadata()).getValue(BufferType.TYPE).hasPower) {
      PowerBarOverlayRenderHelper.instance.render(stack, xPosition, yPosition);
    }
  }

  @Override
  public int receiveEnergy(ItemStack stack, int maxReceive, boolean simulate) {
    BufferType type = EnderIO.blockBuffer.getStateFromMeta(stack.getMetadata()).getValue(BufferType.TYPE);
    if (stack.stackSize == 1 && type.hasPower && !type.isCreative) {
      int energy = getEnergyStored(stack);
      int maxInput = Config.powerConduitTierThreeRF / 20;
      int energyReceived = Math.min(BUFFER_POWER_BUFFER.get(DefaultCapacitorData.BASIC_CAPACITOR) - energy, Math.min(maxReceive, maxInput));

      if (!simulate) {
        energy += energyReceived;
        PowerHandlerUtil.setStoredEnergyForItem(stack, energy);
      }
      return energyReceived;
    }
    return 0;
  }

  @Override
  public int extractEnergy(ItemStack stack, int maxExtract, boolean simulate) {
    BufferType type = EnderIO.blockBuffer.getStateFromMeta(stack.getMetadata()).getValue(BufferType.TYPE);
    if (stack.stackSize == 1 && type.hasPower) {
      int energy = PowerHandlerUtil.getStoredEnergyForItem(stack);
      int energyExtracted = Math.min(energy, Math.min(Config.powerConduitTierThreeRF / 20, maxExtract));

      if (!simulate && !type.isCreative) {
        energy -= energyExtracted;
        PowerHandlerUtil.setStoredEnergyForItem(stack, energy);
      }
      return energyExtracted;
    }
    return 0;
  }

  @Override
  public int getEnergyStored(ItemStack stack) {
    BufferType type = EnderIO.blockBuffer.getStateFromMeta(stack.getMetadata()).getValue(BufferType.TYPE);
    if (stack.stackSize == 1 && type.hasPower) {
      return type.isCreative ? BUFFER_POWER_BUFFER.get(DefaultCapacitorData.BASIC_CAPACITOR) : PowerHandlerUtil.getStoredEnergyForItem(stack);
    }
    return 0;
  }

  @Override
  public int getMaxEnergyStored(ItemStack stack) {
    if (stack.stackSize == 1 && EnderIO.blockBuffer.getStateFromMeta(stack.getMetadata()).getValue(BufferType.TYPE).hasPower) {
      return BUFFER_POWER_BUFFER.get(DefaultCapacitorData.BASIC_CAPACITOR);
    }
    return 0;
  }

  @Override
  public boolean hasEffect(ItemStack stack) {
    return EnderIO.blockBuffer.getStateFromMeta(stack.getMetadata()).getValue(BufferType.TYPE).isCreative || super.hasEffect(stack);
  }

}
