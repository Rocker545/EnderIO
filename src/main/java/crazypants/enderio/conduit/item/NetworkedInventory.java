package crazypants.enderio.conduit.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.enderio.core.common.util.BlockCoord;
import com.enderio.core.common.util.RoundRobinIterator;

import crazypants.enderio.Log;
import crazypants.enderio.capability.ItemTools;
import crazypants.enderio.conduit.ConnectionMode;
import crazypants.enderio.conduit.item.filter.IItemFilter;
import crazypants.enderio.config.Config;
import crazypants.enderio.machine.invpanel.TileInventoryPanel;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

public class NetworkedInventory {

  IItemConduit con;
  EnumFacing conDir;
  BlockPos location;
  EnumFacing inventorySide;

  List<Target> sendPriority = new ArrayList<Target>();
  RoundRobinIterator<Target> rrIter = new RoundRobinIterator<Target>(sendPriority);

  private int extractFromSlot = -1;

  int tickDeficit;

  boolean inventoryPanel = false;

  World world;
  ItemConduitNetwork network;
  String invName;

  NetworkedInventory(ItemConduitNetwork network, IItemConduit con, EnumFacing conDir, IItemHandler inv, BlockCoord location) {
    this.network = network;
    inventorySide = conDir.getOpposite();
    this.con = con;
    this.conDir = conDir;
    this.location = location.getBlockPos();
    world = con.getBundle().getBundleWorldObj();

    IBlockState bs = world.getBlockState(location.getBlockPos());
    invName = bs.getBlock().getLocalizedName();
    
    TileEntity te = world.getTileEntity(location.getBlockPos());       
    if(te instanceof TileInventoryPanel) {
      inventoryPanel = true;
    }
  }

  public boolean hasTarget(IItemConduit conduit, EnumFacing dir) {
    for (Target t : sendPriority) {
      if(t.inv.con == conduit && t.inv.conDir == dir) {
        return true;
      }
    }
    return false;
  }

  boolean canExtract() {
    ConnectionMode mode = con.getConnectionMode(conDir);
    return mode == ConnectionMode.INPUT || mode == ConnectionMode.IN_OUT;
  }

  boolean canInsert() {
    if(inventoryPanel) {
      return false;
    }
    ConnectionMode mode = con.getConnectionMode(conDir);
    return mode == ConnectionMode.OUTPUT || mode == ConnectionMode.IN_OUT;
  }

  boolean isInventoryPanel() {
    return inventoryPanel;
  }

  boolean isSticky() {
    return con.getOutputFilter(conDir) != null && con.getOutputFilter(conDir).isValid() && con.getOutputFilter(conDir).isSticky();
  }

  int getPriority() {
    return con.getOutputPriority(conDir);
  }

  public void onTick() {
    if(tickDeficit > 0 || !canExtract() || !con.isExtractionRedstoneConditionMet(conDir)) {
      //do nothing     
    } else {
      transferItems();
    }

    tickDeficit--;
    if(tickDeficit < -1) {
      //Sleep for a second before checking again.
      tickDeficit = 20;
    }
  }

  private int nextSlot(int numSlots) {
    ++extractFromSlot;
    if(extractFromSlot >= numSlots || extractFromSlot < 0) {
      extractFromSlot = 0;
    }
    return extractFromSlot;
  }

  private void setNextStartingSlot(int slot) {
    extractFromSlot = slot;
    extractFromSlot--;
  }

