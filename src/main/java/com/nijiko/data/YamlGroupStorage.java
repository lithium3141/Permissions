package com.nijiko.data;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.bukkit.util.config.Configuration;

import com.nijiko.permissions.EntryType;

public class YamlGroupStorage implements GroupStorage {
    private final Configuration groupConfig;
    private final ReentrantReadWriteLock rwl;
    private boolean modified;
    private final String world;
    // private int taskId;
    private boolean saveOff;

    YamlGroupStorage(Configuration groupConfig, String world, int reloadDelay, boolean autoSave) {
        this.groupConfig = groupConfig;
        this.world = world;
        this.rwl = new ReentrantReadWriteLock(false);
        this.saveOff = !autoSave;
        reload();
    }

    @Override
    public Set<String> getPermissions(String name) {
        rwl.readLock().lock();
        List<String> rawPerms;
        try {
            rawPerms = groupConfig.getStringList("groups." + name + ".permissions", null);
        } finally {
            rwl.readLock().unlock();
        }
        Set<String> permissions = new HashSet<String>();
        if (rawPerms != null && !rawPerms.isEmpty())
            permissions.addAll(rawPerms);
        return permissions;
    }

    @Override
    public LinkedHashSet<GroupWorld> getParents(String name) {
        List<String> rawParents = null;
        rwl.readLock().lock();
        try {
            rawParents = groupConfig.getStringList("groups." + name + ".inheritance", null);
        } finally {
            rwl.readLock().unlock();
        }
        LinkedHashSet<GroupWorld> parents = new LinkedHashSet<GroupWorld>(rawParents.size());
        for (String raw : rawParents) {
            String[] split = raw.split(",", 2); // Split into at most 2 parts
            // ("world,blah" -> "world", "blah")("blah" -> "blah")
            if (split.length == 0)
                continue;
            if (split.length == 1)
                parents.add(new GroupWorld(world, split[0]));
            else
                parents.add(new GroupWorld(split[0], split[1]));
        }
        return parents;
    }

    @Override
    public void addPermission(String name, String permission) {
        rwl.writeLock().lock();
        try {
            Set<String> permissions = new HashSet<String>(groupConfig.getStringList("groups." + name + ".permissions", null));
            permissions.add(permission);
            groupConfig.setProperty("groups." + name + ".permissions", new LinkedList<String>(permissions));
            modified = true;
            save();
        } finally {
            rwl.writeLock().unlock();
        }
    }

    @Override
    public void removePermission(String name, String permission) {
        rwl.writeLock().lock();
        try {
            Set<String> permissions = new HashSet<String>(groupConfig.getStringList("groups." + name + ".permissions", null));
            permissions.remove(permission);
            groupConfig.setProperty("groups." + name + ".permissions", new LinkedList<String>(permissions));
            modified = true;
            save();
        } finally {
            rwl.writeLock().unlock();
        }
    }

    @Override
    public void addParent(String name, String groupWorld, String groupName) {
        rwl.writeLock().lock();
        try {
            Set<String> parents = new HashSet<String>(groupConfig.getStringList("groups." + name + ".inheritance", null));
            if (groupWorld == null || this.world.equalsIgnoreCase(groupWorld))
                parents.add(groupName);
            else
                parents.add(groupWorld + "," + groupName);
            groupConfig.setProperty("groups." + name + ".inheritance", new LinkedList<String>(parents));
            modified = true;
            save();
        } finally {
            rwl.writeLock().unlock();
        }
    }

    @Override
    public void removeParent(String name, String groupWorld, String groupName) {
        rwl.writeLock().lock();
        try {
            Set<String> parents = new HashSet<String>(groupConfig.getStringList("groups." + name + ".inheritance", null));
            if (groupWorld == null || this.world.equalsIgnoreCase(groupWorld))
                parents.remove(groupName);
            else
                parents.remove(groupWorld + "," + groupName);
            groupConfig.setProperty("groups." + name + ".inheritance", new LinkedList<String>(parents));
            modified = true;
            save();
        } finally {
            rwl.writeLock().unlock();
        }
    }

    @Override
    public Set<String> getEntries() {
        rwl.readLock().lock();
        List<String> rawGroups = null;
        try {
            rawGroups = groupConfig.getKeys("groups");
        } finally {
            rwl.readLock().unlock();
        }
        Set<String> users = rawGroups == null ? new LinkedHashSet<String>() : new LinkedHashSet<String>(rawGroups);
        return users;
    }

    @Override
    public String getWorld() {
        return world;
    }

    @Override
    public void forceSave() {
        rwl.writeLock().lock();
        try {
            if (modified)
                groupConfig.save();
            groupConfig.load();
            modified = false;
        } finally {
            rwl.writeLock().unlock();
        }
    }

    @Override
    public void save() {
        rwl.writeLock().lock();
        try {
            if (!saveOff)
                forceSave();
        } finally {
            rwl.writeLock().unlock();
        }
    }

