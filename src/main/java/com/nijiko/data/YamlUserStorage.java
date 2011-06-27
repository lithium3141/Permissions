package com.nijiko.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.bukkit.util.config.Configuration;
import org.bukkit.util.config.ConfigurationNode;

import com.nijiko.permissions.EntryType;

public class YamlUserStorage implements UserStorage {
    protected final Configuration userConfig;
    private final ReentrantReadWriteLock rwl;
    private boolean modified;
    private final String world;
    // private int taskId;
    private boolean saveOff;

    YamlUserStorage(Configuration userConfig, String world, int reloadDelay, boolean autoSave) {
        this.userConfig = userConfig;
        this.world = world;
        this.rwl = new ReentrantReadWriteLock(false);
        this.saveOff = !autoSave;
        performConversion();
        reload();
    }

    private void performConversion() {
        userConfig.load();

        boolean converting = false;

        for (String user : this.getEntries()) {
            ConfigurationNode node = userConfig.getNode("users." + user);

            if (node.getProperty("groups") == null) {
                if (!converting) {
                    System.out.println("[Permissions] Converting GM/2.x syntax files...");
                    converting = true;
                }
                LinkedHashSet<String> groups = new LinkedHashSet<String>();

                String mainGroup = node.getString("group");
                if (mainGroup != null)
                    groups.add(mainGroup);

                List<String> subgroups = node.getStringList("subgroups", null);
                for (String subgroup : subgroups) {
                    if (subgroup != null && !subgroup.isEmpty())
                        groups.add(subgroup);
                }

                node.removeProperty("group");
                node.removeProperty("subgroups");

                node.setProperty("groups", new LinkedList<String>(groups));

                LinkedHashSet<String> perms = new LinkedHashSet<String>();
                List<String> oldperms = node.getStringList("permissions", null);
                for (String oldperm : oldperms) {
                    if (oldperm != null && !oldperm.isEmpty()) {
                        perms.add(oldperm.startsWith("+") ? oldperm.substring(1) : oldperm);
                    }
                }
                node.setProperty("permissions", new LinkedList<String>(perms));

            } else
                break;
        }

        if (converting) {
            System.out.println("[Permissions] Conversion complete!");
        }

        userConfig.save();
    }

    @Override
    public Set<String> getPermissions(String name) {
        name = name.replace('.', ','); // Fix for legacy usernames with periods
        Set<String> permissions;
        rwl.readLock().lock();
        try {
            permissions = new HashSet<String>(userConfig.getStringList("users." + name + ".permissions", null));
        } finally {
            rwl.readLock().unlock();
        }
        return permissions;
    }

