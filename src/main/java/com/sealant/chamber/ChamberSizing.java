// Copied from Simulated 1.3.2 honey glue (MIT); see THIRD_PARTY_NOTICES.md.
package com.sealant.chamber;

import dev.simulated_team.simulated.service.SimConfigService;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.phys.AABB;

public class ChamberSizing {
   public static Pair<Boolean, String> checkBounds(AABB bb) {
      if (checkBBMin(bb)) {
         return Pair.of(false, "Chamber is too small");
      } else {
         return checkBBMax(bb) ? Pair.of(false, "Chamber is too large") : Pair.of(true, "");
      }
   }

   public static boolean checkBBMin(AABB bb) {
      return bb.getXsize() < 1.0 || bb.getYsize() < 1.0 || bb.getZsize() < 1.0;
   }

   public static boolean checkBBMax(AABB bb) {
      int max = (Integer)SimConfigService.INSTANCE.server().assembly.honeyGlueRange.get();
      return bb.getXsize() > (double)max || bb.getYsize() > (double)max || bb.getZsize() > (double)max;
   }
}
