package crazypants.enderio.api.teleport;

import crazypants.enderio.config.Config;
import crazypants.enderio.sound.IModSound;
import crazypants.enderio.sound.SoundRegistry;

public enum TravelSource {

  BLOCK(SoundRegistry.TRAVEL_SOURCE_BLOCK) {
    @Override
    public int getMaxDistanceTravelled() {
      return Config.travelAnchorMaximumDistance;
    }
  },
  STAFF(SoundRegistry.TRAVEL_SOURCE_ITEM) {
    @Override
    public int getMaxDistanceTravelled() {
      return Config.travelStaffMaximumDistance;
    }

    @Override
    public float getPowerCostPerBlockTraveledRF() {
      return Config.travelStaffPowerPerBlockRF;
    }
  },
  STAFF_BLINK(SoundRegistry.TRAVEL_SOURCE_ITEM) {
    @Override
    public int getMaxDistanceTravelled() {
      return Config.travelStaffMaxBlinkDistance;
    }

    @Override
    public float getPowerCostPerBlockTraveledRF() {
      return Config.travelStaffPowerPerBlockRF;
    }
  },
  TELEPAD(SoundRegistry.TELEPAD);

  public static int getMaxDistance() {
    return STAFF.getMaxDistanceTravelledSq();
  }

  public static int getMaxDistanceSq() {
    return STAFF.getMaxDistanceTravelledSq();
  }

  public final IModSound sound;

  private TravelSource(IModSound sound) {
    this.sound = sound;
  }

  public boolean getConserveMomentum() {
    return this == STAFF_BLINK;
  }

  public int getMaxDistanceTravelled() {
    return 0;
  }

  public int getMaxDistanceTravelledSq() {
    return getMaxDistanceTravelled() * getMaxDistanceTravelled();
  }

  public float getPowerCostPerBlockTraveledRF() {
    return 0;
  }

}