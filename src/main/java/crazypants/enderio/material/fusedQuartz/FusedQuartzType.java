package crazypants.enderio.material.fusedQuartz;

import java.util.Locale;

import javax.annotation.Nonnull;

import crazypants.enderio.config.Config;
import crazypants.util.NullHelper;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.util.IStringSerializable;

public enum FusedQuartzType implements IStringSerializable {

  FUSED_QUARTZ("fusedQuartz", BaseMaterial.QUARTZ, Upgrade.NONE),
  FUSED_GLASS("fusedGlass", BaseMaterial.GLASS, Upgrade.NONE),
  ENLIGHTENED_FUSED_QUARTZ("enlightenedFusedQuartz", BaseMaterial.QUARTZ, Upgrade.ENLIGHTENED),
  ENLIGHTENED_FUSED_GLASS("enlightenedFusedGlass", BaseMaterial.GLASS, Upgrade.ENLIGHTENED),
  DARK_FUSED_QUARTZ("darkFusedQuartz", BaseMaterial.QUARTZ, Upgrade.DARKENED),
  DARK_FUSED_GLASS("darkFusedGlass", BaseMaterial.GLASS, Upgrade.DARKENED);

  private static enum BaseMaterial {
    QUARTZ,
    GLASS
  }

  private static enum Upgrade {
    NONE,
    ENLIGHTENED,
    DARKENED
  }

  public static final @Nonnull PropertyEnum<FusedQuartzType> KIND = NullHelper.notnullM(PropertyEnum.<FusedQuartzType> create("kind", FusedQuartzType.class),
      "PropertyEnum.create()");

  private final @Nonnull String unlocalisedName;
  private final @Nonnull BaseMaterial baseMaterial;
  private final @Nonnull Upgrade upgrade;
  private Block block;

  private FusedQuartzType(@Nonnull String unlocalisedName, @Nonnull BaseMaterial baseMaterial, @Nonnull Upgrade upgrade) {
    this.unlocalisedName = unlocalisedName;
    this.baseMaterial = baseMaterial;
    this.upgrade = upgrade;
  }

  public boolean connectTo(FusedQuartzType other) {
    return other != null && ((Config.clearGlassConnectToFusedQuartz && Config.glassConnectToTheirVariants)
        || (Config.clearGlassConnectToFusedQuartz && this.upgrade == other.upgrade)
        || (Config.glassConnectToTheirVariants && this.baseMaterial == other.baseMaterial));
  }

  @Override
  public @Nonnull String getName() {
    return NullHelper.notnullJ(name().toLowerCase(Locale.ENGLISH), "String.toLowerCase()");
  }

  public static @Nonnull FusedQuartzType getTypeFromMeta(int meta) {
    return NullHelper.notnullJ(values()[meta >= 0 && meta < values().length ? meta : 0], "Enum.values()");
  }

  public static int getMetaFromType(@Nonnull FusedQuartzType fusedQuartzType) {
    return fusedQuartzType.ordinal();
  }

  public boolean isEnlightened() {
    return upgrade == Upgrade.ENLIGHTENED;
  }

  public boolean isBlastResistant() {
    return baseMaterial == BaseMaterial.QUARTZ;
  }

  public int getLightOpacity() {
    return upgrade == Upgrade.DARKENED ? 255 : 0;
  }

  public @Nonnull String getUnlocalisedName() {
    return unlocalisedName;
  }

  public Block getBlock() {
    return block;
  }

  public void setBlock(Block block) {
    this.block = block;
  }
}