package crazypants.enderio.machine.invpanel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.enderio.core.api.common.util.ITankAccess;

import crazypants.enderio.ModObject;
import crazypants.enderio.conduit.TileConduitBundle;
import crazypants.enderio.conduit.item.FilterRegister;
import crazypants.enderio.conduit.item.ItemConduit;
import crazypants.enderio.conduit.item.ItemConduitNetwork;
import crazypants.enderio.conduit.item.filter.IItemFilter;
import crazypants.enderio.config.Config;
import crazypants.enderio.fluid.Fluids;
import crazypants.enderio.fluid.SmartTankFluidHandler;
import crazypants.enderio.fluid.SmartTankFluidMachineHandler;
import crazypants.enderio.machine.AbstractMachineEntity;
import crazypants.enderio.machine.IoMode;
import crazypants.enderio.machine.SlotDefinition;
import crazypants.enderio.machine.generator.zombie.IHasNutrientTank;
import crazypants.enderio.machine.generator.zombie.PacketNutrientTank;
import crazypants.enderio.machine.invpanel.client.ClientDatabaseManager;
import crazypants.enderio.machine.invpanel.client.InventoryDatabaseClient;
import crazypants.enderio.machine.invpanel.server.InventoryDatabaseServer;
import crazypants.enderio.network.PacketHandler;
import crazypants.enderio.tool.SmartTank;
import info.loenwind.autosave.annotations.Storable;
import info.loenwind.autosave.annotations.Store;
import info.loenwind.autosave.annotations.Store.StoreFor;
import info.loenwind.autosave.handlers.enderio.HandleStoredCraftingRecipe.HandleStoredCraftingRecipeArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

@Storable
public class TileInventoryPanel extends AbstractMachineEntity implements ITankAccess.IExtendedTankAccess, IHasNutrientTank {

  public static final int SLOT_CRAFTING_START = 0;
  public static final int SLOT_CRAFTING_RESULT = 9;
  public static final int SLOT_VIEW_FILTER = 10;
  public static final int SLOT_RETURN_START = 11;

  public static final int MAX_STORED_CRAFTING_RECIPES = 6;

  @Store
  protected final SmartTank fuelTank;
  protected boolean tanksDirty;

  private InventoryDatabaseServer dbServer;
  private InventoryDatabaseClient dbClient;

  @Store({ StoreFor.CLIENT, StoreFor.SAVE })
  private boolean active;
  @Store
  private boolean extractionDisabled;

  public InventoryPanelContainer eventHandler;
  private IItemFilter itemFilter;

  @Store
  private int guiSortMode;
  @Store
  private String guiFilterString = "";
  @Store
  private boolean guiSync;

  @Store(handler = HandleStoredCraftingRecipeArrayList.class)
  private final ArrayList<StoredCraftingRecipe> storedCraftingRecipes;

  public TileInventoryPanel() {
    super(new SlotDefinition(0, 8, 11, 20, 21, 20));
    this.fuelTank = new SmartTank(Fluids.fluidNutrientDistillation, Config.inventoryPanelFree ? 0 : 2000);
    this.fuelTank.setTileEntity(this);
    this.fuelTank.setCanDrain(false);
    this.storedCraftingRecipes = new ArrayList<StoredCraftingRecipe>();
  }

  public InventoryDatabaseServer getDatabaseServer() {
    return dbServer;
  }

  public InventoryDatabaseClient getDatabaseClient(int generation) {
    if(dbClient != null && dbClient.getGeneration() != generation) {
      ClientDatabaseManager.INSTANCE.destroyDatabase(dbClient.getGeneration());
      dbClient = null;
    }
    if(dbClient == null) {
      dbClient = ClientDatabaseManager.INSTANCE.getOrCreateDatabase(generation);
    }
    return dbClient;
  }

  public @Nullable InventoryDatabaseClient getDatabaseClient() {
    return dbClient;
  }

  @Override
  public boolean canInsertItem(int slot, ItemStack itemstack, EnumFacing side) {  
    return false;
  }

  @Override
  protected boolean canExtractItem(int slot, ItemStack itemstack) {
    return !extractionDisabled && super.canExtractItem(slot, itemstack);
  }

