package crazypants.enderio.item;

import org.lwjgl.opengl.GL11;

import com.enderio.core.common.vecmath.Vector4i;

import cofh.api.energy.IEnergyContainerItem;
import crazypants.enderio.item.darksteel.upgrade.EnergyUpgrade;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

public class PowerBarOverlayRenderHelper {

  /*
   * These flags are not suited for the config file, but we might decide later to use different values.
   * 
   * MIMIC_VANILLA_RENDERBUG: Vanilla renders the bars 1 pixel too wide. If set to true, we'll do also.
   * 
   * HIDE_VANILLA_RENDERBUG: Overpaint vanilla's mistake instead. Don't use together with MIMIC_VANILLA_RENDERBUG.
   * 
   * SHOW_ON_FULL: Show power bar when energy storage is full. (Vanilla damage bar is hidden when full)
   * 
   * SHOW_ON_FULL_UPGRADEABLE: Same but for items that can be upgraded to have an energy storage. This will give a visual difference between unupgraded and full
   * items.
   * 
   * SHOW_ON_EMPTY: Show power bar when energy storage is empty. Should not be false if SHOW_ON_FULL is false, too.
   * 
   * SHOW_ON_EMPTY_UPGRADEABLE: Same for upgradable items.
   */
  private static final boolean MIMIC_VANILLA_RENDERBUG = false;
  private static final boolean HIDE_VANILLA_RENDERBUG = true;
  private static final boolean SHOW_ON_FULL = false;
  private static final boolean SHOW_ON_FULL_UPGRADEABLE = true;
  private static final boolean SHOW_ON_EMPTY = true;
  private static final boolean SHOW_ON_EMPTY_UPGRADEABLE = true;

  private static final double BAR_W = MIMIC_VANILLA_RENDERBUG ? 13d : 12d;

  protected Vector4i colorShadow = new Vector4i(0, 0, 0, 255);
  protected Vector4i colorBarLeft = new Vector4i(0x02, 0x03, 0x60, 255);
  protected Vector4i colorBarRight = new Vector4i(0x2D, 0xCE, 0xFA, 255);
  protected Vector4i colorBG = new Vector4i(0x00, 0x00, 0x30, 255);

  /**
   * Instance for items that always have their power buffer
   */
  public static final PowerBarOverlayRenderHelper instance = new PowerBarOverlayRenderHelper(false);
  /**
   * Instance for items that can be upgraded to get a power buffer
   */
  public static final PowerBarOverlayRenderHelper instance_upgradeable = new PowerBarOverlayRenderHelper(true);

  public static final FluidBarOverlayRenderHelper instance_fluid = new FluidBarOverlayRenderHelper();

  private final boolean isUpgradeableItem;

  protected PowerBarOverlayRenderHelper(boolean isUpgradeableItem) {
    this.isUpgradeableItem = isUpgradeableItem;
  }

  public boolean render(ItemStack stack, int xPosition, int yPosition) {
    if (hasEnergyStore(stack) && stack.getItem() instanceof IEnergyContainerItem) {
      IEnergyContainerItem energyItem = (IEnergyContainerItem) stack.getItem();
      int maxEnergy = energyItem.getMaxEnergyStored(stack);
      if (maxEnergy > 0) {
        int energy = energyItem.getEnergyStored(stack);
        if (shouldShowBar(maxEnergy, energy)) {
          double level = (double) energy / (double) maxEnergy;
          boolean up = stack.getItem().showDurabilityBar(stack);
          boolean top = stack.stackSize != 1;
          render(level, xPosition, yPosition, top ? 12 : up ? 2 : 0, true);
          return true;
        }
      }
    }
    if (HIDE_VANILLA_RENDERBUG && stack.getItem().showDurabilityBar(stack)) {
      overpaintVanillaRenderBug(xPosition, yPosition);
    }
    return false;
  }

  protected boolean shouldShowBar(int maxEnergy, int energy) {
    if (energy < 0 || energy > maxEnergy) {
      return false;
    }
    if (energy == 0) {
      return isUpgradeableItem ? SHOW_ON_EMPTY_UPGRADEABLE : SHOW_ON_EMPTY;
    }
    if (energy == maxEnergy) {
      return isUpgradeableItem ? SHOW_ON_FULL_UPGRADEABLE : SHOW_ON_FULL;
    }
    return true;
  }

  protected boolean hasEnergyStore(ItemStack stack) {
    return stack != null && (!isUpgradeableItem || EnergyUpgrade.loadFromItem(stack) != null);
  }