    @Override
    public LinkedHashSet<GroupWorld> getParents(String name) {
        name = name.replace('.', ','); // Fix for legacy usernames with periods
        List<String> rawParents;
        rwl.readLock().lock();
        try {
            rawParents = userConfig.getStringList("users." + name + ".groups", null);
        } finally {
            rwl.readLock().unlock();
        }
        LinkedHashSet<GroupWorld> parents = new LinkedHashSet<GroupWorld>(rawParents.size());
        for (String raw : rawParents) {
            String[] split = raw.split(",", 2); // Split into at most 2 parts
                                                // ("world,blah" -> "world",
                                                // "blah")("blah" -> "blah")
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
        // System.out.println("Adding permission " + permission + " to " + name
        // + " in world '" + world + "'.");
        name = name.replace('.', ','); // Fix for legacy usernames with periods
        Set<String> permissions;
        rwl.writeLock().lock();
        try {
            permissions = new LinkedHashSet<String>(userConfig.getStringList("users." + name + ".permissions", null));
            permissions.add(permission);
            userConfig.setProperty("users." + name + ".permissions", new LinkedList<String>(permissions));
            modified = true;
            save();
        } finally {
            rwl.writeLock().unlock();
        }
        // System.out.println(userConfig.getStringList("users." + name +
        // ".permissions", null));
        // System.out.println(userConfig.getStringList("users." + name +
        // ".permissions", null));
    }

    @Override
    public void removePermission(String name, String permission) {
        // System.out.println("Removing permission " + permission + " from " +
        // name + " in world '" + world + "'.");
        name = name.replace('.', ','); // Fix for legacy usernames with periods
        Set<String> permissions;
        rwl.writeLock().lock();
        try {
            permissions = new LinkedHashSet<String>(userConfig.getStringList("users." + name + ".permissions", null));
            permissions.remove(permission);
            userConfig.setProperty("users." + name + ".permissions", new LinkedList<String>(permissions));
            modified = true;
            save();
        } finally {
            rwl.writeLock().unlock();
        }
    }

    @Override
    public void addParent(String name, String groupWorld, String groupName) {
        name = name.replace('.', ','); // Fix for legacy usernames with periods
        rwl.writeLock().lock();
        try {
            Set<String> parents = new HashSet<String>(userConfig.getStringList("users." + name + ".groups", null));
            if (groupWorld == null || this.world.equalsIgnoreCase(groupWorld))
                parents.add(groupName);
            else
                parents.add(groupWorld + "," + groupName);
            userConfig.setProperty("users." + name + ".groups", new LinkedList<String>(parents));
            modified = true;
            save();
        } finally {
            rwl.writeLock().unlock();
        }

    }

    @Override
    public void removeParent(String name, String groupWorld, String groupName) {
        name = name.replace('.', ','); // Fix for legacy usernames with periods
        rwl.writeLock().lock();
        try {
            Set<String> parents = new HashSet<String>(userConfig.getStringList("users." + name + ".groups", null));
            if (groupWorld == null || this.world.equalsIgnoreCase(groupWorld))
                parents.remove(groupName);
            else
                parents.remove(groupWorld + "," + groupName);
            userConfig.setProperty("users." + name + ".groups", new LinkedList<String>(parents));
            modified = true;
            save();
        } finally {
            rwl.writeLock().unlock();
        }
    }

    @Override
    public Set<String> getEntries() {
        rwl.readLock().lock();
        List<String> rawUsers = null;
        try {
            rawUsers = userConfig.getKeys("users");
        } finally {
            rwl.readLock().unlock();
        }
        Set<String> users = new HashSet<String>();
        if (rawUsers != null)
            for (String username : rawUsers) {
                if (username == null)
                    continue;
                users.add(username.replace(',', '.'));
            }
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
            if (modified) {
                // System.out.println("Saving world '" + world + "'.");
                userConfig.save();
            }
            userConfig.load();
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
            userConfig.load();
            modified = false;
        } finally {
            rwl.writeLock().unlock();
        }
        // System.out.println("Reloading user config for world \""+world+"\".");
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
    public boolean create(String name) {
        boolean created = false;
        rwl.writeLock().lock();
        try {
            if (userConfig.getProperty("users." + name) == null) {
                Map<String, Object> template = new HashMap<String, Object>();
                template.put("groups", null);
                template.put("permissions", null);
                userConfig.setProperty("users." + name, template);
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
        rwl.writeLock().lock();
        boolean exists = false;
        try {
            exists = userConfig.getProperty("users." + name) != null;
            userConfig.removeProperty("users." + name);
            modified = true;
            save();
        } finally {
            rwl.writeLock().unlock();
        }
        return exists;
    }

    @Override
    public void removeData(String name, String path) {
        rwl.writeLock().lock();
        try {
            userConfig.removeProperty("users." + name + ".info." + path);
            modified = true;
            save();
        } finally {
            rwl.writeLock().unlock();
        }
        return;
    }

    @Override
    public void setData(String name, String path, Object data) {
        rwl.writeLock().lock();
        try {
            userConfig.setProperty("users." + name + ".info." + path, data);
            modified = true;
            save();
        } finally {
            rwl.writeLock().unlock();
        }
        return;
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
        Object data = null;
        try {
            data = userConfig.getProperty("users." + name + ".info." + path);
        } finally {
            rwl.readLock().unlock();
        }
        return data;
    }

    @Override
    public EntryType getType() {
        return EntryType.USER;
    }
}
