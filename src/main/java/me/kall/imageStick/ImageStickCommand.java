package me.kall.imageStick;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ImageStickCommand implements CommandExecutor, TabCompleter {
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");

    private final ImageStickPlugin plugin;

    public ImageStickCommand(ImageStickPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("imagestick.use")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "import" -> handleImport(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendUsage(sender);
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
        int delayTicks = plugin.getCommandDelayTicks();

        sender.sendMessage(ChatColor.GREEN + "Starting ImageFrame import for " + imageFiles.size() + " image(s) in '" + dirName + "'...");
        sender.sendMessage(ChatColor.GRAY + "Base URL: " + baseUrl + "/" + dirName + "/");
        sender.sendMessage(ChatColor.GRAY + "Each command dispatched with " + delayTicks + " tick(s) delay.");
        dispatchNext(sender, imageFiles, 0, dirName, baseUrl, width, height);
    }

    private void dispatchNext(CommandSender sender, @NotNull List<File> files, int index, String dirName, String baseUrl, int width, int height) {
        if (index >= files.size()) {
            sender.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ All " + files.size() + " ImageFrame commands dispatched!");
            return;
        }

        File imgFile = files.get(index);
        String fileName = imgFile.getName();
        String frameName = dirName + "_" + stripExtension(fileName);
        String url = baseUrl + "/" + dirName + "/" + fileName;
        String cmd = "imageframe create " + frameName + " " + url + " " + width + " " + height + " combined";

        if (sender instanceof org.bukkit.entity.Player player) {
            Bukkit.dispatchCommand(player, cmd);
        } else {
            sender.sendMessage(ChatColor.RED + "Must be run by a player.");
            return;
        }

        sender.sendMessage(ChatColor.AQUA + "[" + (index + 1) + "/" + files.size() + "] " + ChatColor.WHITE + "Dispatched: /" + cmd);

        Bukkit.getScheduler().runTaskLater(plugin, () -> dispatchNext(sender, files, index + 1, dirName, baseUrl, width, height), plugin.getCommandDelayTicks());
    }

    private void handleReload(@NotNull CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "ImageStick config reloaded.");
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
        String name = stripExtension(f.getName());
        Matcher m = Pattern.compile("(\\d+)$").matcher(name);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    @NotNull
    private static String getExtension(@NotNull String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot + 1) : "";
    }

    @NotNull
    private static String stripExtension(@NotNull String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(0, dot) : filename;
    }

    private void sendUsage(@NotNull CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "=== ImageStick ===");
        sender.sendMessage(ChatColor.YELLOW + "/imagestick import <directory> <width> <height>");
        sender.sendMessage(ChatColor.GRAY   + "  Batch-imports all images in plugins/ImageStick/images/<directory>/");
        sender.sendMessage(ChatColor.YELLOW + "/imagestick reload");
        sender.sendMessage(ChatColor.GRAY   + "  Reloads config.yml");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("imagestick.use")) return Collections.emptyList();

        if (args.length == 1) return Stream.of("import", "reload").filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());

        if (args.length == 2 && "import".equalsIgnoreCase(args[0])) {
            File[] dirs = plugin.getImagesRoot().listFiles(File::isDirectory);
            if (dirs == null) return Collections.emptyList();
            return Arrays.stream(dirs)
                    .map(File::getName)
                    .filter(n -> n.startsWith(args[1]))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && "import".equalsIgnoreCase(args[0])) return List.of("1", "2", "4");
        if (args.length == 4 && "import".equalsIgnoreCase(args[0])) return List.of("1", "2", "4");
        return Collections.emptyList();
    }
}