  public void render(double level, int xPosition, int yPosition, int offset, boolean shadow) {
    double width = level * BAR_W;
    GlStateManager.enableLighting();
    GlStateManager.disableLighting();
    GlStateManager.disableDepth();
    GlStateManager.disableTexture2D();
    GlStateManager.disableAlpha();
    GlStateManager.disableBlend();
    GlStateManager.shadeModel(GL11.GL_SMOOTH);
    Tessellator tessellator = Tessellator.getInstance();
    VertexBuffer worldrenderer = tessellator.getBuffer();
    worldrenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
    this.drawPlain(worldrenderer, xPosition + 2, yPosition + 13 - offset, 13, shadow ? 2 : 1, colorShadow);
    this.drawGrad(worldrenderer, xPosition + 2, yPosition + 13 - offset, (BAR_W + width) / 2, 1, colorBarLeft, colorBarRight);
    this.drawRight(worldrenderer, xPosition + 2 + (int) BAR_W, yPosition + 13 - offset, BAR_W - width, 1, colorBG);
    if (HIDE_VANILLA_RENDERBUG && offset == 2) {
      overpaintVanillaRenderBug(worldrenderer, xPosition, yPosition);
    }
    tessellator.draw();
    GlStateManager.shadeModel(GL11.GL_FLAT);
    GlStateManager.enableAlpha();
    GlStateManager.enableTexture2D();
    GlStateManager.enableLighting();
    GlStateManager.enableDepth();
  }

  protected void drawGrad(VertexBuffer renderer, int x, int y, double width, int height, Vector4i colorL, Vector4i colorR) {
    renderer.pos(x + 0, y + 0, 0.0D).color(colorL.x, colorL.y, colorL.z, colorL.w).endVertex();
    renderer.pos(x + 0, y + height, 0.0D).color(colorL.x, colorL.y, colorL.z, colorL.w).endVertex();
    renderer.pos(x + width, y + height, 0.0D).color(colorR.x, colorR.y, colorR.z, colorR.w).endVertex();
    renderer.pos(x + width, y + 0, 0.0D).color(colorR.x, colorR.y, colorR.z, colorR.w).endVertex();
  }

  protected void drawPlain(VertexBuffer renderer, int x, int y, double width, int height, Vector4i color) {
    renderer.pos(x + 0, y + 0, 0.0D).color(color.x, color.y, color.z, color.w).endVertex();
    renderer.pos(x + 0, y + height, 0.0D).color(color.x, color.y, color.z, color.w).endVertex();
    renderer.pos(x + width, y + height, 0.0D).color(color.x, color.y, color.z, color.w).endVertex();
    renderer.pos(x + width, y + 0, 0.0D).color(color.x, color.y, color.z, color.w).endVertex();
  }

  protected void drawRight(VertexBuffer renderer, int x, int y, double width, int height, Vector4i color) {
    renderer.pos(x - width, y + 0, 0.0D).color(color.x, color.y, color.z, color.w).endVertex();
    renderer.pos(x - width, y + height, 0.0D).color(color.x, color.y, color.z, color.w).endVertex();
    renderer.pos(x, y + height, 0.0D).color(color.x, color.y, color.z, color.w).endVertex();
    renderer.pos(x, y + 0, 0.0D).color(color.x, color.y, color.z, color.w).endVertex();
  }

  protected void overpaintVanillaRenderBug(int xPosition, int yPosition) {
    GlStateManager.disableLighting();
    GlStateManager.disableDepth();
    GlStateManager.disableTexture2D();
    GlStateManager.disableAlpha();
    GlStateManager.disableBlend();
    Tessellator tessellator = Tessellator.getInstance();
    VertexBuffer worldrenderer = tessellator.getBuffer();
    worldrenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
    overpaintVanillaRenderBug(worldrenderer, xPosition, yPosition);
    tessellator.draw();
    GlStateManager.enableAlpha();
    GlStateManager.enableTexture2D();
    GlStateManager.enableLighting();
    GlStateManager.enableDepth();
  }

  protected void overpaintVanillaRenderBug(VertexBuffer worldrenderer, int xPosition, int yPosition) {
    this.drawPlain(worldrenderer, xPosition + 2 + 12, yPosition + 13, 1, 1, colorShadow);
  }

  public static class FluidBarOverlayRenderHelper extends PowerBarOverlayRenderHelper {

    protected FluidBarOverlayRenderHelper() {
      super(false);
      colorBarLeft = new Vector4i(0x76, 0x84, 0x52, 255);
      colorBarRight = new Vector4i(0xac, 0xdb, 0x39, 255);
      colorBG = new Vector4i(0x00, 0x30, 0x00, 255);
    }

    @Override
    public boolean render(ItemStack stack, int xPosition, int yPosition) {
      return render(stack, xPosition, yPosition, 0);
    }

    public boolean render(ItemStack stack, int xPosition, int yPosition, int barOffset) {
      if (stack.getItem() instanceof IFluidContainerItem) {
        IFluidContainerItem fluidItem = (IFluidContainerItem) stack.getItem();
        int maxFluid = fluidItem.getCapacity(stack);
        if (maxFluid > 0) {
          FluidStack fluidStack = fluidItem.getFluid(stack);
          int fluid = fluidStack == null ? 0 : fluidStack.amount;
          if (shouldShowBar(maxFluid, fluid)) {
            double level = (double) fluid / (double) maxFluid;
            boolean up = stack.getItem().showDurabilityBar(stack);
            boolean top = stack.stackSize != 1;
            render(level, xPosition, yPosition, top ? 12 - barOffset : up ? 2 + barOffset : barOffset, (barOffset & 1) == 0);
            return true;
          }
        }
      }
      return false;
    }

  }

}
