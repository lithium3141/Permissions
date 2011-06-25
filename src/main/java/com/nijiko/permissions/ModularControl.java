package com.nijiko.permissions;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.util.config.Configuration;

import com.nijiko.data.GroupStorage;
import com.nijiko.data.GroupWorld;
import com.nijiko.data.StorageFactory;
import com.nijiko.data.UserStorage;
import com.nijikokun.bukkit.Permissions.Permissions;

public class ModularControl extends PermissionHandler {
    private Map<String, UserStorage> userStores = new HashMap<String, UserStorage>();
    private Map<String, GroupStorage> groupStores = new HashMap<String, GroupStorage>();
    private Map<String, String> userStorageMirrorings = new HashMap<String, String>();
    private Map<String, String> groupStorageMirrorings = new HashMap<String, String>();

    private Map<String, String> userStorageInheritance = new HashMap<String, String>();
    private Map<String, String> groupStorageInheritance = new HashMap<String, String>();

    private Map<String, Map<String, Group>> worldGroups = new HashMap<String, Map<String, Group>>();
    private Map<String, Map<String, User>> worldUsers = new HashMap<String, Map<String, User>>();
    private Map<String, Group> defaultGroups = new HashMap<String, Group>();
    private Configuration storageConfig;
    private String defaultWorld = "";
    PermissionCache cache = new PermissionCache();

    class RefreshTask implements Runnable {
        @Override
        public void run() {
            storageReload();
        }
    }
    
//    static {
//        if(RefreshTask.class.getClassLoader() != ModularControl.class.getClassLoader()) {
//            System.err.println("[Permissions] ClassLoader check failed.");
//            throw new RuntimeException("RefreshTask was loaded using a different classloader.");
//        }
//    }
    
    public ModularControl(Configuration storageConfig) {
//        System.out.println(this.getClass().getClassLoader());
        this.storageConfig = storageConfig;
        loadWorldInheritance();
        int period = storageConfig.getInt("permissions.storage.reload", 6000);
        Permissions.instance.getServer().getScheduler().scheduleAsyncRepeatingTask(Permissions.instance, this.new RefreshTask(), period, period);
    }
    
    //World manipulation methods
    @Override
    public void setDefaultWorld(String world) {
        this.defaultWorld = world;
    }
    @Override
    public boolean reload(String world) {
        cache.reloadWorld(world);
        UserStorage userStore = getUserStorage(world);
        GroupStorage groupStore = getGroupStorage(world);
        if (userStore == null && groupStore == null)
            return false;
        if (userStore != null)
            userStore.reload();
        if (groupStore != null)
            groupStore.reload();
        defaultGroups.remove(world);
        worldUsers.remove(world);
        worldGroups.remove(world);
        load(world, userStore, groupStore);
        return true;
    }
    @Override
    public boolean loadWorld(String world) throws Exception {
        if (checkWorld(world)) {
            forceLoadWorld(world);
            return true;
        }
        return false;
    }

    @Override
    public void forceLoadWorld(String world) throws Exception {
        UserStorage userStore = StorageFactory.getUserStorage(world, storageConfig);
        GroupStorage groupStore = StorageFactory.getGroupStorage(world, storageConfig);
        load(world, userStore, groupStore);
    }

    @Override
    public boolean checkWorld(String world) {
        return ((userStores.get(world) == null) && (userStorageMirrorings.get(world) == null)) || (((groupStores.get(world) == null) && (groupStorageMirrorings.get(world) == null)));
    }

    @Override
    public void load() throws Exception {
        this.loadWorld("*"); // Global permissions
        this.loadWorld(defaultWorld);
    }

