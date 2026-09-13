package org.valkyrienskies.eureka.mixin;

import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * C2ME mid-tick chunk tasks + VS shipyard entity sections can re-enter
 * FastUtil's AVL set during Entity#setPos. That NPEs and crashes the server
 * (MTS BuilderEntityRenderForwarder). Swallow the corrupt add; the section
 * map still holds the entity.
 */
@Mixin(EntitySectionStorage.class)
public abstract class MixinEntitySectionStorageSafe {

    @Redirect(
            method = {"addSection", "m_156901_"},
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/longs/LongSortedSet;add(J)Z"
            )
    )
    private boolean eureka$safeSectionIdAdd(final LongSortedSet set, final long packedChunk) {
        try {
            return set.add(packedChunk);
        } catch (final NullPointerException | ClassCastException | IllegalArgumentException e) {
            return false;
        }
    }
}
