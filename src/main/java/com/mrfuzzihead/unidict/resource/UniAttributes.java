package com.mrfuzzihead.unidict.resource;

import javax.annotation.Nonnull;

/**
 * A pure (Minecraft-free) value pair tying a {@link Resource} to one of its child entries.
 *
 * <p>
 * Ported from {@code wanion.unidict.resource.UniAttributes} (WanionCane, MPL-2.0), genericized
 * over {@link IResourceContainer} so it can live outside the Minecraft-bound
 * {@code UniResourceContainer} and remain T1-testable. Used by the resource handler (M4) to
 * remember, for an individual item hash, which resource/entry it belongs to.
 *
 * @param <E> the child-entry type (M4 uses {@code UniResourceContainer}).
 */
public final class UniAttributes<E extends IResourceContainer> {

    public final Resource<E> resource;
    public final E uniResourceContainer;

    public UniAttributes(@Nonnull final Resource<E> resource, @Nonnull final E uniResourceContainer) {
        this.resource = resource;
        this.uniResourceContainer = uniResourceContainer;
    }
}