    private void storageReload() {
        saveAll();
        cache.flushAll();
        for (UserStorage store : userStores.values()) {
            store.reload();
        }
        for (GroupStorage store : groupStores.values()) {
            store.reload();
        }
        for(Map<String, User> users : worldUsers.values())
            for(User u : users.values())
                u.clearTransientPerms();
        for(Map<String, Group> groups : worldGroups.values())
            for(Group g : groups.values())
                g.clearTransientPerms();
//        try {
//            Class.forName("com.nijiko.data.SqlStorage");
//            SqlStorage.clearWorldCache();
//        } catch (ClassNotFoundException e) {
//        }
        
        Permissions.instance.getServer().getPluginManager().callEvent(new StorageReloadEvent());
    }
    @Override
    public void reload() {
        storageReload();
        defaultGroups.clear();
        worldUsers.clear();
        worldGroups.clear();
        Set<String> worlds = this.getWorlds();
        for (String world : worlds) {
            UserStorage us = getUserStorage(world);
            GroupStorage gs = getGroupStorage(world);
            us.reload();
            gs.reload();
            load(world, us, gs);
        }
    }
    
    @Override
    public void closeAll() {
        cache.flushAll();
        this.saveAll();
        Permissions.instance.getServer().getPluginManager().callEvent(new ControlCloseEvent());
//        try {
//            Class.forName("com.nijiko.data.SqlStorage");
//            SqlStorage.closeAll();
//        } catch (ClassNotFoundException e) {
//        }
    }

    @Override
    public void save(String world) {
        UserStorage userStore = getUserStorage(world);
        GroupStorage groupStore = getGroupStorage(world);
        if (userStore != null)
            userStore.forceSave();
        if (groupStore != null)
            groupStore.forceSave();
    }

    @Override
    public void saveAll() {
        Collection<UserStorage> userStoreColl = this.userStores.values();
        for (UserStorage userStore : userStoreColl) {
            userStore.forceSave();
        }
        Collection<GroupStorage> groupStoreColl = this.groupStores.values();
        for (GroupStorage groupStore : groupStoreColl) {
            groupStore.forceSave();
        }
    }
    
    private void loadWorldInheritance() {
        userStorageInheritance.clear();
        groupStorageInheritance.clear();
        storageConfig.load();
        List<String> worlds = storageConfig.getKeys("permissions.storage.world-inheritance");
        Map<String, String> worldInheritance = new HashMap<String, String>();
        if(worlds != null) {
            for (String world : worlds) {
                String parentWorld = storageConfig.getString("permissions.storage.world-inheritance." + world);
                if (parentWorld != null && !world.equals("*"))
                    worldInheritance.put(world, parentWorld);
            }
        }
        List<String> userWorlds = storageConfig.getKeys("permissions.storage.user.world-inheritance");
        if(userWorlds != null) {
            for (String userWorld : userWorlds) {
                String parentWorld = storageConfig.getString("permissions.storage.user.world-inheritance.users" + userWorld);
                if (parentWorld != null && !userWorld.equals("*"))
                    userStorageInheritance.put(userWorld, parentWorld);
            }
        }
        for (Map.Entry<String, String> inherit : worldInheritance.entrySet()) {
            if (!userStorageInheritance.containsKey(inherit.getKey())) {
                userStorageInheritance.put(inherit.getKey(), inherit.getValue());
            }
        }

        List<String> groupWorlds = storageConfig.getKeys("permissions.storage.group.world-inheritance");
        if(groupWorlds != null) {
            for (String groupWorld : groupWorlds) {
                String parentWorld = storageConfig.getString("permissions.storage.group.world-inheritance." + groupWorld);
                if (parentWorld != null && !groupWorld.equals("*"))
                    userStorageInheritance.put(groupWorld, parentWorld);
            }
        }
        for (Map.Entry<String, String> inherit : worldInheritance.entrySet()) {
            if (!groupStorageInheritance.containsKey(inherit.getKey())) {
                groupStorageInheritance.put(inherit.getKey(), inherit.getValue());
            }
        }
    }

    String getWorldParent(String world, boolean user) {
        return user ? userStorageInheritance.containsKey(world) ? userStorageInheritance.get(world) : null : groupStorageInheritance.containsKey(world) ? groupStorageInheritance.get(world) : null;
    }

    UserStorage getUserStorage(String world) {
        if (world == null)
            return null;
        return this.userStores.get(getParentWorldUser(world));
    }

    GroupStorage getGroupStorage(String world) {
        if (world == null)
            return null;
        return this.groupStores.get(getParentWorldGroup(world));
    }

