// Copied from Simulated 1.3.2 honey glue (MIT); see THIRD_PARTY_NOTICES.md.
package com.sealant.chamber;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;

public class ChamberRenderer extends EntityRenderer<ChamberEntity> {
   public ChamberRenderer(Context context) {
      super(context);
   }

   public ResourceLocation getTextureLocation(ChamberEntity entity) {
      return com.sealant.Sealant.path("textures/item/sealant.png");
   }

   public boolean shouldRender(ChamberEntity entity, Frustum frustum, double x, double y, double z) {
      return false;
   }
}