  private boolean transferItems() {
    IItemHandler inventory = getInventory();
    if (inventory == null) {
      return false;
    }
    int numSlots = inventory.getSlots();
    if(numSlots < 1) {
      return false;
    }
    
    ItemStack extractItem = null;
    int maxExtracted = con.getMaximumExtracted(conDir);

    int slot = -1;
    int slotChecksPerTick = Math.min(numSlots, ItemConduitNetwork.MAX_SLOT_CHECK_PER_TICK);
    for (int i = 0; i < slotChecksPerTick; i++) {
      slot = nextSlot(numSlots);      
      ItemStack item = inventory.getStackInSlot(slot);
      if(canExtractItem(item)) {
        extractItem = item.copy();        
        if (inventory.extractItem(slot, extractItem.stackSize, true) != null) {
          if(doTransfer(extractItem, slot, maxExtracted)) {
            setNextStartingSlot(slot);
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean canExtractItem(ItemStack itemStack) {
    if(itemStack == null) {
      return false;
    }
    IItemFilter filter = con.getInputFilter(conDir);
    if(filter == null) {
      return true;
    }
    return filter.doesItemPassFilter(this, itemStack);
  }

  private boolean doTransfer(ItemStack extractedItem, int slot, int maxExtract) {
    if(extractedItem == null || extractedItem.getItem() == null) {
      return false;
    }
    ItemStack toExtract = extractedItem.copy();
    toExtract.stackSize = Math.min(maxExtract, toExtract.stackSize);
    int numInserted = insertIntoTargets(toExtract);
    if(numInserted <= 0) {
      return false;
    }
    itemExtracted(slot, numInserted);
    return true;

  }

  // TODO: what should this do? Need to handle error cases, I think.
  public void itemExtracted(int slot, int numInserted) {
    IItemHandler inventory = getInventory();
    if (inventory != null) {
      ItemStack curStack = inventory.getStackInSlot(slot);
      if (curStack != null) {
        ItemStack extracted = inventory.extractItem(slot, numInserted, false);
        if (extracted == null || extracted.stackSize != numInserted) {
          Log.warn("NetworkedInventory.itemExtracted: Inserted " + numInserted + " " + curStack.getDisplayName() + " but only removed "
              + (extracted == null ? "null" : extracted.stackSize));
        }
      }
    }
    con.itemsExtracted(numInserted, slot);
    tickDeficit = Math.round(numInserted * con.getTickTimePerItem(conDir));
  }

  public void onItemExtracted(int slot, int numInserted) {
    con.itemsExtracted(numInserted, slot);
    tickDeficit = Math.round(numInserted * con.getTickTimePerItem(conDir));
  }

  int insertIntoTargets(ItemStack toExtract) {
    if(toExtract == null) {
      return 0;
    }

    int totalToInsert = toExtract.stackSize;
    int leftToInsert = totalToInsert;
    boolean matchedStickyInput = false;

    Iterable<Target> targets = getTargetIterator();

    //for (Target target : sendPriority) {
    for (Target target : targets) {
      if(target.stickyInput && !matchedStickyInput) {
        IItemFilter of = target.inv.con.getOutputFilter(target.inv.conDir);
        matchedStickyInput = of != null && of.isValid() && of.doesItemPassFilter(this, toExtract);
      }
      if(target.stickyInput || !matchedStickyInput) {        
        int inserted = target.inv.insertItem(toExtract);
        if(inserted > 0) {
          toExtract.stackSize -= inserted;
          leftToInsert -= inserted;
        }
        if(leftToInsert <= 0) {
          return totalToInsert;
        }
      }
    }
    return totalToInsert - leftToInsert;
  }

  private Iterable<Target> getTargetIterator() {
    if(con.isRoundRobinEnabled(conDir)) {
      return rrIter;
    }
    return sendPriority;
  }

  private int insertItem(ItemStack item) {
    if(!canInsert() || item == null) {
      return 0;
    }
    IItemFilter filter = con.getOutputFilter(conDir);
    if(filter != null) {
      if(!filter.doesItemPassFilter(this, item)) {
        return 0;
      }
    }
    return ItemTools.doInsertItem(getInventory(), item);
  }

  void updateInsertOrder() {
    sendPriority.clear();
    if(!canExtract()) {
      return;
    }
    List<Target> result = new ArrayList<NetworkedInventory.Target>();

    for (NetworkedInventory other : network.inventories) {
      if((con.isSelfFeedEnabled(conDir) || (other != this))
          && other.canInsert()
          && con.getInputColor(conDir) == other.con.getOutputColor(other.conDir)) {

        if(Config.itemConduitUsePhyscialDistance) {
          sendPriority.add(new Target(other, distanceTo(other), other.isSticky(), other.getPriority()));
        } else {
          result.add(new Target(other, 9999999, other.isSticky(), other.getPriority()));
        }
      }
    }

    if(Config.itemConduitUsePhyscialDistance) {
      Collections.sort(sendPriority);
    } else {
      if(!result.isEmpty()) {
        Map<BlockCoord, Integer> visited = new HashMap<BlockCoord, Integer>();
        List<BlockCoord> steps = new ArrayList<BlockCoord>();
        steps.add(con.getLocation());
        calculateDistances(result, visited, steps, 0);

        sendPriority.addAll(result);

        Collections.sort(sendPriority);
      }
    }

  }

  private void calculateDistances(List<Target> targets, Map<BlockCoord, Integer> visited, List<BlockCoord> steps, int distance) {
    if(steps == null || steps.isEmpty()) {
      return;
    }

    ArrayList<BlockCoord> nextSteps = new ArrayList<BlockCoord>();
    for (BlockCoord bc : steps) {
      IItemConduit con1 = network.conMap.get(bc);
      if (con1 != null) {
        for (EnumFacing dir : con1.getExternalConnections()) {
          Target target = getTarget(targets, con1, dir);
          if(target != null && target.distance > distance) {
            target.distance = distance;
          }
        }

        if(!visited.containsKey(bc)) {
          visited.put(bc, distance);
        } else {
          int prevDist = visited.get(bc);
          if(prevDist <= distance) {
            continue;
          }
          visited.put(bc, distance);
        }

        for (EnumFacing dir : con1.getConduitConnections()) {
          nextSteps.add(bc.getLocation(dir));
        }
      }
    }
    calculateDistances(targets, visited, nextSteps, distance + 1);
  }

  private Target getTarget(List<Target> targets, IItemConduit con1, EnumFacing dir) {
    if (targets == null || con1 == null || con1.getLocation() == null) {
      return null;
    }
    for (Target target : targets) {
      BlockCoord targetConLoc = null;
      if(target != null && target.inv != null && target.inv.con != null) {
        targetConLoc = target.inv.con.getLocation();
        if (targetConLoc != null && target.inv.conDir == dir && targetConLoc.equals(con1.getLocation())) {
          return target;
        }
      }
    }
    return null;
  }

  private int distanceTo(NetworkedInventory other) {
    return con.getLocation().getDistSq(other.con.getLocation());
  }

  public @Nullable IItemHandler getInventory() {
    return ItemTools.getExternalInventory(world, location, inventorySide);
  }

  public EnumFacing getInventorySide() {
    return inventorySide;
  }

  public void setInventorySide(EnumFacing inventorySide) {
    this.inventorySide = inventorySide;
  }

  static class Target implements Comparable<Target> {
    NetworkedInventory inv;
    int distance;
    boolean stickyInput;
    int priority;

    Target(NetworkedInventory inv, int distance, boolean stickyInput, int priority) {
      this.inv = inv;
      this.distance = distance;
      this.stickyInput = stickyInput;
      this.priority = priority;
    }

    @Override
    public int compareTo(Target o) {
      if(stickyInput && !o.stickyInput) {
        return -1;
      }
      if(!stickyInput && o.stickyInput) {
        return 1;
      }
      if(priority != o.priority) {
        return ItemConduitNetwork.compare(o.priority, priority);
      }
      return ItemConduitNetwork.compare(distance, o.distance);
    }

  }

  public String getLocalizedInventoryName() {
    return invName;
  }

  public boolean isAt(BlockPos pos) {
    return location != null && pos != null && location.equals(pos);
  }
}
