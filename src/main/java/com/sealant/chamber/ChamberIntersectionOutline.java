package com.sealant.chamber;

import com.sealant.Sealant;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client-only visual prototype: highlight OTHER structures whose bounding OBB overlaps a chamber. */
public final class ChamberIntersectionOutline {
    private static final Set<UUID> drawn = new HashSet<>();
    private static ChamberIntersection intersection; // Lazy: initialize Sable geometry only once a world exists.

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || !(com.sealant.config.SealantClientConfig.value(com.sealant.config.SealantClientConfig.OUTLINES,false) || mc.getEntityRenderDispatcher().shouldRenderHitBoxes()
                || mc.player.getMainHandItem().is(Sealant.SEALANT.get())
                || mc.player.getOffhandItem().is(Sealant.SEALANT.get()))) {
            clear();
            return;
        }
        if (intersection == null) intersection = new ChamberIntersection();
        Map<UUID, AABB> highlighted = new HashMap<>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ChamberEntity chamber) || !chamber.isAlive()) continue;
            var owner = Sable.HELPER.getContainingClient(chamber);
            if (owner == null || owner.isRemoved()) continue;
            AABB chamberLocal = chamber.getBoundingBox();
            // Query in WORLD coordinates, then test the two actual oriented boxes.
            // getContaining(worldPoint) is a plot lookup and cannot find spatial occupants.
            var search = ChamberIntersection.worldSearchBounds(chamberLocal, owner.logicalPose());
            for (var candidate : Sable.HELPER.getAllIntersecting(mc.level, search)) {
                if (!(candidate instanceof ClientSubLevel other) || other.isRemoved()
                        || other.getUniqueId().equals(owner.getUniqueId())
                        || highlighted.containsKey(other.getUniqueId())) continue;
                var bounds = other.getPlot().getBoundingBox();
                if (bounds == null) continue;
                // Plot integer maxima are inclusive BLOCK indices; AABB maxima are outer faces.
                AABB otherLocal = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                        (double)bounds.maxX() + 1, (double)bounds.maxY() + 1, (double)bounds.maxZ() + 1);
                if (intersection.intersects(chamberLocal, owner.logicalPose(), otherLocal, other.logicalPose()))
                    highlighted.put(other.getUniqueId(), otherLocal);
            }
        }
        // A structure can intersect multiple chambers. Draw once and keep it until ALL overlaps end.
        for (UUID old : drawn) if (!highlighted.containsKey(old)) Outliner.getInstance().remove(key(old));
        highlighted.forEach((id, local) -> {
            // Preserve the VERIFIED plot-space Outliner path. Sable supplies renderPose itself.
            Outliner.getInstance().showAABB(key(id), local).colored(ChamberStyle.COLOR)
                    .clearTextures().disableLineNormals().lineWidth(1 / 32f);
        });
        drawn.clear();
        drawn.addAll(highlighted.keySet());
    }

    public static void clear() {
        for (UUID id : drawn) Outliner.getInstance().remove(key(id));
        drawn.clear();
    }
    private static String key(UUID id) { return "sealant:chamber_intersection:" + id; }
    private ChamberIntersectionOutline() {}
}
