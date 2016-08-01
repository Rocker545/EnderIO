package crazypants.enderio.config;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Syncs some configs that are only used clientside, but must use the serverside
 * value for balance purposes.
 */
public class PacketConfigSync implements IMessage, IMessageHandler<PacketConfigSync, IMessage> {

  @Override
  public void toBytes(ByteBuf buf) {
    buf.writeBoolean(Config.travelAnchorEnabled);
    buf.writeInt(Config.travelAnchorMaximumDistance);
    buf.writeBoolean(Config.travelStaffEnabled);
    buf.writeBoolean(Config.travelStaffBlinkEnabled);
    buf.writeBoolean(Config.travelStaffBlinkThroughSolidBlocksEnabled);
    buf.writeBoolean(Config.travelStaffBlinkThroughClearBlocksEnabled);
    buf.writeInt(Config.travelStaffBlinkPauseTicks);
    buf.writeInt(Config.travelStaffMaximumDistance);
    buf.writeInt(Config.travelStaffMaxBlinkDistance);
    buf.writeFloat(Config.travelStaffPowerPerBlockRF);
    buf.writeBoolean(Config.telepadLockCoords);
    buf.writeBoolean(Config.telepadLockDimension);
    buf.writeBoolean(Config.killerMendingEnabled);
    buf.writeInt(Config.darkSteelAnvilMaxLevel);   
  }

  @Override
  public void fromBytes(ByteBuf data) {
    Config.travelAnchorEnabled = data.readBoolean();
    Config.travelAnchorMaximumDistance = data.readInt();
    Config.travelStaffEnabled = data.readBoolean();
    Config.travelStaffBlinkEnabled = data.readBoolean();
    Config.travelStaffBlinkThroughSolidBlocksEnabled = data.readBoolean();
    Config.travelStaffBlinkThroughClearBlocksEnabled = data.readBoolean();
    Config.travelStaffBlinkPauseTicks = data.readInt();
    Config.travelStaffMaximumDistance = data.readInt();
    Config.travelStaffMaxBlinkDistance = data.readInt();
    Config.travelStaffPowerPerBlockRF = data.readFloat();
    Config.telepadLockCoords = data.readBoolean();
    Config.telepadLockDimension = data.readBoolean();
    Config.killerMendingEnabled = data.readBoolean();
    Config.darkSteelAnvilMaxLevel = data.readInt();
  }

  @Override
  public IMessage onMessage(PacketConfigSync message, MessageContext ctx) {
    return null;
  }
}
