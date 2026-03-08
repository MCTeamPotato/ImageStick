package me.kall.imageStick;

import org.bukkit.Rotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class SlideGroupManager {

    private final ImageStickPlugin plugin;
    private final File dataFile;

    private final Map<UUID, SlideGroup> frameIndex = new HashMap<>();

    private final List<SlideGroup> groups = new ArrayList<>();

    public SlideGroupManager(@NotNull ImageStickPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "slides.yml");
        load();
    }

    public void register(SlideGroup group) {
        groups.add(group);
        for (UUID uuid : group.getFrameUUIDs()) {
            frameIndex.put(uuid, group);
        }
        save();
    }

    public SlideGroup getByFrame(UUID frameUUID) {
        return frameIndex.get(frameUUID);
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (int i = 0; i < groups.size(); i++) {
            SlideGroup slideGroup   = groups.get(i);
            String  key = "groups." + i;

            config.set(key + ".directory", slideGroup.getDirectory());
            config.set(key + ".width", slideGroup.getWidth());
            config.set(key + ".height", slideGroup.getHeight());
            config.set(key + ".rotation", slideGroup.getRotation().name());
            config.set(key + ".currentIndex", slideGroup.getCurrentIndex());
            config.set(key + ".slideNames", slideGroup.getSlideNames());

            List<String> uuids = new ArrayList<>();
            for (UUID uuid : slideGroup.getFrameUUIDs()) uuids.add(uuid.toString());
            config.set(key + ".frameUUIDs", uuids);
        }
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save slides.yml", e);
        }
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = cfg.getConfigurationSection("groups");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection sec = root.getConfigurationSection(key);
                if (sec == null) continue;

                String directory = sec.getString("directory");
                int width = sec.getInt("width");
                int height = sec.getInt("height");
                Rotation rotation = Rotation.valueOf(sec.getString("rotation", "NONE"));
                int currentIndex = sec.getInt("currentIndex", 0);
                List<String> slideNames = sec.getStringList("slideNames");

                List<String> rawUUIDs = sec.getStringList("frameUUIDs");
                List<UUID> uuids = new ArrayList<>();
                for (String s : rawUUIDs) uuids.add(UUID.fromString(s));

                SlideGroup group = new SlideGroup(directory, width, height, uuids, rotation, slideNames, currentIndex);
                groups.add(group);
                for (UUID uuid : uuids) frameIndex.put(uuid, group);

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load slide group entry '" + key + "': " + e.getMessage(), e);
            }
        }
        plugin.getLogger().info("Loaded " + groups.size() + " slide group(s) from slides.yml");
    }
}