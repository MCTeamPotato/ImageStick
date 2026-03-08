package me.kall.imageStick;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.logging.Level;

public class ImageStickPlugin extends JavaPlugin {
    private ImageHttpServer   httpServer;
    private SlideGroupManager slideGroupManager;
    private String            resolvedIp;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        File imagesRoot = new File(getDataFolder(), "images");
        if (!imagesRoot.exists()) {
            //noinspection ResultOfMethodCallIgnored
            imagesRoot.mkdirs();
            getLogger().info("Created images directory at: " + imagesRoot.getAbsolutePath());
        }

        String configuredIp = getConfig().getString("server-ip", "auto");
        if ("auto".equalsIgnoreCase(configuredIp)) {
            try {
                resolvedIp = InetAddress.getLocalHost().getHostAddress();
                getLogger().info("Auto-detected server IP: " + resolvedIp);
            } catch (Exception e) {
                resolvedIp = "127.0.0.1";
                getLogger().warning("Could not auto-detect IP, falling back to 127.0.0.1. " + "Set 'server-ip' in config.yml to your public IP.");
            }
        } else {
            resolvedIp = configuredIp;
        }

        int port = getConfig().getInt("http-port", 8765);
        httpServer = new ImageHttpServer(imagesRoot, port);
        try {
            httpServer.start();
            getLogger().info("ImageStick HTTP server started on port " + port);
            getLogger().info("Images served from: " + imagesRoot.getAbsolutePath());
            getLogger().info("Base URL: http://" + resolvedIp + ":" + port + "/");
        } catch (IOException e) {
            getLogger().log(Level.SEVERE,
                    "Failed to start HTTP server on port " + port + ": " + e.getMessage(), e);
        }

        slideGroupManager = new SlideGroupManager(this);

        ImageStickCommand commandExecutor = new ImageStickCommand(this, slideGroupManager);
        //noinspection DataFlowIssue
        getCommand("imagestick").setExecutor(commandExecutor);
        //noinspection DataFlowIssue
        getCommand("imagestick").setTabCompleter(commandExecutor);

        getServer().getPluginManager().registerEvents(
                new SlideListener(this, slideGroupManager), this);

        getLogger().info("ImageStick enabled. Place images under plugins/ImageStick/images/<directory>/");
    }

    @Override
    public void onDisable() {
        if (httpServer != null) {
            httpServer.stop();
            getLogger().info("ImageStick HTTP server stopped.");
        }
    }

    public String getResolvedIp()       { return resolvedIp; }
    public int    getHttpPort()         { return getConfig().getInt("http-port", 8765); }
    public int    getCommandDelayTicks(){ return getConfig().getInt("command-delay-ticks", 5); }

    public File getImagesRoot() {
        return new File(getDataFolder(), "images");
    }

    public SlideGroupManager getSlideGroupManager() {
        return slideGroupManager;
    }
}