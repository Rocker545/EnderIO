package crazypants.enderio.machine.invpanel;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.enderio.core.common.network.NetworkUtil;

import crazypants.enderio.EnderIO;
import crazypants.enderio.machine.invpanel.client.InventoryDatabaseClient;
import crazypants.enderio.machine.invpanel.server.InventoryDatabaseServer;
import crazypants.enderio.machine.invpanel.server.ItemEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketItemInfo implements IMessage, IMessageHandler<PacketItemInfo, IMessage> {

  private int windowId;
  private int generation;
  private byte[] compressed;

  public PacketItemInfo() {
  }

  public PacketItemInfo(int windowId, InventoryDatabaseServer db, List<ItemEntry> items) {
    this.windowId = windowId;
    this.generation = db.generation;
    try {
      compressed = db.compressItemInfo(items);
    } catch (IOException ex) {
      Logger.getLogger(PacketItemInfo.class.getName()).log(Level.SEVERE, "Exception while compressing items", ex);
      compressed = new byte[0];
    }
  }

  @Override
  public void fromBytes(ByteBuf buf) {
    windowId = buf.readInt();
    generation = buf.readInt();
    compressed = NetworkUtil.readByteArray(buf);
  }

  @Override
  public void toBytes(ByteBuf buf) {
    buf.writeInt(windowId);
    buf.writeInt(generation);
    NetworkUtil.writeByteArray(buf, compressed);
  }

  @Override
  public IMessage onMessage(PacketItemInfo message, MessageContext ctx) {
    EntityPlayer player = EnderIO.proxy.getClientPlayer();
    if (player.openContainer.windowId == message.windowId && player.openContainer instanceof InventoryPanelContainer) {
      InventoryPanelContainer ipc = (InventoryPanelContainer) player.openContainer;
      TileInventoryPanel teInvPanel = ipc.getInv();
      InventoryDatabaseClient db = teInvPanel.getDatabaseClient(message.generation);
      try {
        db.readCompressedItems(message.compressed);
      } catch (IOException ex) {
        Logger.getLogger(PacketItemInfo.class.getName()).log(Level.SEVERE, "Exception while reading item info", ex);
      }
    }
    return null;
  }
}