    private void load(String world, UserStorage userStore, GroupStorage groupStore) {
        if (userStore == null || groupStore == null)
            return;
        String userWorld = userStore.getWorld();
        if (!world.equalsIgnoreCase(userWorld))
            this.userStorageMirrorings.put(world, userWorld);
        String groupWorld = groupStore.getWorld();
        if (!world.equalsIgnoreCase(groupWorld))
            this.groupStorageMirrorings.put(world, groupWorld);
        this.userStores.put(userStore.getWorld(), userStore);
        this.groupStores.put(groupStore.getWorld(), groupStore);

        defaultGroups.remove(groupWorld);

        HashMap<String, Group> groups = new HashMap<String, Group>();
        Set<String> groupNames = groupStore.getEntries();
        for (String groupName : groupNames) {
            Group group = new Group(this, groupStore, groupName, groupWorld, false);
            groups.put(groupName.toLowerCase(), group);
            if (group.isDefault() && defaultGroups.get(world) == null)
                defaultGroups.put(groupWorld, group);
        }
        worldGroups.put(world, groups);
        
        Map<String, User> users = new HashMap<String, User>();
        Set<String> userNames = userStore.getEntries();
        for (String userName : userNames) {
            User user = new User(this, userStore, userName, userWorld, false);
            users.put(userName.toLowerCase(), user);
        }
        worldUsers.put(world, users);
        
        Permissions.instance.getServer().getPluginManager().callEvent(new WorldConfigLoadEvent(world));
    }
   
    @Override  
    public Set<String> getWorlds() {
        Set<String> worlds = new HashSet<String>();
        worlds.addAll(this.userStorageMirrorings.keySet());
        worlds.addAll(this.userStores.keySet());
        worlds.addAll(this.groupStorageMirrorings.keySet());
        worlds.addAll(this.groupStores.keySet());
        return worlds;
    }
    
    public String getParentWorldGroup(String world) {
        if (!world.equals("*") && !world.equals("?") && groupStorageMirrorings.get(world) != null)
            return groupStorageMirrorings.get(world);
        return world;
    }

    public String getParentWorldUser(String world) {
        if (!world.equals("*") && !world.equals("?") && userStorageMirrorings.get(world) != null)
            world = userStorageMirrorings.get(world);
        return world;
    }
    
    //Permission checking methods
    @Override
    public boolean has(String world, String name, String permission) {
        return permission(world, name, permission);
    }

    @Override
    public boolean has(Player player, String permission) {
        return permission(player, permission);
    }

    @Override
    public boolean permission(Player player, String permission) {
        String name = player.getName();
        String worldName = player.getWorld().getName();
        return permission(worldName, name, permission);
    }

    @Override
    public boolean permission(String world, String name, String permission) {
        if (name == null || name.isEmpty() || world == null || world.isEmpty())
            return true;
//        System.out.println("Checking world '" + world + "', user '" + name + "'.");
        world = getParentWorldUser(world);
        User user = this.getUsr(world, name);
        if (user == null)
            return false;
//        System.out.println("Using user object " + user);
        return user.hasPermission(permission);
    }

