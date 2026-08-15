package com.mrfuzzihead.unidict;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.mrfuzzihead.unidict.config.ForgeConfigIO;
import com.mrfuzzihead.unidict.pure.config.ConfigData;
import com.mrfuzzihead.unidict.pure.config.ConfigPresets;

/**
 * Runtime configuration holder (M2 commit 2). Replaces the boilerplate starter config with a load of
 * the forge {@code .cfg} into the pure {@link ConfigData} value model via {@link ForgeConfigIO}.
 *
 * <p>
 * Loading is a deliberate improvement over upstream: keys absent from the file fall back to the
 * standard preset defaults, explicit keys win (last-write-wins), unrecognised/removed keys are
 * ignored (never fatal, never wiped), and the file is saved only when keys changed — not deleted on
 * a version bump (docs/PLAN.md §M2).
 *
 * <p>
 * Integrations (M6/M7) read the per-mod toggles through the accessors below; the transparency
 * report (BB-1) and {@code SelectionRules} (M4) read via {@link #get()}.
 */
public class Config {

    private static ConfigData data = ConfigPresets.standard();

    private Config() {}

    /** Loads the forge configuration file into the runtime {@link ConfigData} (additive save). */
    public static void load(final File configFile) {
        final Configuration configuration = new Configuration(configFile);
        configuration.load();
        try {
            // Resolve the user-selected preset first (defaults to standard) so the base defaults used
            // for both default-registration and parsing follow the chosen preset (BB-2). Explicit
            // keys present in the file still override (last-write-wins).
            final ConfigData base = ConfigPresets.byName(ForgeConfigIO.readPreset(configuration));
            // Register every known key so absent keys are written (first run yields a full .cfg);
            // existing values are preserved by the forge getters.
            ForgeConfigIO.registerDefaults(configuration, base);
            data = ForgeConfigIO.load(configuration, base);
        } finally {
            ForgeConfigIO.saveIfChanged(configuration);
        }
    }

    public static ConfigData get() {
        return data;
    }

    // ---- convenience accessors consulted by integrations / selection ---------------------------

    public static boolean keepOneEntry() {
        return data.keepOneEntry;
    }

    public static boolean inputReplacement() {
        return data.inputReplacement;
    }

    public static boolean unifyDrops() {
        return data.unifyDrops;
    }

    public static boolean integrationModule() {
        return data.integrationModuleEnabled;
    }

    public static boolean furnace() {
        return data.furnaceIntegration;
    }

    public static boolean ae2() {
        return data.ae2Integration;
    }

    public static boolean ic2() {
        return data.ic2Integration;
    }

    public static boolean ie() {
        return data.ieIntegration;
    }

    public static boolean chest() {
        return data.chestIntegration;
    }

    public static boolean enderIO() {
        return data.enderIOIntegration;
    }

    public static boolean forestry() {
        return data.forestryIntegration;
    }

    public static boolean crafting() {
        return data.craftingIntegration;
    }

    public static boolean railcraft() {
        return data.railcraftIntegration;
    }

    public static boolean thermalExpansion() {
        return data.thermalExpansionIntegration;
    }

    public static boolean galacticraft() {
        return data.galacticraftIntegration;
    }
}
