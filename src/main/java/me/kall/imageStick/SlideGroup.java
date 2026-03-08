package me.kall.imageStick;

import org.bukkit.Rotation;

import java.util.List;
import java.util.UUID;


public class SlideGroup {
    private final String directory;
    private final int width;
    private final int height;
    private final List<UUID> frameUUIDs;
    private final Rotation rotation;
    private final List<String> slideNames;
    private int currentIndex;

    public SlideGroup(String directory, int width, int height, List<UUID> frameUUIDs, Rotation rotation, List<String> slideNames, int currentIndex) {
        this.directory   = directory;
        this.width       = width;
        this.height      = height;
        this.frameUUIDs  = frameUUIDs;
        this.rotation    = rotation;
        this.slideNames  = slideNames;
        this.currentIndex = currentIndex;
    }

    public String getDirectory()       { return directory; }
    public int    getWidth()           { return width; }
    public int    getHeight()          { return height; }
    public List<UUID> getFrameUUIDs()  { return frameUUIDs; }
    public Rotation   getRotation()    { return rotation; }
    public List<String> getSlideNames(){ return slideNames; }
    public int    getCurrentIndex()    { return currentIndex; }
    public int    getTotalSlides()     { return slideNames.size(); }

    public String currentSlideName()   { return slideNames.get(currentIndex); }

    public boolean step(int delta, boolean loop) {
        int next = currentIndex + delta;
        if (loop) {
            next = Math.floorMod(next, slideNames.size());
        } else {
            if (next < 0 || next >= slideNames.size()) return false;
        }
        currentIndex = next;
        return true;
    }
}