    @Override
    public void reload() {
        rwl.writeLock().lock();
        try {
            groupConfig.load();
            modified = false;
        } finally {
            rwl.writeLock().unlock();
        }
        // System.out.println("Reloading group config for world \""+world+"\".");
    }

    @Override
    public boolean isAutoSave() {
        rwl.readLock().lock();
        boolean save = true;
        try {
            save = saveOff;
        } finally {
            rwl.readLock().unlock();
        }
        return save;
    }

    @Override
    public void setAutoSave(boolean autoSave) {
        rwl.writeLock().lock();
        try {
            saveOff = autoSave;
        } finally {
            rwl.writeLock().unlock();
        }
    }

    @Override
    public boolean isDefault(String name) {
        boolean isDefault = false;
        rwl.readLock().lock();
        try {
            isDefault = groupConfig.getBoolean("groups." + name + ".default", false);
        } finally {
            rwl.readLock().unlock();
        }
        return isDefault;
    }

    @Override
    public boolean create(String name) {
        boolean created = false;
        rwl.writeLock().lock();
        try {
            if (groupConfig.getProperty("groups." + name) == null) {
                Map<String, Object> template = new HashMap<String, Object>();
                template.put("inheritance", null);
                template.put("permissions", null);
                groupConfig.setProperty("groups." + name, template);
                modified = true;
                created = true;
                save();
            }
        } finally {
            rwl.writeLock().unlock();
        }
        return created;
    }

    @Override
    public boolean delete(String name) {
        boolean exists = false;
        rwl.writeLock().lock();
        try {
            exists = groupConfig.getProperty("groups." + name) != null;
            groupConfig.removeProperty("groups." + name);
            modified = true;
            save();
        } finally {
            rwl.writeLock().unlock();
        }
        return exists;
    }

    @Override
    public void setData(String name, String path, Object data) {
        rwl.writeLock().lock();
        try {
            groupConfig.setProperty("groups." + name + ".info." + path, data);
            modified = true;
            save();
        } finally {
            rwl.writeLock().unlock();
        }
        return;
    }

    @Override
    public Set<String> getTracks() {
        List<String> rawTracks = null;
        rwl.readLock().lock();
        try {
            rawTracks = groupConfig.getKeys("tracks");
        } finally {
            rwl.readLock().unlock();
        }
        if (groupConfig.getProperty("track") instanceof List) {
            if(rawTracks == null)
                rawTracks = Arrays.asList((String) null);
            else
                rawTracks.add(null);
        }
        return new HashSet<String>(rawTracks);
    }

    @Override
    public LinkedList<GroupWorld> getTrack(String trackName) {
        List<String> rawGroups = null;
        rwl.readLock().lock();
        try {
            if (trackName == null) {
                rawGroups = groupConfig.getStringList("track", null);

            } else {
                rawGroups = groupConfig.getStringList("tracks." + trackName, null);
            }
        } finally {
            rwl.readLock().unlock();
        }
        
        if (rawGroups == null)
            return null;
        LinkedHashSet<GroupWorld> track = new LinkedHashSet<GroupWorld>(rawGroups.size());
        for (String raw : rawGroups) {
            String[] split = raw.split(",", 2); // Split into at most 2 parts
            // ("world,blah" -> "world",
            // "blah")("blah" -> "blah")
            if (split.length == 0)
                continue;
            if (split.length == 1)
                track.add(new GroupWorld(world, split[0]));
            else
                track.add(new GroupWorld(split[0], split[1]));
        }
        return new LinkedList<GroupWorld>(track);
    }

    @Override
    public void removeData(String name, String path) {
        rwl.writeLock().lock();
        try {
            groupConfig.removeProperty("groups." + name + ".info." + path);
            modified = true;
            save();
        } finally {
            rwl.writeLock().unlock();
        }
    }

    @Override
    public String getString(String name, String path) {
        Object raw = getObj(name, path);
        if (raw instanceof String)
            return (String) raw;
        if (raw == null)
            return null;
        return raw.toString();
    }

    @Override
    public Integer getInt(String name, String path) {
        Object raw = getObj(name, path);
        if (raw instanceof Integer)
            return (Integer) raw;
        if (raw == null)
            return null;
        int val;
        try {
            val = Integer.valueOf(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
        return val;
    }

    @Override
    public Double getDouble(String name, String path) {
        Object raw = getObj(name, path);
        if (raw instanceof Double)
            return (Double) raw;
        if (raw == null)
            return null;
        double val;
        try {
            val = Double.valueOf(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
        return val;
    }

    @Override
    public Boolean getBool(String name, String path) {
        Object raw = getObj(name, path);
        if (raw instanceof Boolean)
            return (Boolean) raw;
        if (raw == null)
            return null;
        boolean val = Boolean.valueOf(raw.toString());
        return val;
    }

    private Object getObj(String name, String path) {
        rwl.readLock().lock();
        Object data = groupConfig.getProperty("groups." + name + ".info." + path);
        rwl.readLock().unlock();
        return data;
    }

    @Override
    public EntryType getType() {
        return EntryType.GROUP;
    }
}
