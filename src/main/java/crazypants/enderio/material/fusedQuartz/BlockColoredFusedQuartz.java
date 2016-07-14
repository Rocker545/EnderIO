package crazypants.enderio.material.fusedQuartz;

import java.util.List;

import crazypants.enderio.EnderIO;
import crazypants.enderio.EnderIOTab;
import crazypants.enderio.ModObject;
import crazypants.enderio.machine.painter.blocks.BlockItemPaintedBlock;
import crazypants.enderio.paint.PainterUtil2.IWithPaintName;
import crazypants.enderio.render.EnumMergingBlockRenderMode;
import crazypants.enderio.render.ITintedItem;
import net.minecraft.block.BlockColored;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockColoredFusedQuartz extends BlockFusedQuartz {

  private final FusedQuartzType glasstype;

  public static BlockColoredFusedQuartz create() {
    for (FusedQuartzType glasstype : FusedQuartzType.values()) {
      final BlockColoredFusedQuartz blockColoredFusedQuartz = new BlockColoredFusedQuartz(glasstype);
      blockColoredFusedQuartz.init();
      glasstype.setBlock(blockColoredFusedQuartz);
    }
    return null;
  }

  private BlockColoredFusedQuartz(FusedQuartzType glasstype) {
    super(ModObject.blockFusedQuartz.getUnlocalisedName() + "_" + glasstype.getUnlocalisedName());
    this.glasstype = glasstype;
    setDefaultState(this.blockState.getBaseState().withProperty(EnumMergingBlockRenderMode.RENDER, EnumMergingBlockRenderMode.AUTO)
        .withProperty(FusedQuartzType.KIND, glasstype).withProperty(BlockColored.COLOR, DEFAULT_COLOR));
  }

  @Override
  public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
    return state.withProperty(EnumMergingBlockRenderMode.RENDER, EnumMergingBlockRenderMode.AUTO).withProperty(FusedQuartzType.KIND, glasstype);
  }

  @Override
  public IBlockState getStateFromMeta(int meta) {
    return getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.byMetadata(meta));
  }

  @Override
  public int getMetaFromState(IBlockState state) {
    return state.getValue(BlockColored.COLOR).getMetadata();
  }

  @Override
  @SideOnly(Side.CLIENT)
  public void getSubBlocks(Item par1, CreativeTabs par2CreativeTabs, List<ItemStack> par3List) {
    if (par2CreativeTabs != null) {
      for (EnumDyeColor enumdyecolor : EnumDyeColor.values()) {
        if (enumdyecolor != EnumDyeColor.WHITE || par2CreativeTabs == EnderIOTab.tabNoTab) {
          par3List.add(new ItemStack(par1, 1, enumdyecolor.getMetadata()));
        }
      }
    }
  }

  @Override
  public String getUnlocalizedName(int meta) {
    return "enderio.blockFusedQuartz." + glasstype.getUnlocalisedName();
  }

  @Override
  protected ItemBlock createItemBlock() {
    return new BlockItemFusedQuartzColored(this, getName());
  }

  public static class BlockItemFusedQuartzColored extends BlockItemPaintedBlock implements ITintedItem, IWithPaintName {

    public BlockItemFusedQuartzColored(BlockColoredFusedQuartz block, String name) {
      super(block, name);
    }

    @Override
    public int getItemTint(ItemStack stack, int tintIndex) {
      return stack == null ? -1 : EnumDyeColor.byMetadata(stack.getMetadata()).getMapColor().colorValue;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack par1ItemStack, EntityPlayer par2EntityPlayer, List<String> par3List, boolean par4) {
      super.addInformation(par1ItemStack, par2EntityPlayer, par3List, par4);
      FusedQuartzType type = ((BlockColoredFusedQuartz) block).glasstype;
      if (type.isBlastResistant()) {
        par3List.add(EnderIO.lang.localize("blastResistant"));
      }
      if (type.isEnlightened()) {
        par3List.add(EnderIO.lang.localize("lightEmitter"));
      }
      if (type.getLightOpacity() > 0) {
        par3List.add(EnderIO.lang.localize("lightBlocker"));
      }
    }

    @Override
    public String getPaintName(ItemStack stack) {
      return EnderIO.lang.localize("color." + EnumDyeColor.byMetadata(stack.getMetadata()).getUnlocalizedName());
    }

  }

  // @Override
  // @Nullable
  // public Item getItemDropped(IBlockState state, Random rand, int fortune) {
  // return Item.getItemFromBlock(this);
  // }
  //
  // @Override
  // public int damageDropped(IBlockState state) {
  // return getMetaFromState(state);
  // }

}
