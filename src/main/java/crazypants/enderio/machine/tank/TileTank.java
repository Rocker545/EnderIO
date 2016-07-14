package crazypants.enderio.machine.tank;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.enderio.core.api.common.util.ITankAccess;
import com.enderio.core.common.fluid.FluidWrapper;
import com.enderio.core.common.util.FluidUtil;
import com.enderio.core.common.util.FluidUtil.FluidAndStackResult;
import com.enderio.core.common.util.ItemUtil;

import crazypants.enderio.EnderIO;
import crazypants.enderio.fluid.SmartTankFluidHandler;
import crazypants.enderio.fluid.SmartTankFluidMachineHandler;
import crazypants.enderio.machine.AbstractMachineEntity;
import crazypants.enderio.machine.SlotDefinition;
import crazypants.enderio.network.PacketHandler;
import crazypants.enderio.paint.IPaintable;
import crazypants.enderio.tool.SmartTank;
import info.loenwind.autosave.annotations.Storable;
import info.loenwind.autosave.annotations.Store;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

@Storable
public class TileTank extends AbstractMachineEntity implements ITankAccess.IExtendedTankAccess, IPaintable.IPaintableTileEntity {

  private static int IO_MB_TICK = 100;

  @Store
  protected SmartTank tank;
  protected int lastUpdateLevel = -1;
  
  private boolean tankDirty = false;
  private int lastFluidLuminosity = 0;

  @Store
  private VoidMode voidMode = VoidMode.NEVER;

  public TileTank(int meta) {
    super(new SlotDefinition(0, 2, 3, 4, -1, -1));
    if(meta == 1) {
      tank = new SmartTank(32000);
    } else {
      tank = new SmartTank(16000);
    }
    tank.setTileEntity(this);
  }

  public TileTank() {
    this(0);
  }

  @Override
  protected boolean doPush(@Nullable EnumFacing dir) {
    boolean res = super.doPush(dir);
    if (dir != null && tank.getFluidAmount() > 0) {
      if (FluidWrapper.transfer(tank, worldObj, getPos().offset(dir), dir.getOpposite(), IO_MB_TICK) > 0) {
        setTanksDirty();
      }
    }
    return res;
  }

  @Override
  protected boolean doPull(@Nullable EnumFacing dir) {
    boolean res = super.doPull(dir);
    if (dir != null && tank.getFluidAmount() < tank.getCapacity()) {
      if (FluidWrapper.transfer(worldObj, getPos().offset(dir), dir.getOpposite(), tank, IO_MB_TICK) > 0) {
        setTanksDirty();
      }
    }
    return res;
  }

  private int getFilledLevel() {
    int level = (int) Math.floor(16 * tank.getFilledRatio());
    if(level == 0 && tank.getFluidAmount() > 0) {
      level = 1;
    }
    return level;
  }

  public boolean canVoidItems() {
    return tank.getFluid() != null && tank.getFluid().getFluid().getTemperature() > 973;
  }
  
  public VoidMode getVoidMode() {
    return voidMode;
  }
  
  public void setVoidMode(VoidMode mode) {
    this.voidMode = mode;
  }
  
  @Override
  public @Nonnull String getMachineName() {
    return "tank";
  }

  @Override
  protected boolean isMachineItemValidForSlot(int i, ItemStack item) {
    if (canVoidItems() && voidMode == VoidMode.ALWAYS && i < getSlotDefinition().getMaxInputSlot()) {
      return false;
    }
    if (i == 0) {
      FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(item);
      if (fluid != null) {
        return true;
      }
      if (item.getItem() == Items.WATER_BUCKET) {
        return true;
      }
      if (item.getItem() == Items.LAVA_BUCKET) {
        return true;
      }
      if (item.getItem() instanceof IFluidContainerItem && ((IFluidContainerItem) item.getItem()).getFluid(item) != null) {
        return true;
      }
      return false;
    } else if (i == 1) {
      if (item.getItem() instanceof IFluidContainerItem
          && (((IFluidContainerItem) item.getItem()).getFluid(item) == null || ((IFluidContainerItem) item.getItem()).getFluid(item).amount < ((IFluidContainerItem) item
              .getItem()).getCapacity(item))) {
        return true;
      }
      return FluidContainerRegistry.isEmptyContainer(item) || item.getItem() == Items.BUCKET;
    } else if (i == 2 && canVoidItems()) {
      return voidMode == VoidMode.ALWAYS || (voidMode == VoidMode.NEVER ? false : !FluidContainerRegistry.isContainer(item));
    }
    return false;
  }
  
  @Override
  public void setInventorySlotContents(int slot, @Nullable ItemStack contents) {
    super.setInventorySlotContents(slot, contents);
  }

  @Override
  public boolean isActive() {
    return false;
  }
  
  private static long lastSendTickAll = -1;
  private long nextSendTickThis = -1;
  private int sendPrio = 0;

  /*
   * Limit sending of client updates because a group of tanks pushing into each other can severely kill the clients fps from doing these updates.
   */
  private boolean canSendClientUpdate() {
    long tick = EnderIO.proxy.getTickCount();
    if (nextSendTickThis > tick) {
      return false;
    }
    if (tick == lastSendTickAll && sendPrio++ < 200) {
      return false;
    }
    nextSendTickThis = (lastSendTickAll = tick) + 10 + sendPrio * 2;
    sendPrio = 0;
    return true;
  }

