package me.kall.imageStick;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.api.events.ImageMapAddedEvent;
import com.loohp.imageframe.objectholders.ItemFrameSelectionManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Rotation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ImageStickCommand implements CommandExecutor, TabCompleter {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");

    private final ImageStickPlugin  plugin;
    private final SlideGroupManager groupManager;

    public ImageStickCommand(ImageStickPlugin plugin, SlideGroupManager groupManager) {
        this.plugin       = plugin;
        this.groupManager = groupManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("imagestick.use")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (args.length < 1) { sendUsage(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "import" -> handleImport(sender, args);
            case "bind"   -> handleBind(sender, args);
            case "reload" -> handleReload(sender);
            default       -> sendUsage(sender);
        }
        return true;
    }

    private void handleImport(CommandSender sender, @NotNull String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /imagestick import <directory> <width> <height>");
            return;
        }
        String dirName = args[1];
        int width, height;
        try {
            width  = Integer.parseInt(args[2]);
            height = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Width and height must be integers.");
            return;
        }
        File imageDir = new File(plugin.getImagesRoot(), dirName);
        if (!imageDir.exists() || !imageDir.isDirectory()) {
            sender.sendMessage(ChatColor.RED + "Directory not found: plugins/ImageStick/images/" + dirName);
            sender.sendMessage(ChatColor.YELLOW + "Place your images there and try again.");
            return;
        }
        List<File> imageFiles = collectSortedImages(imageDir);
        if (imageFiles.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No supported image files found in: " + dirName);
            sender.sendMessage(ChatColor.YELLOW + "Supported formats: png, jpg, jpeg, gif, webp");
            return;
        }
        String baseUrl = "http://" + plugin.getResolvedIp() + ":" + plugin.getHttpPort();
        sender.sendMessage(ChatColor.GREEN + "Starting ImageFrame import for " + imageFiles.size() + " image(s) in '" + dirName + "'...");
        sender.sendMessage(ChatColor.GRAY + "Base URL: " + baseUrl + "/" + dirName + "/");
        sender.sendMessage(ChatColor.GRAY + "Each image will be dispatched after the previous one finishes.");
        dispatchNext(sender, imageFiles, 0, dirName, baseUrl, width, height);
    }

    private void dispatchNext(CommandSender sender, @NotNull List<File> files, int index,
                              String dirName, String baseUrl, int width, int height) {
        if (index >= files.size()) {
            sender.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ All " + files.size() + " ImageFrame maps created!");
            return;
        }
        File   imgFile   = files.get(index);
        String fileName  = imgFile.getName();
        String frameName = dirName + "_" + stripExtension(fileName);
        String url       = baseUrl + "/" + dirName + "/" + fileName;
        String cmd       = "imageframe create " + frameName + " " + url + " " + width + " " + height + " combined";

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Must be run by a player.");
            return;
        }

        Listener[] holder = new Listener[1];
        holder[0] = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onImageMapAdded(ImageMapAddedEvent event) {
                if (!event.getImageMap().getName().equalsIgnoreCase(frameName)) return;
                HandlerList.unregisterAll(holder[0]);
                int next = index + 1;
                sender.sendMessage(ChatColor.AQUA + "[" + next + "/" + files.size() + "] " + ChatColor.WHITE + frameName + ChatColor.GREEN + " ✔");
                Bukkit.getScheduler().runTask(plugin, () -> dispatchNext(sender, files, next, dirName, baseUrl, width, height));
            }
        };
        Bukkit.getPluginManager().registerEvents(holder[0], plugin);

        sender.sendMessage(ChatColor.GRAY + "Creating: " + frameName + "...");
        Bukkit.dispatchCommand(player, cmd);
    }

    private void handleBind(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
            return;
        }
        if (args.length < 4) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /imagestick bind <directory> <width> <height>");
            return;
        }
        String dirName = args[1];
        int width, height;
        try {
            width  = Integer.parseInt(args[2]);
            height = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Width and height must be integers.");
            return;
        }

        File imageDir = new File(plugin.getImagesRoot(), dirName);
        if (!imageDir.exists() || !imageDir.isDirectory()) {
            player.sendMessage(ChatColor.RED + "Directory not found: plugins/ImageStick/images/" + dirName);
            return;
        }
        List<File> imageFiles = collectSortedImages(imageDir);
        if (imageFiles.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No images found in directory: " + dirName);
            return;
        }

        RayTraceResult ray = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 5.0, e -> e instanceof ItemFrame);
        if (ray == null || !(ray.getHitEntity() instanceof ItemFrame cornerFrame)) {
            player.sendMessage(ChatColor.RED + "Look at the top-left item frame of the wall first.");
            return;
        }

        float yaw = player.getLocation().getYaw();
        ItemFrameSelectionManager.SelectedItemFrameResult selection = ImageFrame.combinedMapItemHandler.findItemFrames(cornerFrame, yaw, width, height, item -> true);

        if (selection == null) {
            player.sendMessage(ChatColor.RED + "Could not find a " + width + "×" + height + " item-frame arrangement starting here.");
            player.sendMessage(ChatColor.GRAY + "Make sure you have " + (width * height) + " adjacent item frames with the same facing direction.");
            return;
        }

        List<ItemFrame> frames = selection.getItemFrames();
        if (frames.size() != width * height) {
            player.sendMessage(ChatColor.RED + "Expected " + (width * height) + " frames, found " + frames.size());
            return;
        }

        Rotation rotation = selection.getRotation();

        List<UUID> frameUUIDs = new ArrayList<>();
        for (ItemFrame f : frames) frameUUIDs.add(f.getUniqueId());

        List<String> slideNames = new ArrayList<>();
        for (File imgFile : imageFiles) {
            slideNames.add(dirName + "_" + stripExtension(imgFile.getName()));
        }

        SlideGroup group = new SlideGroup(dirName, width, height, frameUUIDs, rotation, slideNames, 0);
        groupManager.register(group);

        player.sendMessage(ChatColor.GREEN + "✔ Slide group bound!");
        player.sendMessage(ChatColor.GRAY  + "  Directory : " + dirName);
        player.sendMessage(ChatColor.GRAY  + "  Size      : " + width + "×" + height);
        player.sendMessage(ChatColor.GRAY  + "  Slides    : " + slideNames.size());
        player.sendMessage(ChatColor.GRAY  + "  Frames    : " + frameUUIDs.size());
        player.sendMessage(ChatColor.YELLOW + "Right-click any frame with a stick → next slide");
        player.sendMessage(ChatColor.YELLOW + "Shift + right-click → previous slide");
    }


    private void handleReload(@NotNull CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "ImageStick config reloaded.");
    }

    private void sendUsage(@NotNull CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "=== ImageStick ===");
        sender.sendMessage(ChatColor.YELLOW + "/imagestick import <directory> <width> <height>");
        sender.sendMessage(ChatColor.GRAY   + "  Batch-imports all images in plugins/ImageStick/images/<directory>/");
        sender.sendMessage(ChatColor.YELLOW + "/imagestick bind <directory> <width> <height>");
        sender.sendMessage(ChatColor.GRAY   + "  Binds the item-frame wall you're looking at to a slide group");
        sender.sendMessage(ChatColor.YELLOW + "/imagestick reload");
        sender.sendMessage(ChatColor.GRAY   + "  Reloads config.yml");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("imagestick.use")) return Collections.emptyList();

        if (args.length == 1) return Stream.of("import", "bind", "reload").filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());

        boolean isImportOrBind = "import".equalsIgnoreCase(args[0]) || "bind".equalsIgnoreCase(args[0]);

        if (args.length == 2 && isImportOrBind) {
            File[] dirs = plugin.getImagesRoot().listFiles(File::isDirectory);
            if (dirs == null) return Collections.emptyList();
            return Arrays.stream(dirs)
                    .map(File::getName)
                    .filter(n -> n.startsWith(args[1]))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && isImportOrBind) return List.of("1", "2", "4");
        if (args.length == 4 && isImportOrBind) return List.of("1", "2", "4");
        return Collections.emptyList();
    }

    @NotNull
    private List<File> collectSortedImages(@NotNull File dir) {
        File[] files = dir.listFiles(f -> f.isFile() && IMAGE_EXTENSIONS.contains(getExtension(f.getName()).toLowerCase()));
        if (files == null) return Collections.emptyList();
        List<File> list = Arrays.asList(files);
        list.sort(Comparator.comparingInt(ImageStickCommand::extractTrailingNumber).thenComparing(File::getName));
        return list;
    }

    private static int extractTrailingNumber(@NotNull File f) {
        String  name = stripExtension(f.getName());
        Matcher m    = Pattern.compile("(\\d+)$").matcher(name);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    @NotNull
    private static String getExtension(@NotNull String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot + 1) : "";
    }

    @NotNull
    static String stripExtension(@NotNull String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(0, dot) : filename;
    }
}