  @Override
  protected boolean isMachineItemValidForSlot(int slot, ItemStack stack) {
    if(slot == SLOT_VIEW_FILTER && stack != null) {
      return FilterRegister.isItemFilter(stack) && FilterRegister.isFilterSet(stack);
    }
    return true;
  }

  @Override
  public ItemStack decrStackSize(int fromSlot, int amount) {
    ItemStack res = super.decrStackSize(fromSlot, amount);
    if(res != null && fromSlot < SLOT_CRAFTING_RESULT && eventHandler != null) {
      eventHandler.onCraftMatrixChanged(this);
    }
    if(res != null && fromSlot == SLOT_VIEW_FILTER) {
      updateItemFilter();
    }
    return res;
  }

  @Override
  public void setInventorySlotContents(int slot, @Nullable ItemStack contents) {
    super.setInventorySlotContents(slot, contents);
    if(slot < SLOT_CRAFTING_RESULT && eventHandler != null) {
      eventHandler.onCraftMatrixChanged(this);
    }
    if(slot == SLOT_VIEW_FILTER) {
      updateItemFilter();
    }
  }

  private void updateItemFilter() {
    itemFilter = FilterRegister.getFilterForUpgrade(inventory[SLOT_VIEW_FILTER]);
  }

  public IItemFilter getItemFilter() {
    return itemFilter;
  }

  @Override
  public boolean isActive() {
    return Config.inventoryPanelFree || active;
  }

  @Override
  public void doUpdate() {
    if(worldObj.isRemote) {
      updateEntityClient();
      return;
    }

    if(shouldDoWorkThisTick(20)) {
      scanNetwork();
    }

    if (forceClientUpdate.peek()) {      
      IBlockState bs = getWorld().getBlockState(pos);
      getWorld().notifyBlockUpdate(pos, bs, bs, 3);            
      markDirty();
    }

    if(tanksDirty) {
      tanksDirty = false;
      PacketHandler.sendToAllAround(new PacketNutrientTank(this), this);
    }
  }

  private void scanNetwork() {
    EnumFacing facingDir = getFacing();
    EnumFacing backside = facingDir.getOpposite();

    ItemConduitNetwork icn = null;

    BlockPos p = pos.offset(backside);
    TileEntity te = worldObj.getTileEntity(p);
    if(te instanceof TileConduitBundle) {
      TileConduitBundle teCB = (TileConduitBundle) te;
      ItemConduit conduit = teCB.getConduit(ItemConduit.class);
      if(conduit != null) {
        icn = (ItemConduitNetwork) conduit.getNetwork();
      }
    }

    if(icn != null) {
      dbServer = icn.getDatabase();
      dbServer.sendChangeLogs();
      refuelPower(dbServer);

      if(active != dbServer.isOperational()) {
        active = dbServer.isOperational();
        forceClientUpdate.set();
      }
    } else {
      if(active) {
        forceClientUpdate.set();
      }
      dbServer = null;
      active = false;
    }
  }

  public float getAvailablePower() {
    return getPower() * Config.inventoryPanelPowerPerMB;
  }

  public void refuelPower(InventoryDatabaseServer db) {
    float missingPower = Config.inventoryPanelPowerPerMB * 0.5f - db.getPower();
    if(missingPower > 0) {
      int amount = (int) Math.ceil(missingPower / Config.inventoryPanelPowerPerMB);
      amount = Math.min(amount, getPower());
      if(amount > 0) {
        useNutrient(amount);
        dbServer.addPower(amount * Config.inventoryPanelPowerPerMB);
      }
    }
  }

  public void useNutrient(int amount) {
    fuelTank.removeFluidAmount(amount);
  }
  
  private int getPower() {
    return Config.inventoryPanelFree ? 100 : fuelTank.getFluidAmount();
  }

  @Override
  protected boolean processTasks(boolean redstoneCheck) {
    return false;
  }

  public int getGuiSortMode() {
    return guiSortMode;
  }

  public String getGuiFilterString() {
    return guiFilterString;
  }
  
  public boolean getGuiSync() {
    return guiSync;
  }

  public void setGuiParameter(int sortMode, String filterString, boolean sync) {
    this.guiSortMode = sortMode;
    this.guiFilterString = filterString;
    this.guiSync = sync;
    if (worldObj != null && !worldObj.isRemote) {
      PacketHandler.INSTANCE.sendToDimension(new PacketGuiSettingsUpdated(this), worldObj.provider.getDimension());
      markDirty();
    }
  }