  @Override
  protected boolean processTasks(boolean redstoneCheck) {
    processItems(redstoneCheck);
    int filledLevel = getFilledLevel();
    if(lastUpdateLevel != filledLevel) {
      lastUpdateLevel = filledLevel;
      setTanksDirty();
    }
    if (tankDirty && canSendClientUpdate()) {
      PacketHandler.sendToAllAround(new PacketTankFluid(this), this);
      worldObj.updateComparatorOutputLevel(pos, getBlockType());
      int thisFluidLuminosity = tank.getFluid() == null || tank.getFluid().getFluid() == null || tank.getFluidAmount() == 0 ? 0 : tank.getFluid().getFluid()
          .getLuminosity(tank.getFluid());
      if (thisFluidLuminosity != lastFluidLuminosity) {
        worldObj.checkLight(getPos());
        worldObj.markAndNotifyBlock(pos, worldObj.getChunkFromBlockCoords(pos), worldObj.getBlockState(pos), worldObj.getBlockState(pos), 3);
        // worldObj.notifyLightSet(getPos());
        // worldObj.checkLightFor(EnumSkyBlock.BLOCK, pos);
        // updateBlock();
        lastFluidLuminosity = thisFluidLuminosity;
      }
      tankDirty = false;
    }
    return false;
  }
  
  public int getComparatorOutput() {
    if (tank.isEmpty()) {
      return 0;
    }

    return (int) (1 + ((double) tank.getFluidAmount() / (double) tank.getCapacity()) * 14);
  }

  private boolean processItems(boolean redstoneCheck) {
    if(!redstoneCheck) {
      return false;
    }
    if(!shouldDoWorkThisTick(20)) {
      return false;
    }
    if (canVoidItems()) {
      inventory[2] = null;
      markDirty();
    }
    return drainFullContainer() || fillEmptyContainer();
  }

  private boolean fillEmptyContainer() {
    FluidAndStackResult fill = FluidUtil.tryFillContainer(inventory[getSlotDefinition().getMinInputSlot() + 1], getOutputTanks()[0].getFluid());
    if (fill.result.fluidStack == null) {
      return false;
    }
    
    int slot = getSlotDefinition().getMaxOutputSlot();

    if (inventory[slot] != null) {
      if (inventory[slot].isStackable() && ItemUtil.areStackMergable(inventory[slot], fill.result.itemStack)
          && inventory[slot].stackSize < inventory[slot].getMaxStackSize()) {
        fill.result.itemStack.stackSize += inventory[slot].stackSize;
      } else {
        return false;
      }
    }

    getOutputTanks()[0].setFluid(fill.remainder.fluidStack);
    setInventorySlotContents(getSlotDefinition().getMinInputSlot() + 1, fill.remainder.itemStack);
    setInventorySlotContents(slot, fill.result.itemStack);

    setTanksDirty();
    markDirty();
    return false;
  }

  private boolean drainFullContainer() {
    FluidAndStackResult fill = FluidUtil.tryDrainContainer(inventory[getSlotDefinition().getMinInputSlot()], this);
    if (fill.result.fluidStack == null) {
      return false;
    }
    
    int slot = getSlotDefinition().getMinOutputSlot();

    if (inventory[slot] != null && fill.result.itemStack != null) {
      if (inventory[slot].isStackable() && ItemUtil.areStackMergable(inventory[slot], fill.result.itemStack)
          && inventory[slot].stackSize < inventory[slot].getMaxStackSize()) {
        fill.result.itemStack.stackSize += inventory[slot].stackSize;
      } else {
        return false;
      }
    }

    getInputTank(fill.result.fluidStack).setFluid(fill.remainder.fluidStack);
    setInventorySlotContents(getSlotDefinition().getMinInputSlot(), fill.remainder.itemStack);
    if (fill.result.itemStack != null) {
      setInventorySlotContents(slot, fill.result.itemStack);
    }

    setTanksDirty();
    markDirty();
    return false;
  }

  @Override
  public FluidTank getInputTank(FluidStack forFluidType) {
    return tank;
  }

  @Override
  public FluidTank[] getOutputTanks() {
    return new FluidTank[] { tank };
  }

  @Override
  public void setTanksDirty() {
    if (!tankDirty) {
      tankDirty = true;
      markDirty();
    }
  }

  @Override
  public boolean shouldRenderInPass(int pass) {
    return pass == 1 && tank.getFluidAmount() > 0;
  }

  @SuppressWarnings("null")
  @Override
  @Nonnull
  public List<ITankData> getTankDisplayData() {
    return Collections.<ITankData> singletonList(new ITankData() {

      @Override
      @Nonnull
      public EnumTankType getTankType() {
        return EnumTankType.STORAGE;
      }

      @Override
      @Nullable
      public FluidStack getContent() {
        return tank.getFluid();
      }

      @Override
      public int getCapacity() {
        return tank.getCapacity();
      }
    });
  }

  private SmartTankFluidHandler smartTankFluidHandler;

  @Override
  public boolean hasCapability(Capability<?> capability, EnumFacing facingIn) {
    return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, facingIn);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T getCapability(Capability<T> capability, EnumFacing facingIn) {
    if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
      if (smartTankFluidHandler == null) {
        smartTankFluidHandler = new SmartTankFluidMachineHandler(this, tank);
      }
      return (T) smartTankFluidHandler.get(facingIn);
    }
    return super.getCapability(capability, facingIn);
  }

}
