package com.nijikokun.bukkit.Permissions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
//import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import com.nijiko.MessageHelper;
import com.nijiko.data.GroupWorld;
import com.nijiko.data.StorageFactory;
import com.nijiko.data.YamlCreator;
import com.nijiko.permissions.Entry;
import com.nijiko.permissions.Group;
import com.nijiko.permissions.ModularControl;
import com.nijiko.permissions.PermissionHandler;
import com.nijiko.permissions.User;

/**
 * Permissions 3.x Copyright (C) 2011 Matt 'The Yeti' Burnett <admin@theyeticave.net> Original Credit & Copyright (C) 2010 Nijikokun <nijikokun@gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Permissions Public License as published by the Free Software Foundation, either version 2 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Permissions Public License for more details.
 * 
 * You should have received a copy of the GNU Permissions Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

public class Permissions extends JavaPlugin {

    protected static final Logger log = Logger.getLogger("Minecraft.Permissions");
    public static Plugin instance;
    // private Configuration storageConfig;
    @Deprecated
    public static final String name = "Permissions";
    public static final String version = "3.1.7";
    public static final String codename = "Yeti";

    public Listener buildListener = new Listener(this);

    /**
     * Controller for permissions and security. Use getHandler() instead.
     */
    @Deprecated
    public static PermissionHandler Security;

    // private PermissionHandler controller;

    private String defaultWorld = "";
    private static final boolean autoComplete = true;
    private final YamlCreator yamlC;
    private int dist = 10;
    private final PrWorldListener wListener = new PrWorldListener();

    private boolean errorFlag = false;

    // protected String pluginInternalName = "Permissions";

    public Permissions() {
        yamlC = new YamlCreator();
        StorageFactory.registerDefaultCreator(yamlC);
        StorageFactory.registerCreator("YAML", yamlC);
    }

    @Override
    public void onLoad() {
        instance = this;
        Properties prop = new Properties();
        FileInputStream in = null;
        getDataFolder().mkdirs();
        try {
            in = new FileInputStream(new File("server.properties"));
            prop.load(in);
            defaultWorld = prop.getProperty("level-name");
        } catch (IOException e) {
            System.err.println("[Permissions] Unable to read default world's name from server.properties.");
            e.printStackTrace();
            defaultWorld = "world";
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // PropertyHandler server = new PropertyHandler("server.properties");
        // defaultWorld = server.getString("level-name");

        File storageOpt = new File("plugins" + File.separator + "Permissions" + File.separator, "storageconfig.yml");
        storageOpt.getParentFile().mkdirs();
        if (!storageOpt.exists()) {
            try {
                System.out.println("[Permissions] Creating storageconfig.yml.");
                if (!storageOpt.createNewFile()) {
                    disable("[Permissions] Unable to create storageconfig.yml!");
                }
            } catch (IOException e) {
                e.printStackTrace();
                disable("[Permissions] storageconfig.yml could not be created.");
                return;
            }
        }
        if (!storageOpt.isFile() || !storageOpt.canRead()) {
            disable("[Permissions] storageconfig.yml is not a file or is not readable.");
            return;
        }

        Configuration storageConfig = new Configuration(storageOpt);
        storageConfig.load();
        // this.storageConfig = storageConfig;

        // Setup Permission
        setupPermissions(storageConfig);

        // Enabled
        log.info("[Permissions] (" + codename + ") was initialized.");
    }

    @Override
    public void onDisable() {
        if (!errorFlag) {
            log.info("[Permissions] (" + codename + ") saving data...");
            this.getHandler().closeAll();
            // getHandler() = null;
            log.info("[Permissions] (" + codename + ") saved all data.");
        }
        this.getServer().getScheduler().cancelTasks(this);
        log.info("[Permissions] (" + codename + ") disabled successfully.");
        return;
    }

    private void disable(String error) {
        if (error != null)
            log.severe(error);
        log.severe("[Permissions] Shutting down Permissions due to error(s).");
        this.errorFlag = true;
    }

    /**
     * Returns the PermissionHandler instance.<br />
     * <br />
     * <blockquote>
     * 
     * <pre>
     * Permissions.getHandler()
     * </pre>
     * 
     * </blockquote>
     * 
     * @return PermissionHandler
     */
    public PermissionHandler getHandler() {
        return Permissions.Security;
    }

    public void setupPermissions(Configuration storageConfig) {
//        System.out.println("Setting up Permissions...");
        try {
            Security = new ModularControl(storageConfig);
            getHandler().setDefaultWorld(defaultWorld);
            getHandler().load();
//            System.out.println(getServer().getWorlds());
            for (World w : getServer().getWorlds()) {
                getHandler().loadWorld(w.getName());
            }
        } catch (Throwable t) {
            t.printStackTrace();
            disable("[Permissions] Unable to load permission data.");
            return;
        }
        // getServer().getServicesManager().register(PermissionHandler.class,
        // getHandler(), this, ServicePriority.Normal);
    }

    @Override
    public void onEnable() {
        if (errorFlag) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        StorageFactory.registerDefaultCreator(yamlC);
        StorageFactory.registerCreator("YAML", yamlC);

        PluginDescriptionFile description = getDescription();
        // Enabled
        log.info("[" + description.getName() + "] version [" + description.getVersion() + "] (" + codename + ")  loaded");

        this.getServer().getPluginManager().registerEvent(Event.Type.BLOCK_PLACE, buildListener, Priority.High, this);
        this.getServer().getPluginManager().registerEvent(Event.Type.BLOCK_BREAK, buildListener, Priority.High, this);
        this.getServer().getPluginManager().registerEvent(Event.Type.WORLD_LOAD, wListener, Priority.Monitor, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        Player player = null;
        // String commandName = command.getName().toLowerCase();
        PluginDescriptionFile pdfFile = this.getDescription();
        MessageHelper msg = new MessageHelper(sender);
        if (sender instanceof Player) {
            player = (Player) sender;
        }

        if (Security == null) {
            msg.send("&4[Permissions] Permissions was unable to load data during server load.");
            msg.send("&4[Permissions] Please post the the portion of your server log since the server start/reload in the forums thread.");
            return true;
        }
        if (args.length == 0) {
            if (player != null) {
                msg.send("&7-------[ &fPermissions&7 ]-------");
                msg.send("&7Currently running version: &f[" + pdfFile.getVersion() + "] (" + codename + ")");

                if (getHandler().has(player.getWorld().getName(), player.getName(), "permissions.reload")) {
                    msg.send("&7Reload with: &f/permissions &a-reload &e<world>");
                    msg.send("&fLeave &e<world> blank to reload default world.");
                }

                msg.send("&7-------[ &fPermissions&7 ]-------");
                return true;
            } else {
                sender.sendMessage("[" + pdfFile.getName() + "] version [" + pdfFile.getVersion() + "] (" + codename + ")  loaded");
                return true;
            }
        }

        if (args[0].equalsIgnoreCase("-reload")) {
            String world;
            if (args.length > 1) {
                StringBuilder tempWorld = new StringBuilder();
                int val = extractQuoted(args, 1, tempWorld);
                switch (val) {
                case -1:
                    msg.send("&4[Permissions] Argument index error.");
                    return true;
                case -2:
                    msg.send("&4[Permissions] No ending quote found.");
                    return true;
                }
                world = tempWorld.toString();
            } else
                world = "";
            return reload(sender, world);
        } else if (args[0].equalsIgnoreCase("-load")) {
            String world;
            if (args.length > 1) {
                StringBuilder tempWorld = new StringBuilder();
                int val = extractQuoted(args, 1, tempWorld);
                switch (val) {
                case -1:
                    msg.send("&4[Permissions] Argument index error.");
                    return true;
                case -2:
                    msg.send("&4[Permissions] No ending quote found.");
                    return true;
                }
                world = tempWorld.toString();
            } else
                world = "";

            if (player != null && !getHandler().has(player, "permissions.load." + world)) {
                msg.send("&4[Permissions] You do not have permissions to use this command.");
                return true;
            }

            try {
                getHandler().forceLoadWorld(world);
            } catch (Exception e) {
                msg.send("&4[Permissions] Error occured while loading world.");
                e.printStackTrace();
                return true;
            }
            msg.send("&7[Permissions] World loaded.");
            return true;
        } else if (args[0].equalsIgnoreCase("-list")) {
            if (args.length > 1) {
                if (args[1].equalsIgnoreCase("worlds")) {
                    if (player != null && !getHandler().has(player, "permissions.list.worlds")) {
                        msg.send("&4[Permissions] You do not have permissions to use this command.");
                        return true;
                    }
                    Set<String> worlds = getHandler().getWorlds();
                    StringBuilder text = new StringBuilder();
                    if (worlds.isEmpty()) {
                        text.append("&4[Permissions] No worlds loaded.");
                    } else {
                        text.append("&a[Permissions] Loaded worlds: &b");
                        for (String world : worlds) {
                            text.append(world).append(", ");
                        }
                        text.delete(text.length() - 2, text.length());
                    }
                    msg.send(text.toString());
                    return true;
                }
                if (args.length > 2) {
                    StringBuilder tempWorld = new StringBuilder();
                    int val = extractQuoted(args, 2, tempWorld);
                    switch (val) {
                    case -1:
                        msg.send("&4[Permissions] Argument index error.");
                        return true;
                    case -2:
                        msg.send("&4[Permissions] No ending quote found.");
                        return true;
                    }
                    String world = tempWorld.toString();
                    if (args[1].equalsIgnoreCase("users")) {
                        if (player != null && !getHandler().has(player, "permissions.list.users")) {
                            msg.send("&4[Permissions] You do not have permissions to use this command.");
                            return true;
                        }
                        Collection<User> users = getHandler().getUsers(world);
                        msg.send(listEntries(users, "Users"));
                        return true;
                    } else if (args[1].equalsIgnoreCase("groups")) {
                        if (player != null && !getHandler().has(player, "permissions.list.groups")) {
                            msg.send("&4[Permissions] You do not have permissions to use this command.");
                            return true;
                        }
                        Collection<Group> groups = getHandler().getGroups(world);
                        msg.send(listEntries(groups, "Groups"));
                        return true;
                    } else if (args[1].equalsIgnoreCase("tracks")) {
                        if (player != null && !getHandler().has(player, "permissions.list.tracks")) {
                            msg.send("&4[Permissions] You do not have permissions to use this command.");
                            return true;
                        }
                        Set<String> tracks = getHandler().getTracks(world);
                        StringBuilder b = new StringBuilder();
                        b.append("&7[Permissions] &bTracks in world &c").append(world).append("&b: &c");
                        for(String track : tracks ) {
                            if(track == null)
                                track = "<Default Track>";
                            b.append(track).append(", ");
                        }
                        int len = b.length();
                        b.delete(len - 2, len);
                        msg.send(b.toString());
                        return true;
                    }
                }
            }
            msg.send("&7[Permissions] Syntax: ");
            msg.send("&b/permissions &a-list &eworlds.");
            msg.send("&b/permissions &a-list &e[users|groups] &d<world>.");
            return true;
        }

        // This part is for selecting the appropriate entry (/pr (g:)<entryname>
        // (w:<world>) ...)
        int currentArg = 0;
        boolean isGroup = args[0].startsWith("g:");
        String name = isGroup ? args[0].substring(2) : args[0];
        currentArg++;
        String world = sender instanceof Player ? ((Player) sender).getWorld().getName() : null;
        if (args.length > currentArg && args[currentArg].startsWith("w:")) {
            StringBuilder tempWorld = new StringBuilder();
            String[] tempArgs = new String[args.length];
            System.arraycopy(args, 0, tempArgs, 0, args.length); // XXX: Temp
            // solution for
            // w: prefix
            tempArgs[currentArg] = tempArgs[currentArg].substring(2);
            currentArg = extractQuoted(tempArgs, currentArg, tempWorld);
            switch (currentArg) {
            case -1:
                msg.send("&4[Permissions] Argument index error.");
                return true;
            case -2:
                msg.send("&4[Permissions] No ending quote found.");
                return true;
            }
            world = tempWorld.toString();
        }
        if (world == null) {
            msg.send("&4[Permissions] No world specified. Defaulting to default world.");
            world = defaultWorld;
        }

        Entry entry = isGroup ? getHandler().getGroupObject(world, name) : getHandler().getUserObject(world, name);
        // Note that entry may be null if the user/group doesn't exist
        if (args.length > currentArg) {
            if (args[currentArg].equalsIgnoreCase("create")) {
                if (player != null && !getHandler().has(player, "permissions.create")) {
                    msg.send("&4[Permissions] You do not have permissions to use this command.");
                    return true;
                }
                if (entry != null) {
                    msg.send("&4[Permissions] User/Group already exists.");
                    return true;
                }
                try {
                    entry = isGroup ? getHandler().safeGetGroup(world, name) : getHandler().safeGetUser(world, name);
                } catch (Exception e) {
                    e.printStackTrace();
                    msg.send("&4[Permissions] Error creating user/group.");
                    return true;
                }
                msg.send("&7[Permissions] User/Group created.");
                return true;
            } else if (entry == null) {
                msg.send("&4[Permissions] User/Group does not exist.");

                if (autoComplete) {
                    Set<String> matches = null;
                    if(!isGroup) {
                        matches = getNames(getHandler().getUsers(world));
                        Player[] online = getServer().getOnlinePlayers();
                        for (Player p : online) {
                            matches.add(p.getName());
                        }
                    } else {
                        matches = getNames(getHandler().getGroups(world));
                    }
                    String closest = getClosest(name, matches, dist);

                    if (closest != null) {
                        msg.send("&7[Permissions]&b Using closest match &4" + closest + "&b.");
                        name = closest;
                        try {
                            entry = isGroup ? getHandler().safeGetGroup(world, name) : getHandler().safeGetUser(world, name);
                        } catch (Exception e) {
                            e.printStackTrace();
                            msg.send("&4[Permissions] Error creating user/group.");
                            return true;
                        }
//                        if (entry == null) {
//                            msg.send("&4[Permissions] Closest user/group does not exist.");
//                            return true;
//                        }
                    } else {
                        return true;
                    }
                }

            }
            if (args[currentArg].equalsIgnoreCase("delete")) {
                if (player != null && !getHandler().has(player, "permissions.delete")) {
                    msg.send("&4[Permissions] You do not have permissions to use this command.");
                    return true;
                }
                String text = entry.delete() ? "&7[Permissions] User/Group deleted." : "&4[Permissions] Deletion failed.";
                msg.send(text);
                return true;
            } else if (args[currentArg].equalsIgnoreCase("has")) {
                currentArg++;
                if (player != null && !getHandler().has(player, "permissions.has")) {
                    msg.send("&4[Permissions] You do not have permissions to use this command.");
                    return true;
                }
                if (args.length > currentArg) {
                    String permission = args[currentArg];
                    boolean has = entry.hasPermission(permission);
                    msg.send("&7[Permissions]&b User/Group " + (has ? "has" : "does not have") + " that permission.");
                    return true;
                }
                msg.send("&7[Permissions] Syntax: /pr (g:)<target> (w:<world>) has <permission>");
                return true;
            } else if (args[currentArg].equalsIgnoreCase("perms")) {
                currentArg++;
                if (args.length > currentArg) {
                    if (args[currentArg].equalsIgnoreCase("list")) {
                        if (player != null && !getHandler().has(player, "permissions.perms.list")) {
                            msg.send("&4[Permissions] You do not have permissions to use this command.");
                            return true;
                        }
                        Set<String> perms = entry.getPermissions();
                        String text = "";
                        if (perms == null || perms.isEmpty()) {
                            text = "&4[Permissions] User/Group has no non-inherited permissions.";
                        } else {
                            StringBuilder temp = new StringBuilder("&7[Permissions]&b Permissions: &c");
                            for (String perm : perms) {
                                temp.append(perm).append("&b,&c ");
                            }
                            text = temp.substring(0, temp.length() - 6);
                        }
                        msg.send(text);
                        return true;
                    } else if (args[currentArg].equalsIgnoreCase("listall")) {
                        if (player != null && !getHandler().has(player, "permissions.perms.listall")) {
                            msg.send("&4[Permissions] You do not have permissions to use this command.");
                            return true;
                        }
                        Set<String> perms = entry.getAllPermissions();
                        String text = "&7[Permissions]&b Permissions: &c";
                        if (perms == null || perms.isEmpty()) {
                            text = "&4[Permissions] User/Group has no permissions.";
                        } else {
                            StringBuilder temp = new StringBuilder("&7[Permissions]&b Permissions: &c");
                            for (String perm : perms) {
                                temp.append(perm).append("&b,&c ");
                            }
                            text = temp.substring(0, temp.length() - 6);
                        }
                        msg.send(text);
                        return true;
                    } else if (args[currentArg].equalsIgnoreCase("add") || args[currentArg].equalsIgnoreCase("remove")) {
                        boolean add = args[currentArg].equalsIgnoreCase("add");

                        String permNode = add ? "permissions.perms.add" : "permissions.perms.remove";
                        if (player != null && !getHandler().has(player, permNode)) {
                            msg.send("&4[Permissions] You do not have permissions to use this command.");
                            return true;
                        }

                        currentArg++;
                        String text = add ? "&7[Permissions]&b Permission added successfully." : "&7[Permissions]&b Permission removed successfully.";
                        if (args.length > currentArg) {
                            String permission = args[currentArg];
                            Set<String> perms = entry.getPermissions();
                            if (!(perms.contains(permission) ^ add))
                                text = "&4[Permissions] User/Group already has that permission.";
                            else
                                entry.setPermission(permission, add);
                        }
                        msg.send(text);
                        return true;
                    }
                }
                msg.send("&7[Permissions] Syntax: ");
                msg.send("&b/permissions &a(g:)<target> (w:<world>) perms list");
                msg.send("&b/permissions &a(g:)<target> (w:<world>) perms [add|remove] <node>");
                return true;
            } else if (args[currentArg].equalsIgnoreCase("parents")) {
                currentArg++;
                if (args.length > currentArg) {
                    if (args[currentArg].equalsIgnoreCase("list")) {
                        if (player != null && !getHandler().has(player, "permissions.parents.list")) {
                            msg.send("&4[Permissions] You do not have permissions to use this command.");
                            return true;
                        }
                        // LinkedHashSet<GroupWorld> parents =
                        // entry.getRawParents();
                        LinkedHashSet<Entry> parents = entry.getParents();
                        String text = "&7[Permissions]&b Parents: &c";
                        if (parents == null || parents.isEmpty()) {
                            text = "&4[Permissions] User/Group has no parents.";
                        } else {
                            StringBuilder temp = new StringBuilder("&7[Permissions]&b Parents: &c");
                            for (Entry parent : parents) {
                                temp.append(parent.toString()).append("&b,&c ");
                            }
                            text = temp.substring(0, temp.length() - 6);
                        }
                        msg.send(text);
                        return true;
                    } else if (args[currentArg].equalsIgnoreCase("listall")) {
                        if (player != null && !getHandler().has(player, "permissions.parents.listall")) {
                            msg.send("&4[Permissions] You do not have permissions to use this command.");
                            return true;
                        }
                        // LinkedHashSet<GroupWorld> parents =
                        // entry.getRawParents();
                        LinkedHashSet<Entry> parents = entry.getAncestors();
                        String text = "&7[Permissions]&b All parents: &c";
                        if (parents == null || parents.isEmpty()) {
                            text = "&4[Permissions] User/Group has no parents.";
                        } else {
                            StringBuilder temp = new StringBuilder("&7[Permissions]&b Parents: &c");
                            for (Entry parent : parents) {
                                temp.append(parent.toString()).append("&b,&c ");
                            }
                            text = temp.substring(0, temp.length() - 6);
                        }
                        msg.send(text);
                        return true;
                    } else if (args[currentArg].equalsIgnoreCase("add") || args[currentArg].equalsIgnoreCase("remove")) {
                        boolean add = args[currentArg].equalsIgnoreCase("add");
                        String permNode = add ? "permissions.parents.add" : "permissions.parents.remove";
                        if (player != null && !getHandler().has(player, permNode)) {
                            msg.send("&4[Permissions] You do not have permissions to use this command.");
                            return true;
                        }
                        currentArg++;
                        String text = add ? "&7[Permissions]&b Parent added successfully." : "&7[Permissions]&b Parent removed successfully.";
                        if (args.length > currentArg) {
                            String parentName = args[currentArg];
                            String parentWorld = world;
                            if (args.length > (++currentArg)) {
                                StringBuilder tempWorld = new StringBuilder();
                                currentArg = extractQuoted(args, currentArg, tempWorld);
                                switch (currentArg) {
                                case -1:
                                    msg.send("&4[Permissions] Argument index error.");
                                    return true;
                                case -2:
                                    msg.send("&4[Permissions] No ending quote found.");
                                    return true;
                                }
                                parentWorld = tempWorld.toString();
                            }
                            LinkedHashSet<GroupWorld> parents = entry.getRawParents();
                            if (add && parents.contains(new GroupWorld(parentWorld, parentName)))
                                text = "&4[Permissions] User/Group already has that parent.";
                            if (!add && !parents.contains(new GroupWorld(parentWorld, parentName)))
                                text = "&4[Permissions] User/Group does not have such a parent.";
                            else {
                                Group parent = getHandler().getGroupObject(parentWorld, parentName);
                                if (parent == null) {
                                    text = "&4[Permissions] No such group exists.";
                                } else {
                                    if (add)
                                        entry.addParent(parent);
                                    else
                                        entry.removeParent(parent);
                                }
                            }
                        }
                        msg.send(text);
                        return true;
                    }
                }
                msg.send("&7[Permissions] Syntax: ");
                msg.send("&b/permissions &a(g:)<target> (w:<world>) parents list");
                msg.send("&b/permissions &a(g:)<target> (w:<world>) parents [add|remove] <parentname> (parentworld)");
                return true;
            } else if (args[currentArg].equalsIgnoreCase("info")) {
                currentArg++;
                if (args.length > currentArg) {
                    String choice = args[currentArg];
                    if (choice.equalsIgnoreCase("get")) {
                        currentArg++;
                        if (player != null && !getHandler().has(player, "permissions.info.get")) {
                            msg.send("&4[Permissions] You do not have permissions to use this command.");
                            return true;
                        }
                        if (args.length > currentArg) {
                            String path = args[currentArg];
                            msg.send("&7[Permissions]&b " + entry.getString(path));
                            return true;
                        }
                    } else if (choice.equalsIgnoreCase("set")) {
                        currentArg++;
                        if (player != null && !getHandler().has(player, "permissions.info.set")) {
                            msg.send("&4[Permissions] You do not have permissions to use this command.");
                            return true;
                        }
                        if (args.length > currentArg) {
                            String path = args[currentArg];
                            currentArg++;
                            if (args.length > currentArg) {
                                String newValueString = args[currentArg];
                                Object newValue;
                                String type = "";
                                try {
                                    if (newValueString.startsWith("b:")) {
                                        newValue = Boolean.valueOf(newValueString.substring(2));
                                        type = "Boolean";
                                    } else if (newValueString.startsWith("d:")) {
                                        newValue = Double.valueOf(newValueString.substring(2));
                                        type = "Double";
                                    } else if (newValueString.startsWith("i:")) {
                                        newValue = Integer.valueOf(newValueString.substring(2));
                                        type = "Integer";
                                    } else {
                                        newValue = newValueString;
                                        type = "String";
                                    }
                                } catch (NumberFormatException e) {
                                    msg.send("&4[Permissions]&b Error encountered when parsing value.");
                                    return true;
                                }
                                entry.setData(path, newValue);
                                msg.send("&7[Permissions]&b &a" + path + "&b set to &a" + type + " &c" + newValue.toString());
                                return true;
                            }
                        }
                    } else if (choice.equalsIgnoreCase("remove")) {
                        currentArg++;
                        if (player != null && !getHandler().has(player, "permissions.info.remove")) {
                            msg.send("&4[Permissions] You do not have permissions to use this command.");
                            return true;
                        }
                        if (args.length > currentArg) {
                            String path = args[currentArg];
                            entry.removeData(path);
                            msg.send("&7[Permissions]&b &a" + path + "&b cleared.");
                            return true;
                        }
                    }
                }
                msg.send("&7[Permissions] Syntax: ");
                msg.send("&b/permissions &a(g:)<target> (w:<world>) info get <path>");
                msg.send("&b/permissions &a(g:)<target> (w:<world>) info set <path> (i:|d:|b:)<data>");
                return true;
            } else if (args[currentArg].equalsIgnoreCase("dumpcache")) {
                if (player != null && !getHandler().has(player, "permissions.debug.dumpcache")) {
                    msg.send("&4[Permissions] You do not have permissions to use this command.");
                    return true;
                }
                msg.send("&7[Permissions]&b Cache: &f");
                msg.send(entry.getCache().toString());
                return true;
            } else if (entry instanceof User) {
                User user = (User) entry;
                if (args[currentArg].equalsIgnoreCase("promote") || args[currentArg].equalsIgnoreCase("demote")) {
                    boolean isPromote = args[currentArg].equalsIgnoreCase("promote");
                    if (player != null && !getHandler().has(player, isPromote ? "permissions.promote" : "permissions.demote")) {
                        msg.send("&4[Permissions] You do not have permissions to use this command.");
                        return true;
                    }
                    currentArg++;

                    String trackName = null;
                    String parentName = null;
                    String parentWorld = world;
                    if (args.length > currentArg) {
                        String temp = args[currentArg];
                        if (temp.startsWith("t:"))
                            trackName = temp.substring(2);
                        else
                            parentName = temp;
                        currentArg++;
                    }

                    Set<String> tracks = getHandler().getTracks(world);
                    if (tracks == null || tracks.isEmpty()) {
                        msg.send("&4[Permissions] No tracks in specified world.");
                        return true;
                    }
                    if (!tracks.contains(trackName)) {
                        msg.send("&4[Permissions] Specified track does not exist.");
                        return true;
                    }

                    Group parent = null;
                    if (args.length > currentArg) {
                        if(parentName == null) {
                            parentName = args[currentArg];
                            currentArg++;
                            if(args.length > currentArg) {
                                parentWorld = args[currentArg];
                            }
                        }
                        else {
                            parentWorld = args[currentArg];
                            currentArg++;
                        }
                        parent = getHandler().getGroupObject(parentWorld, parentName);
                    } else {
                        parent = user.getPrimaryGroup();
                    }

                    if (parent == null) {
                        msg.send("&4[Permissions] Specified parent group does not exist.");
                        return true;
                    }
                    
                    if(!user.getParents(null).contains(parent)) {
                        msg.send("&4[Permissions] User is not a child of the specified parent group.");
                        return true;
                    }

                    boolean result = false;
                    if (isPromote)
                        result = user.promote(parent, trackName);
                    else
                        result = user.demote(parent, trackName);
                    
                    if(result)
                        msg.send("&7[Permissions] Promotion/demotion successful.");
                    else
                        msg.send("&7[Permissions] Promotion/demotion failed.");                        
                        
                    return true;
                }

                msg.send("&7[Permissions] Syntax: /permissions <target> (w:<world>) [promote|demote] (t:<track>) (<parent>) (w:<parentworld>)");
            }

            msg.send("&b/permissions &a(g:)<target> (w:<world>) [perms|parents] [list|add|remove] ...");
            msg.send("&b/permissions &a(g:)<target> (w:<world>) info [get|set|remove] ...");
        }

        return false;
    }

    private boolean reload(CommandSender sender, String arg) {
        Player p = null;
        if (sender instanceof Player) {
            p = (Player) sender;
        }

        if (arg == null || arg.equals("")) {

            if (p != null && !getHandler().has(p.getWorld().getName(), p.getName(), "permissions.reload.default")) {
                p.sendMessage(ChatColor.RED + "[Permissions] You lack the necessary permissions to perform this action.");
                return true;
            }

            getHandler().reload(defaultWorld);
            sender.sendMessage(ChatColor.GRAY + "[Permissions] Default world reloaded.");
            return true;
        }

        if (arg.equalsIgnoreCase("all")) {

            if (p != null && !getHandler().has(p.getWorld().getName(), p.getName(), "permissions.reload.all")) {
                p.sendMessage(ChatColor.RED + "[Permissions] You lack the necessary permissions to perform this action.");
                return true;
            }

            getHandler().reload();
            sender.sendMessage(ChatColor.GRAY + "[Permissions] All worlds reloaded.");
            return true;
        }

        if (p != null && !getHandler().has(p.getWorld().getName(), p.getName(), "permissions.reload." + arg)) {
            p.sendMessage(ChatColor.RED + "[Permissions] You lack the necessary permissions to perform this action.");
            return true;
        }

        if (getHandler().reload(arg))
            sender.sendMessage(ChatColor.GRAY + "[Permissions] Reload of World " + arg + " completed.");
        else
            sender.sendMessage(ChatColor.GRAY + "[Permissions] World " + arg + " does not exist.");
        return true;

    }

    @Override
    public String toString() {
        PluginDescriptionFile pdf = this.getDescription();
        return pdf.getName() + " version " + pdf.getVersion() + " (" + codename + ")";
    }

    private String listEntries(Collection<? extends Entry> entries, String type) {
        StringBuilder text = new StringBuilder();
        if (entries == null) {
            text.append("&4[Permissions] World does not exist.");
        } else if (entries.isEmpty()) {
            text.append("&4[Permissions] No ").append(type.toLowerCase()).append(" in that world.");
        } else {
            text.append("&a[Permissions] " + type + ": &b");
            for (Entry entry : entries) {
                text.append(entry.getName()).append(", ");
            }
            text.delete(text.length() - 2, text.length());
        }
        return text.toString();
    }

    private int extractQuoted(String[] args, int currentArg, StringBuilder target) {
        if (args.length <= currentArg)
            return -1; // Args array too small
        target.append(args[currentArg]);
        currentArg++;
        if (target.charAt(0) != '"')
            return currentArg;

        target.deleteCharAt(0); // Delete the starting quote

        while (args.length > currentArg) {
            target.append(" ").append(args[currentArg]);
            currentArg++;
            if (target.charAt(target.length() - 1) == '"') {
                target.deleteCharAt(target.length() - 1);
                return currentArg;
            }
        }

        return -2; // No ending quote
    }

    private Set<String> getNames(Collection<? extends Entry> entries) {
        Set<String> names = new HashSet<String>();
        for (Entry e : entries) {
            if (e != null)
                names.add(e.getName());
        }
        return names;
    }

    public static String getClosest(String word, Set<String> dict, final int threshold) {
        if (word == null || word.isEmpty() || dict == null || dict.isEmpty()) {
            return null;
        }
        if (dict.contains(word)) {
            return word;
        }
        String result = null;
        int currentDist = threshold;
        String lw = word.toLowerCase();
        for (String s : dict) {
            if (s == null)
                continue;
            String ls = s.toLowerCase();
            int dist;
            if (ls.startsWith(lw)) {
                dist = s.length() - word.length();
                if (currentDist > dist) {
                    result = s;
                    currentDist = dist;
                }
            }
        }
        return result;
    }
}