    //Permission manipulation methods
    @Override
    public void addUserPermission(String world, String user, String node) {
        try {
            safeGetUser(world, user).addPermission(node);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removeUserPermission(String world, String user, String node) {
        try {
            safeGetUser(world, user).removePermission(node);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addGroupPermission(String world, String group, String node) {
        try {
            safeGetGroup(world, group).addPermission(node);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removeGroupPermission(String world, String group, String node) {
        try {
            safeGetGroup(world, group).removePermission(node);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Prefix, suffix, build methods
    @Override
    public String getGroupProperName(String world, String group) {
        Group g = getGrp(world, group);
        if(g == null) {
            g = getDefaultGroup(world);
            if(g == null) return "";
        }
        return g.getName();
    }
    
    @Override
    public String getUserPrefix(String world, String user) {
        User u = getUsr(world, user);
        if(u == null)
            return "";
        String prefix = u.getPrefix();
        return prefix == null ? "" : prefix;
    }

    @Override
    public String getUserSuffix(String world, String user) {
        User u = getUsr(world, user);
        if(u == null)
            return "";
        String suffix = u.getSuffix();
        return suffix == null ? "" : suffix;
    }


    @Override
    public String getPrimaryGroup(String world, String user) {
        User u = getUsr(world, user);
        if(u != null) {
            LinkedHashSet<Entry> parents = u.getParents();
            if(parents != null && !parents.isEmpty()) {
                for(Entry e : parents) {
                    if(e instanceof Group)
                        return e.getName();
                    else if(e instanceof User) {
                        User p = (User) e;
                        if(p.getWorld().equals("*") && !u.getWorld().equals("*")) {
                            LinkedHashSet<Entry> grandparents = p.getParents();
                            if(grandparents != null && !grandparents.isEmpty()) {
                                for(Entry pe : parents) {
                                    if(pe instanceof Group)
                                        return e.getName();
                                }
                            }
                        }
                        
                    }
                }
            }
        }
        Group def = this.getDefaultGroup(world);
        if(def != null) return def.getName();
        return "Default";
    }
    @Override
    public boolean canUserBuild(String world, String user) {
        User u = getUsr(world, user);
        if(u == null)
            return false;
        return u.canBuild();
    }
    
    @Override
    public String getGroupRawPrefix(String world, String group) {
        world = getParentWorldGroup(world);
        Group g = this.getGrp(world, group);
        if (g == null)
            return "";
        String prefix = g.getRawString("prefix");
        return prefix == null ? "" : prefix;
    }

    @Override
    public String getGroupRawSuffix(String world, String group) {
        world = getParentWorldGroup(world);
        Group g = this.getGrp(world, group);
        if (g == null)
            return "";
        String suffix = g.getRawString("suffix");
        return suffix == null ? "" : suffix;
    }

    @Override
    public boolean canGroupRawBuild(String world, String group) {
        world = getParentWorldGroup(world);
        Group g = this.getGrp(world, group);
        if (g == null)
            return false;
        return g.canBuild();
    }
    
    //Entry methods
    LinkedHashSet<Group> stringToGroups(LinkedHashSet<GroupWorld> raws, String overrideWorld) {
        if(overrideWorld == null) overrideWorld = defaultWorld;
        LinkedHashSet<Group> groupSet = new LinkedHashSet<Group>();
        for (GroupWorld raw : raws) {
            String rawWorld = raw.getWorld();
            if(rawWorld.equals("?") && overrideWorld != null) {
                rawWorld = overrideWorld;
            }
            String world = getParentWorldGroup(rawWorld);
            Group g = this.getGrp(world, raw.getName());
            if(g != null)
                groupSet.add(g);
//            Map<String, Group> gMap = this.worldGroups.get(world);
//            if (gMap != null) {
//                Group g = gMap.get(raw.getName().toLowerCase());
//                if (g != null)
//                    groupSet.add(g);
//            }
        }
        return groupSet;
    }
    
    public boolean deleteUser(String world, String name) {
        User u = getUserObject(world, name);
        if(u == null)
            return false;
        return u.delete();
    }
    
    void delUsr(String world, String name) {
        worldUsers.get(world).remove(name.toLowerCase());        
    }

    
    public boolean deleteGroup(String world, String name) {
        Group g = getGroupObject(world, name);
        if(g == null)
            return false;
        return g.delete();
    }
    
    void delGrp(String world, String name) {
        worldGroups.get(world).remove(name.toLowerCase());        
        if(defaultGroups.get(world) == getGroupObject(world, name))
            defaultGroups.remove(world);
    }
    
    @Override
    public User safeGetUser(String world, String name) throws Exception {
        try {
            loadWorld(world);
        } catch (Exception e) {
            throw new Exception("Error creating user " + name + " in world " + world + " due to storage problems!", e);
        }
        world = getParentWorldUser(world);
        if (userStorageMirrorings.get(world) != null)
            world = userStorageMirrorings.get(world);
        if (this.worldUsers.get(world) == null)
            this.worldUsers.put(world, new HashMap<String, User>());
        if (this.worldUsers.get(world).get(name.toLowerCase()) == null)
            this.worldUsers.get(world).put(name.toLowerCase(), new User(this, getUserStorage(world), name, world, true));
        return this.worldUsers.get(world).get(name.toLowerCase());
    }

    @Override
    public Group safeGetGroup(String world, String name) throws Exception {
        if(!world.equals("?")) {
            try {
                loadWorld(world);
            } catch (Exception e) {
                throw new Exception("Error creating group " + name + " in world " + world + " due to storage problems!", e);
            }
            world = getParentWorldGroup(world);
            if (groupStorageMirrorings.get(world) != null)
                world = groupStorageMirrorings.get(world);
        }
        if (this.worldGroups.get(world) == null)
            this.worldGroups.put(world, new HashMap<String, Group>());
        if (this.worldGroups.get(world).get(name.toLowerCase()) == null)
            this.worldGroups.get(world).put(name.toLowerCase(), new Group(this, getGroupStorage(world), name, world, !world.equals("?")));
        return this.worldGroups.get(world).get(name.toLowerCase());
    }

    @Override
    public Group getDefaultGroup(String world) {
        world = getParentWorldGroup(world);
        return this.defaultGroups.get(world);
    }

    @Override
    public Collection<User> getUsers(String world) {
        world = getParentWorldUser(world);
        if (worldUsers.get(world) == null)
            return null;
        return worldUsers.get(world).values();
    }

    @Override
    public Collection<Group> getGroups(String world) {
        world = getParentWorldGroup(world);
        if (worldGroups.get(world) == null)
            return null;
        return worldGroups.get(world).values();
    }

    @Override
    public User getUserObject(String world, String name) {
        world = getParentWorldUser(world);
        try {
            loadWorld(world);
        } catch (Exception e) {
            return null;
        }
        if (worldUsers.get(world) == null)
            return world.equals("*") ? null : getUserObject("*", name);
        return worldUsers.get(world).get(name.toLowerCase());
    }

    @Override
    public Group getGroupObject(String world, String name) {
        world = getParentWorldGroup(world);
        try {
            loadWorld(world);
        } catch (Exception e) {
            return null;
        }
        if (worldGroups.get(world) == null)
            return world.equals("*") ? null : getGroupObject("*", name);
        return worldGroups.get(world).get(name.toLowerCase());
    }

    User getUsr(String world, String name) {
//        world = getParentWorldUser(world);
        try {
            User u = safeGetUser(world, name);
            return u;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
//        if (worldUsers.get(world) == null)
//            return world.equals("*") ? null : getUserObject("*", name);
//        return worldUsers.get(world).get(name.toLowerCase());
    }

    Group getGrp(String world, String name) {
//        world = getParentWorldGroup(world);
        try {
            Group g = safeGetGroup(world, name);
            return g;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
//        if (worldGroups.get(world) == null)
//            return world.equals("*") ? null : getGroupObject("*", name);
//        return worldGroups.get(world).get(name.toLowerCase());
    }
    //Parent-related methods
    
    @Override
    public Set<String> getTracks(String world) {
        GroupStorage gs = getGroupStorage(world);
        if(gs == null)
            return null;
        return gs.getTracks();
    }
    @Override
    public boolean inGroup(String world, String name, String groupWorld, String group) {
        world = getParentWorldUser(world);
        groupWorld = getParentWorldGroup(groupWorld);
        try {
            return safeGetUser(world, name).inGroup(groupWorld, group);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean inSingleGroup(String world, String name, String groupWorld, String group) {
        world = getParentWorldUser(world);
        groupWorld = getParentWorldGroup(groupWorld);
        User u = this.getUsr(world, name);
        if (u == null) {
            if (!world.equalsIgnoreCase(groupWorld))
                return false;
            Group g = this.getDefaultGroup(world);
            if (g != null && g.getWorld().equalsIgnoreCase(groupWorld) && g.getName().equalsIgnoreCase(group))
                return true;
            return false;
        }
        return u.getParents().contains(new GroupWorld(groupWorld, group));
    }

    @Override
    public String[] getGroups(String world, String name) {
        world = getParentWorldGroup(world);
        Set<Entry> parents;
        User u = this.getUsr(world, name);
        if (u == null)
            return new String[0];
        parents = u.getAncestors();
        List<String> groupList = new LinkedList<String>();
        for (Entry e : parents) {
            if (e instanceof Group) {
                Group g = (Group) e;
                if (g.getWorld().equalsIgnoreCase(world))
                    groupList.add(g.getName());
            }
        }
        return groupList.toArray(new String[0]);
    }

    @Override
    public boolean inGroup(String world, String name, String group) {
        return inGroup(world, name, world, group);
    }

    @Override
    public boolean inSingleGroup(String world, String name, String group) {
        return inSingleGroup(world, name, world, group);
    }

    @Override
    public Map<String, Set<String>> getAllGroups(String world, String name) {
        Map<String, Set<String>> groupMap = new HashMap<String, Set<String>>();
        world = getParentWorldGroup(world);
        Set<Entry> parents;
        User u = this.getUsr(world, name);
        if (u == null)
            return groupMap;
        parents = u.getAncestors();
        for (Entry e : parents) {
            if (e instanceof Group) {
                if(groupMap.get(e.getWorld()) == null) groupMap.put(e.getWorld(), new HashSet<String>());
                groupMap.get(e.getWorld()).add(e.getName());
            }
        }
        return groupMap;
    }

    //Weight-related methods
    @Override
    public int compareWeights(String world, String first, String second) {
        return compareWeights(world, first, world, second);
    }

    @Override
    public int compareWeights(String firstWorld, String first, String secondWorld, String second) {
        User firstUser = this.getUsr(firstWorld, first);
        User secondUser = this.getUsr(secondWorld, second);
        if (firstUser == null) {
            if (secondUser == null)
                return 0;
            else
                return -1;
        }
        if (secondUser == null)
            return 1;
        return Integer.signum(((Integer) firstUser.getWeight()).compareTo(secondUser.getWeight()));
    }
    
    //Data-related methods
    @Override
    public String getRawInfoString(String world, String entryName, String path, boolean isGroup) {
        Entry e = isGroup ? getGrp(world, entryName) : getUsr(world, entryName);
        if(e == null) return null;
        return e.getRawString(path);
    }

    @Override
    public Integer getRawInfoInteger(String world, String entryName, String path, boolean isGroup) {
        Entry e = isGroup ? getGrp(world, entryName) : getUsr(world, entryName);
        if(e == null) return null;
        return e.getRawInt(path);
    }

    @Override
    public Double getRawInfoDouble(String world, String entryName, String path, boolean isGroup) {
        Entry e = isGroup ? getGrp(world, entryName) : getUsr(world, entryName);
        if(e == null) return null;
        return e.getRawDouble(path);
    }

    @Override
    public Boolean getRawInfoBoolean(String world, String entryName, String path, boolean isGroup) {
        Entry e = isGroup ? getGrp(world, entryName) : getUsr(world, entryName);
        if(e == null) return null;
        return e.getRawBool(path);
    }

    @Override
    public String getInfoString(String world, String entryName, String path, boolean isGroup) {
        Entry e = isGroup ? getGrp(world, entryName) : getUsr(world, entryName);
        if(e == null) return null;
        return e.getString(path);
    }

    @Override
    public String getInfoString(String world, String entryName, String path, boolean isGroup, Comparator<String> comparator) {
        Entry e = isGroup ? getGrp(world, entryName) : getUsr(world, entryName);
        if(e == null) return null;
        return e.getString(path, comparator);
    }

    @Override
    public Integer getInfoInteger(String world, String entryName, String path, boolean isGroup) {
        Entry e = isGroup ? getGrp(world, entryName) : getUsr(world, entryName);
        if(e == null) return null;
        return e.getInt(path);
    }

    @Override
    public Integer getInfoInteger(String world, String entryName, String path, boolean isGroup, Comparator<Integer> comparator) {
        Entry e = isGroup ? getGrp(world, entryName) : getUsr(world, entryName);
        if(e == null) return null;
        return e.getInt(path, comparator);
    }

    @Override
    public Double getInfoDouble(String world, String entryName, String path, boolean isGroup) {
        Entry e = isGroup ? getGrp(world, entryName) : getUsr(world, entryName);
        if(e == null) return null;
        return e.getDouble(path);
    }

    @Override
    public Double getInfoDouble(String world, String entryName, String path, boolean isGroup, Comparator<Double> comparator) {
        Entry e = isGroup ? getGrp(world, entryName) : getUsr(world, entryName);
        if(e == null) return null;
        return e.getDouble(path, comparator);
    }

    @Override
    public Boolean getInfoBoolean(String world, String entryName, String path, boolean isGroup) {
        Entry e = isGroup ? getGrp(world, entryName) : getUsr(world, entryName);
        if(e == null) return null;
        return e.getBool(path);
    }

    @Override
    public Boolean getInfoBoolean(String world, String entryName, String path, boolean isGroup, Comparator<Boolean> comparator) {
        Entry e = isGroup ? getGrp(world, entryName) : getUsr(world, entryName);
        if(e == null) return null;
        return e.getBool(path, comparator);
    }

    @Override
    public void addUserInfo(String world, String name, String path, Object data) {
        User u = this.getUsr(world, name);
        if(u == null)
            return;
        u.setData(path, data);
    }

    @Override
    public void removeUserInfo(String world, String name, String path) {
        User u = this.getUsr(world, name);
        if(u == null)
            return;
        u.removeData(path);
    }
    
    @Override
    public void addGroupInfo(String world, String group, String path, Object data) {
        Group g = this.getGrp(world, group);
        if (g == null)
            return;
        g.setData(path, data);
    }

    @Override
    public void removeGroupInfo(String world, String group, String path) {
        Group g = this.getGrp(world, group);
        if (g == null)
            return;
        g.removeData(path);
    }
    
    //Legacy methods
    @Override
    public String getGroupPermissionString(String world, String group, String path) {
        return getRawInfoString(world, group, path, true);
    }

    @Override
    public int getGroupPermissionInteger(String world, String group, String path) {
        Integer value = getRawInfoInteger(world, group, path, true);
        return value == null ? -1 : value;
    }

    @Override
    public boolean getGroupPermissionBoolean(String world, String group, String path) {
        Boolean value = getRawInfoBoolean(world, group, path, true);
        return value == null ? false : value;
    }

    @Override
    public double getGroupPermissionDouble(String world, String group, String path) {
        Double value = getRawInfoDouble(world, group, path, true);
        return value == null ? -1.0d : value;
    }

    @Override
    public String getUserPermissionString(String world, String name, String path) {
        return getRawInfoString(world, name, path, false);
    }

    @Override
    public int getUserPermissionInteger(String world, String name, String path) {
        Integer value = getRawInfoInteger(world, name, path, false);
        return value == null ? -1 : value;
    }

    @Override
    public boolean getUserPermissionBoolean(String world, String name, String path) {
        Boolean value = getRawInfoBoolean(world, name, path, false);
        return value == null ? false : value;
    }

    @Override
    public double getUserPermissionDouble(String world, String name, String path) {
        Double value = getRawInfoDouble(world, name, path, false);
        return value == null ? -1.0d : value;
    }

    @Override
    public String getPermissionString(String world, String name, String path) {
        return getInfoString(world, name, path, false);
    }

    @Override
    public int getPermissionInteger(String world, String name, String path) {
        Integer value = getInfoInteger(world, name, path, false);
        return value == null ? -1 : value;
    }

    @Override
    public boolean getPermissionBoolean(String world, String name, String path) {
        Boolean value = getInfoBoolean(world, name, path, false);
        return value == null ? false : value;
    }

    @Override
    public double getPermissionDouble(String world, String name, String path) {
        Double value = getInfoDouble(world, name, path, false);
        return value == null ? -1.0d : value;
    }

    @Override
    public String getGroup(String world, String user) {
        return getPrimaryGroup(world, user);
    }

    @Override
    public String getGroupPrefix(String world, String group) {
        return getGroupRawPrefix(world, group);
    }

    @Override
    public String getGroupSuffix(String world, String group) {
        return getGroupRawSuffix(world, group);
    }

    @Override
    public boolean canGroupBuild(String world, String group) {
        return canGroupRawBuild(world, group);
    }

}
