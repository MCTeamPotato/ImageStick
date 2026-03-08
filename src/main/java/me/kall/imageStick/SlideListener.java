package me.kall.imageStick;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.objectholders.ImageMap;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SlideListener implements Listener {
    private final ImageStickPlugin  plugin;
    private final SlideGroupManager groupManager;

    public SlideListener(ImageStickPlugin plugin, SlideGroupManager groupManager) {
        this.plugin       = plugin;
        this.groupManager = groupManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity entity = event.getRightClicked();
        if (!(entity instanceof ItemFrame)) return;

        Player    player = event.getPlayer();
        ItemStack item   = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.STICK) return;

        SlideGroup group = groupManager.getByFrame(entity.getUniqueId());
        if (group == null) return;

        event.setCancelled(true);

        int delta = player.isSneaking() ? -1 : 1;
        boolean loop = plugin.getConfig().getBoolean("slide-loop", false);

        if (!group.step(delta, loop)) {
            String msg = delta > 0 ? ChatColor.YELLOW + "Already on the last slide." : ChatColor.YELLOW + "Already on the first slide.";
            player.sendMessage(msg);
            return;
        }

        String targetName = group.currentSlideName();
        ImageMap imageMap = findMapByName(targetName);
        if (imageMap == null) {
            player.sendMessage(ChatColor.RED + "ImageFrame map not found: " + targetName);
            player.sendMessage(ChatColor.GRAY  + "Did you run /imagestick import first?");

            group.step(-delta, loop);
            return;
        }

        if (imageMap.getWidth() != group.getWidth() || imageMap.getHeight() != group.getHeight()) {
            player.sendMessage(ChatColor.RED + "Map dimension mismatch for: " + targetName);
            group.step(-delta, loop);
            return;
        }

        List<ItemFrame> frames = resolveFrames(group.getFrameUUIDs());
        if (frames == null) {
            player.sendMessage(ChatColor.RED + "Some item frames in this slide group could not be found.");
            player.sendMessage(ChatColor.GRAY  + "They may have been broken. Re-bind with /imagestick bind.");
            group.step(-delta, loop);
            return;
        }

        Rotation rotation = group.getRotation();
        imageMap.fillItemFrames(frames, rotation, (f, i) -> true, (f, i) -> {}, ImageFrame.mapItemFormat);

        groupManager.save();

        int current = group.getCurrentIndex() + 1;
        int total   = group.getTotalSlides();
        player.sendMessage(ChatColor.GREEN + "Slide " + current + " / " + total + ChatColor.GRAY + "  [" + targetName + "]");
    }

    private @Nullable ImageMap findMapByName(String name) {
        for (ImageMap map : ImageFrame.imageMapManager.getMaps()) {
            if (map.getName().equalsIgnoreCase(name)) return map;
        }
        return null;
    }

    private @Nullable List<ItemFrame> resolveFrames(@NotNull List<UUID> uuids) {
        List<ItemFrame> frames = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) {
            Entity e = Bukkit.getEntity(uuid);
            if (!(e instanceof ItemFrame)) return null;
            frames.add((ItemFrame) e);
        }
        return frames;
    }
}