  public int getStoredCraftingRecipes() {
    return storedCraftingRecipes.size();
  }

  public StoredCraftingRecipe getStoredCraftingRecipe(int index) {
    if(index < 0 || index >= storedCraftingRecipes.size()) {
      return null;
    }
    return storedCraftingRecipes.get(index);
  }

  public void addStoredCraftingRecipe(StoredCraftingRecipe recipe) {
    storedCraftingRecipes.add(recipe);
    if (worldObj == null || worldObj.isRemote) {
      PacketHandler.INSTANCE.sendToServer(new PacketStoredCraftingRecipe(PacketStoredCraftingRecipe.ACTION_ADD, 0, recipe));
    } else {
      markDirty();
      updateBlock();
    }
  }

  public void removeStoredCraftingRecipe(int index) {
    if (index >= 0 && index < storedCraftingRecipes.size()) {
      storedCraftingRecipes.remove(index);
      if (worldObj == null || worldObj.isRemote) {
        PacketHandler.INSTANCE.sendToServer(new PacketStoredCraftingRecipe(PacketStoredCraftingRecipe.ACTION_DELETE, index, null));
      } else {
        markDirty();
        updateBlock();
      }
    }
  }

  public boolean isExtractionDisabled() {
    return extractionDisabled;
  }

  public void setExtractionDisabled(boolean extractionDisabled) {
    this.extractionDisabled = extractionDisabled;
    if(worldObj != null) {
      if (!worldObj.isRemote) {
        PacketHandler.INSTANCE.sendToDimension(new PacketUpdateExtractionDisabled(this, extractionDisabled), worldObj.provider.getDimension());
      }
    }
  }

  /**
   * This is called by PacketUpdateExtractionDisabled on the client side
   * @param extractionDisabledIn if extraction is disabled
   */
  void updateExtractionDisabled(boolean extractionDisabledIn) {
    this.extractionDisabled = extractionDisabledIn;
  }

  @Override
  public void readCommon(NBTTagCompound nbtRoot) {
    super.readCommon(nbtRoot);
    faceModes = null;
    if(eventHandler != null) {
      eventHandler.checkCraftingRecipes();
    }
    updateItemFilter();
  }

  @Override
  public @Nonnull String getMachineName() {
    return ModObject.blockInventoryPanel.getUnlocalisedName();
  }

  @Override
  public @Nonnull IoMode getIoMode(@Nullable EnumFacing face) {
    return face == getIODirection() ? IoMode.NONE : IoMode.DISABLED;
  }

  @Override
  public void setIoMode(@Nullable EnumFacing faceHit, @Nullable IoMode mode) {
  }

  @Override
  public @Nonnull IoMode toggleIoModeForFace(@Nullable EnumFacing faceHit) {
    return getIoMode(faceHit);
  }

  private EnumFacing getIODirection() {
    return getFacing().getOpposite();
  }

  @Override
  public FluidTank getInputTank(FluidStack forFluidType) {
    if(forFluidType != null && fuelTank.canFill(forFluidType.getFluid())) {
      return fuelTank;
    }
    return null;
  }

  @Override
  public FluidTank[] getOutputTanks() {
    return new FluidTank[0];
  }

  @Override
  public void setTanksDirty() {
    tanksDirty = true;
  }

  @Override
  public SmartTank getNutrientTank() {
    return fuelTank;
  }

  @SuppressWarnings("null")
  @Override
  @Nonnull
  public List<ITankData> getTankDisplayData() {
    return Collections.<ITankData> singletonList(new ITankData() {

      @Override
      @Nonnull
      public EnumTankType getTankType() {
        return EnumTankType.INPUT;
      }

      @Override
      @Nullable
      public FluidStack getContent() {
        return fuelTank.getFluid();
      }

      @Override
      public int getCapacity() {
        return fuelTank.getCapacity();
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
        smartTankFluidHandler = new SmartTankFluidMachineHandler(this, fuelTank);
      }
      return (T) smartTankFluidHandler.get(facingIn);
    }
    return super.getCapability(capability, facingIn);
  }

}
