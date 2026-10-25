package com.skps2010;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/** Same config file and format as the Fabric version: config/crave_and_feast.json */
public final class FDConfigs {
    public static FDConfig CFG = new FDConfig();

    private FDConfigs() {
    }

    public static void load() {
        Path p = FMLPaths.CONFIGDIR.get().resolve("crave_and_feast.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.notExists(p)) {
                CFG = new FDConfig();
            } else {
                CFG = gson.fromJson(Files.readString(p), FDConfig.class);
                if (CFG == null) CFG = new FDConfig();
                if (CFG.rules == null || CFG.rules.isEmpty()) CFG.rules = new FDConfig().rules;
                if (CFG.cravingBlacklist == null) CFG.cravingBlacklist = new FDConfig().cravingBlacklist;
                if (CFG.cravingWhitelist == null) CFG.cravingWhitelist = new FDConfig().cravingWhitelist;
            }
            // Write back after every load so newly added fields appear in the file
            Files.createDirectories(p.getParent());
            Files.writeString(p, gson.toJson(CFG));
        } catch (Exception e) {
            CraveAndFeast.LOGGER.error("[CraveAndFeast] Failed to load config, using defaults", e);
            CFG = new FDConfig();
        }
    }

    /** Persist the current config to disk (e.g. after editing the HUD layout in-game). */
    public static void save() {
        Path p = FMLPaths.CONFIGDIR.get().resolve("crave_and_feast.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, gson.toJson(CFG));
        } catch (Exception e) {
            CraveAndFeast.LOGGER.error("[CraveAndFeast] Failed to save config", e);
        }
    }
}
