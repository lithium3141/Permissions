package com.nijiko.permissions;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
//import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
//import java.util.HashMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.nijiko.data.GroupWorld;
import com.nijiko.data.Storage;

//TODO: Cleanup and docs
/**
 * This abstract class represents a user or a group.
 * It provides general functions are shared by both groups and users.
 */
public abstract class Entry {

    protected ModularControl controller;
    protected PermissionWorld worldObj;
    protected String name;
    protected String world;
    protected Map<String, CheckResult> cache = new ConcurrentHashMap<String, CheckResult>();
    protected Set<String> transientPerms = new HashSet<String>();
    private final ConcurrentMap<String, Long> timedPerms = new ConcurrentHashMap<String, Long>();

    protected Entry(ModularControl controller, String name, PermissionWorld worldObj) {
        this.controller = controller;
        this.name = name;
        this.world = worldObj.getWorldName();
        this.worldObj = worldObj;
    }

    /**
     * Clears the cache of this entry.
     */
    public void clearCache() {
        for(Iterator<CheckResult> iter = cache.values().iterator();iter.hasNext();) {
            iter.next().invalidate();
            iter.remove();
        }
    }
    
    /**
     * Method called if a parent is added/removed.
     * Impl note: Currently Clears ALL caches. 
     * TODO: Is checking all entry if they are affected more effecient than clearing all caches? 
     */
    private void groupClearCache() {
        controller.clearAllCaches();
    }

    /**
     * Attempts to delete this entry.
     * @return Whether the deletion was successful.
     */
    public boolean delete() {
        clearCache();
        transientPerms.clear();
        timedPerms.clear();
        Storage store = getStorage();
        if (store != null)
            return store.delete(name);
        else
            return false;
    }

    /**
     * Adds a transient permission, which will last to the next minor reload.
     * @param node Transient permission node to add
     */
    public void addTransientPermission(String node) {
        if (node == null)
            return;
        transientPerms.add(node);
        clearCacheNode(node);
    }

    /**
     * Removes a transient permission.
     * @param node Transient permission node to remove
     */
    public void removeTransientPermission(String node) {
        if (node == null)
            return;
        transientPerms.remove(node);
        clearCacheNode(node);
    }

    /**
     * Clears all transient permissions.
     */
    public void clearTransientPerms() {
        Set<String> cloned = new HashSet<String>(transientPerms);
        transientPerms.clear();
        for (String node : cloned)
            clearCacheNode(node);
    }

    /**
     * Adds a timed permission. The duration is measured in server ticks.
     * @param node Timed permission node to add
     * @param duration Duration in server ticks
     * @return Previous duration for the timed permission node if it was added before, if any, or null otherwise.
     */
    public Long addTimedPermission(String node, long duration) {
        if (node == null)
            throw new NullPointerException("Supplied node is null");
        clearCacheNode(node);
        return timedPerms.put(node, duration);
    }
    /**
     * Removes a timed permission.
     * @param node Timed permission node to add
     * @return Previous duration for the timed permission node, if any, or null otherwise.
     */
    public Long removeTimedPermission(String node) {
        if (node == null)
            throw new NullPointerException("Supplied node is null");
        clearCacheNode(node);
        return timedPerms.remove(node);
    }

    /**
     * Get the remaining duration for a timed permission node
     * @param node Timed permission node to check
     * @return Previous duration for the timed permission node, if any, or null otherwise.
     */
    public Long getDuration(String node) {
        return timedPerms.get(node);
    }

    /**
     * Clears all timed permissions.
     */
    public void clearTimedPerms() {
        Set<String> cloned = new HashSet<String>(timedPerms.keySet());
        timedPerms.clear();
        for (String node : cloned)
            clearCacheNode(node);
    }

    /**
     * Interval function used by ticker task. This periodically updates the duration.
     * @param interval Interval in server ticks
     */
    void tick(long interval) {
        if (interval <= 0)
            return;
        for (Iterator<Map.Entry<String, Long>> iter = timedPerms.entrySet().iterator(); iter.hasNext();) {
            Map.Entry<String, Long> entry = iter.next();
            Long left = entry.getValue();
            if (left == null) {
                clearCacheNode(entry.getKey());
                iter.remove();
                continue;
            }
            if (left < 0)
                continue;
            long newLeft = left - interval;
            if (newLeft <= 0) {
                clearCacheNode(entry.getKey());
                iter.remove();
                continue;
            }
            entry.setValue(newLeft);
        }
    }

    /**
     * Used by PermissionWorld to copy timed perms to the new entry object
     * @param e Old entry from previous load. Must have the same world and name as this instance.
     */
    void copyTimedMap(Entry e) {
        if (!this.equals(e))
            return;
        timedPerms.putAll(e.timedPerms);
    }

    /**
     * Returns a set containing all current timed permissions. This set will not reflect changes made after creation.
     * @return Set containing all current timed permissions.
     */
    protected Set<String> getTimedPermissions() {
        return new HashSet<String>(timedPerms.keySet());
    }

    /**
     * Returns an unmodifiable view of the cache.
     * @return
     */
    public Map<String, CheckResult> getCache() {
        return Collections.unmodifiableMap(cache);
    }

    /**
     * Abstract method implemented by subclasses to return the storage instance.
     * This method needs to be implemented so as to allow many methods in this class to work.
     * @return Storage instance used by subclasses
     */
    protected abstract Storage getStorage();

    /**
     * Returns a set containing the permissions of this entry, including timed and transient permissions.
     * This set will not reflect any modifications made after its creation.
     * @return Set containing this entry's permissions.
     */
    public Set<String> getPermissions() {
        Set<String> perms = new HashSet<String>();
        Storage store = getStorage();
        if (store != null)
            perms.addAll(store.getPermissions(name));
        resolvePerms(perms, transientPerms);
        resolvePerms(perms, this.getTimedPermissions());
        return perms;
    }

    /**
     * Returns a set containing world name-group name pairs that represent this entry's parents
     * @return Set containing this entry's parents in world name-group name pair form.
     */
    public LinkedHashSet<GroupWorld> getRawParents() {
        Storage store = getStorage();
        if (store != null)
            return store.getParents(name);
        return null;
    }

    /**
     * Add/removes a node. Its behaviour depends on the <tt>add</tt> parameter.
     * @param permission Node to add/remove
     * @param add True if node is to be added, false if it is to be removed
     */
    public void setPermission(final String permission, final boolean add) {
        if (add)
            addPermission(permission);
        else
            removePermission(permission);
    }

    /**
     * Adds a permission to this entry.
     * @param permission Permission to add
     */
    public void addPermission(final String permission) {
        clearCacheNode(permission);
        Storage store = getStorage();
        if (store != null)
            store.addPermission(name, permission);
    }

    /**
     * Removes a permission from this entry.
     * @param permission Permission to remove
     */
    public void removePermission(final String permission) {
        clearCacheNode(permission);
        Storage store = getStorage();
        if (store != null)
            store.removePermission(name, permission);
    }

    /**
     * Adds a group to this entry's parent list.
     * @param group Group to add
     */
    public void addParent(Group group) {
        groupClearCache();
        Storage store = getStorage();
        if (store != null)
            store.addParent(name, group.world, group.name);
    }

    /**
     * Removes a group from this entry's parent list.
     * @param group Group to remove
     */
    public void removeParent(Group group) {
        groupClearCache();
        Storage store = getStorage();
        if (store != null)
            store.removeParent(name, group.world, group.name);
    }

    /**
     * Checks this entry and its ancestors for the specified permission
     * @param permission Node to check for
     * @return Whether this entry has that permission
     */
    public boolean hasPermission(String permission) {
        if (permission == null)
            return true;
        CheckResult cr = has(permission, relevantPerms(permission), new LinkedHashSet<Entry>(), world);
        return cr.getResult();
    }
    
    /**
     * This method is called to update the cache when a node is added/removed.
     * @param node
     */
    private void clearCacheNode(String node) {
        if(node == null)
            return;
        CheckResult cr = cache.remove(node);
        if(cr != null)
            cr.invalidate();
    }
    
    /**
     * Checks this entry and its ancestors for the specified permission, 
     * and returns a CheckResult object containing more info about the result than hasPermission() provides.
     * @see CheckResult
     * @param node Node to check for
     * @return Results of the check
     */
    public CheckResult has(String node) {
        if (node == null)
            return null;
        return has(node, relevantPerms(node), new LinkedHashSet<Entry>(), world);        
    }

    /**
     * Permission checking algorithm. 
     * Impl notes: Currently, this uses a DFS. Also, an entry can inherit from a parent multiple times,
     * as long as the "path" taken to reach that parent is different. 
     * @param node Node to check for
     * @param relevant Precomputed set containing relevant nodes
     * @param checked Set containing the inheritance "path" taken to reach this entry
     * @param world Specialising world
     * @return Result of check
     */
    protected CheckResult has(String node, LinkedHashSet<String> relevant, LinkedHashSet<Entry> checked, String world) {

        if (checked.contains(this))
            return null;
        checked.add(this);

        CheckResult cr = cache.get(node);
        if (cr != null && (!world.equals(cr.getWorld()) || !cr.isValid())) {
            cr = null;
        }

        boolean skipCache = false;

        if (cr == null) {
            // Check own permissions
            Set<String> perms = this.getPermissions();
            for (String mrn : relevant) {
                if (perms.contains(mrn)) {
                    skipCache = timedPerms.containsKey(mrn);
                    cr = new CheckResult(this, mrn, this, node, world, skipCache);
                    break;
                }
            }

            if (cr == null) {
                // Check parent permissions
                for (Entry e : this.getParents(world)) {
                    CheckResult parentCr = e.has(node, relevant, checked, world);
                    if (parentCr == null)
                        continue;
                    if (parentCr.getRelevantNode() != null) {
                        cr = parentCr.setChecked(this);
                        break;
                    }
                }

                if (cr == null) {
                    // No relevant permissions
                    cr = new CheckResult(this, null, this, node, world, false);
                }
            }
            cache(cr);
        }

        checked.remove(this);
        return cr;
    }

    /**
     * Caches a CheckResult.
     * @param cr CheckResult to cache.
     */
    protected void cache(CheckResult cr) {
        if (cr == null || cr.getNode() == null || cr.shouldSkipCache() || !cr.isValid())
            return;
        cache.put(cr.getNode(), cr);
    }

    /**
     * Checks whether this entry inherits from the given entry.
     * @param entry Possible parent entry
     * @return True if this entry inherits from the given entry, false otherwise.
     */
    public boolean isChildOf(final Entry entry) {
        if (entry == null)
            return false;
        Boolean val = recursiveCheck(new EntryVisitor<Boolean>() {
            @Override
            public Boolean value(Entry e) {
                if (entry.equals(e))
                    return true;
                return null;
            }
        });
        return val == null ? false : val;
    }

    /**
     * Checks whether this entry inherits from a group that matches the given worldname-groupname pair.
     * @return True if this entry has a parent group that matches that pair, false otherwise.
     * @return
     */
    boolean isChildOf(final GroupWorld gw) {
        Boolean val = recursiveCheck(new EntryVisitor<Boolean>() {
            @Override
            public Boolean value(Entry e) {
                if (e.getRawParents().contains(gw))
                    return true;
                return null;
            }
        });
        return val == null ? false : val;
    }

    /**
     * Returns a set containing all permissions, including inherited ones.
     * @return
     */
    public Set<String> getAllPermissions() {
        return getAllPermissions(new LinkedHashSet<Entry>(), world);
    }

    /**
     * This method combines the permissions inherited from parents
     * and this entry's own permissions and returns the result.
     * @param chain Set containing the inheritance "path" taken to reach this entry
     * @param world Specialising world
     * @return
     */
    protected Set<String> getAllPermissions(LinkedHashSet<Entry> chain, String world) {
        Set<String> perms = new HashSet<String>();
        if (chain == null)
            return perms;
        if (chain.contains(this))
            return perms;
        else
            chain.add(this);
        LinkedHashSet<Entry> rawParents = getParents(world);
        Deque<Entry> parents = new ArrayDeque<Entry>();
        for (Entry e : rawParents)
            if (!chain.contains(e))
                parents.push(e);
        rawParents = null;

        for (Entry e : parents) {
            resolvePerms(perms, e.getAllPermissions(chain, world));
        }
        if (chain.contains(this))
            chain.remove(this);
        resolvePerms(perms, this.getPermissions());
        return perms;
    }

    /**
     * Computes the combination of <tt>perms</tt> and <tt>rawPerms</tt>,
     * with nodes in <tt>rawPerms</tt> overriding nodes in <tt>perms</tt>.
     * @param perms Overridable set of permissions
     * @param rawPerms New permissions to add
     */
    protected static void resolvePerms(Set<String> perms, Set<String> rawPerms) {
        for (Iterator<String> rawIter = rawPerms.iterator(); rawIter.hasNext();) {
            String perm = rawIter.next();
            if (perm.isEmpty()) {
                rawIter.remove();
                continue;
            }

            if (perm.endsWith("*")) { // Wildcards
                String wild = perm.substring(0, perm.length() - 1);
                String oppWild = negationOf(perm).substring(0, perm.length() - 1);
                for (Iterator<String> itr = perms.iterator(); itr.hasNext();) {
                    String candidate = itr.next();
                    if (candidate.startsWith(oppWild) || candidate.startsWith(wild))
                        itr.remove();
                }
            }
        }
        perms.addAll(rawPerms);
        return;
    }

    /**
     * Gets the parents of this entry, specialising all ?-world groups into groups with the same world as this entry.
     * Note that users may inherit from users via world inheritance and/or global permissions.
     * @return Set containing parent entries.
     */
    public LinkedHashSet<Entry> getParents() {
        return getParents(world);
    }

    /**
     * Identical to getParents() but without specialising ?-world groups.
     * @return Set containing parent entries.
     */
    protected LinkedHashSet<Entry> getUnspecialisedParents() {
        return getParents(null);
    }

    /**
     * Gets the parents of this entry, specialising all ?-world groups into groups with the provided world.
     * Note that users may inherit from users via world inheritance and/or global permissions.
     * @return Set containing parent entries.
     */
    public LinkedHashSet<Entry> getParents(String world) {
        LinkedHashSet<Group> groupParents = controller.stringToGroups(getRawParents(), world);
        LinkedHashSet<Entry> parents = new LinkedHashSet<Entry>();
        parents.addAll(groupParents);
        if (!this.world.equals("*")) {
            Entry global = this.getType() == EntryType.USER ? controller.getUserObject("*", name) : controller.getGroupObject("*", name);
            if (global != null)
                parents.add(global);
        }
        String parentWorld = controller.getWorldParent(world, this.getType() == EntryType.USER);
        if (parentWorld != null) {
            Entry inherited = this.getType() == EntryType.USER ? controller.getUserObject(parentWorld, name) : controller.getGroupObject(parentWorld, name);
            if (inherited != null)
                parents.add(inherited);
        }
        return parents;
    }

    /**
     * Returns the weight of this entry.
     * @return Weight of this entry.
     */
    public int getWeight() {
        Integer value = getInt("weight");
        return value == null ? -1 : value;
    }

    /**
     * Gets the ancestors of this entry, including parents, parents of parents and etc.
     * @return Set of this entry's ancestors.
     */
    public LinkedHashSet<Entry> getAncestors() {
        LinkedHashSet<Entry> parentSet = new LinkedHashSet<Entry>();
        Queue<Entry> queue = new ArrayDeque<Entry>();

        // Start with the direct ancestors or the default group
        LinkedHashSet<Entry> parents = getParents();
        if (parents != null && parents.size() > 0)
            queue.addAll(parents);
        // Poll the queue
        while (queue.peek() != null) {
            Entry entry = queue.poll();
            if (parentSet.contains(entry))
                continue;
            parents = entry.getParents(world);
            if (parents != null && parents.size() > 0)
                queue.addAll(parents);
            parentSet.add(entry);
        }

        return parentSet;
    }

    /**
     * Checks whether a group matches a worldname-groupname pair.
     * @author rcjrrjcr
     */
    static class GroupChecker implements EntryVisitor<Boolean> {
        protected final String world;
        protected final String group;

        GroupChecker(String world, String group) {
            this.world = world;
            this.group = group;
        }

        @Override
        public Boolean value(Entry e) {
            if (e instanceof Group) {
                Group g = (Group) e;
                if (g.world != null && g.name != null && g.world.equals(world) && g.name.equalsIgnoreCase(group))
                    return true;
            }
            return null;
        }
    }

    /**
     * Checks if the user inherits from the group specified by the parameters
     * @param world World of group
     * @param group Name of group
     * @return True if user inherits from that group (directly or indirectly), false otherwise
     */
    public boolean inGroup(String world, String group) {
        if (this.getType() == EntryType.GROUP && this.world.equalsIgnoreCase(world) && this.name.equalsIgnoreCase(group))
            return true;

        Boolean val = this.recursiveCheck(new GroupChecker(world, group));
        return val == null ? false : val;
    }

    /**
     * Checks if entry can build.
     * @return Whether entry can build.
     */
    public boolean canBuild() {
        Boolean value = this.recursiveCheck(new BooleanInfoVisitor("build"));
        return value == null ? false : value;
    }

    /**
     * This method traverses the inheritance tree (postorder traversal),
     * and for every entry in the inheritance tree, the visitor's <tt>value()</tt> method is called.
     * The visitor should return a non-null value to halt traversal, and a null value to continue to the next entry.
     * When traversal is halted abruptly, the non-null value returned by the visitor will be returned by this method.
     * When traversal completes without the visitor returning a non-null value, null will be returned.
     * @param <T> Return value of visitor
     * @param visitor Visitor object
     * @return Visitor's first non-null return value, or null if
     * traversal completes without any non-null values returned by the visitor.
     */
    public <T> T recursiveCheck(EntryVisitor<T> visitor) {
        return recursiveCheck(new LinkedHashSet<Entry>(), visitor, world);
    }

    protected <T> T recursiveCheck(LinkedHashSet<Entry> checked, EntryVisitor<T> visitor, String overrideWorld) {
        if (checked.contains(this))
            return null;

        T result = visitor.value(this);
        if (result != null)
            return result;

        LinkedHashSet<Entry> parents = getParents(overrideWorld);
        if (parents == null || parents.isEmpty())
            return null;

        checked.add(this);

        for (Entry entry : parents) {
            if (checked.contains(entry))
                continue;
            result = entry.recursiveCheck(checked, visitor, overrideWorld);
            if (result != null)
                return result;
        }

        checked.remove(this);
        return null;
    }

    /**
     * This method is similar to recursiveCheck, but this instead returns the 
     * largest value (according to the comparator) returned by the visitor.
     * If a non-null value is returned for an entry, that entry's parents will not be visited.
     * @param <T> Return type
     * @param visitor Visitor object
     * @param comparator Comparator used to compare visitor return values
     * @return Largest value found by a visitor.
     */
    public <T> T recursiveCompare(EntryVisitor<T> visitor, Comparator<T> comparator) {
        return recursiveCompare(new LinkedHashSet<Entry>(), visitor, comparator, world);
    }

    protected <T> T recursiveCompare(LinkedHashSet<Entry> checked, EntryVisitor<T> visitor, Comparator<T> comparator, String overrideWorld) {
        if (checked.contains(this))
            return null;

        T result = visitor.value(this);
        if (result != null)
            return result;

        Set<Entry> parents = getParents(overrideWorld);
        if (parents == null || parents.isEmpty())
            return null;

        checked.add(this);
        T currentValue = null;

        for (Entry e : parents) {
            if (checked.contains(e))
                continue;
            result = e.recursiveCompare(checked, visitor, comparator, overrideWorld);
            if (result != null) {
                if (comparator.compare(result, currentValue) > 0)
                    currentValue = result;
            }
        }

        checked.remove(this);
        return currentValue;
    }

    /**
     * Abstract function implemented by subclasses to return the type of entry it represents.
     * @return Type of entry (user, group)
     */
    public abstract EntryType getType();

    /**
     * Returns the name of this entry.
     * @return Name
     */
    public String getName() {
        return name;
    }

    public String getWorld() {
        return world;
    }

    @Override
    public String toString() {
        return "Entry " + name + " in " + world;
    }

    public void setData(String path, Object data) {
        Storage store = getStorage();
        if (store != null)
            store.setData(name, path, data);
    }

    public String getRawString(String path) {
        Storage store = getStorage();
        if (store != null)
            return store.getString(name, path);
        else
            return null;
    }

    public Integer getRawInt(String path) {
        Storage store = getStorage();
        if (store != null)
            return store.getInt(name, path);
        else
            return null;
    }

    public Boolean getRawBool(String path) {
        Storage store = getStorage();
        if (store != null)
            return store.getBool(name, path);
        else
            return null;
    }

    public Double getRawDouble(String path) {
        Storage store = getStorage();
        if (store != null)
            return store.getDouble(name, path);
        else
            return null;
    }

    public void removeData(String path) {
        Storage store = getStorage();
        if (store != null)
            store.removeData(name, path);
    }

    public static final class BooleanInfoVisitor implements EntryVisitor<Boolean> {
        private final String path;

        public BooleanInfoVisitor(String path) {
            this.path = path;
        }

        @Override
        public Boolean value(Entry e) {
            return e.getRawBool(path);
        }
    }

    public static final class DoubleInfoVisitor implements EntryVisitor<Double> {
        private final String path;

        protected DoubleInfoVisitor(String path) {
            this.path = path;
        }

        @Override
        public Double value(Entry e) {
            return e.getRawDouble(path);
        }
    }

    public static final class IntegerInfoVisitor implements EntryVisitor<Integer> {
        private final String path;

        public IntegerInfoVisitor(String path) {
            this.path = path;
        }

        @Override
        public Integer value(Entry e) {
            return e.getRawInt(path);
        }
    }

    public static final class StringInfoVisitor implements EntryVisitor<String> {
        private final String path;

        private StringInfoVisitor(String path) {
            this.path = path;
        }

        @Override
        public String value(Entry e) {
            return e.getRawString(path);
        }
    }

    public interface EntryVisitor<T> {
        /**
         * This is the method called by the recursive checker when searching for a value. If the recursion is to be stopped, return a non-null value.
         * 
         * @param g
         *            Group to test
         * @return Null if recursion should continue, any applicable value otherwise
         */
        T value(Entry e);
    }

    public Integer getInt(String path) {
        return getInt(path, new SimpleComparator<Integer>());
    }

    public Integer getInt(final String path, Comparator<Integer> comparator) {
        Integer value = this.recursiveCompare(new IntegerInfoVisitor(path), comparator);
        return value;
    }

    public Double getDouble(String path) {
        return getDouble(path, new SimpleComparator<Double>());
    }

    public Double getDouble(final String path, Comparator<Double> comparator) {
        Double value = this.recursiveCompare(new DoubleInfoVisitor(path), comparator);
        return value;
    }

    public Boolean getBool(String path) {
        return getBool(path, new SimpleComparator<Boolean>());
    }

    public Boolean getBool(final String path, Comparator<Boolean> comparator) {
        Boolean value = this.recursiveCompare(new BooleanInfoVisitor(path), comparator);
        return value;
    }

    public String getString(String path) {
        return getString(path, new SimpleComparator<String>());
    }

    public String getString(final String path, Comparator<String> comparator) {
        String value = this.recursiveCompare(new StringInfoVisitor(path), comparator);
        return value;
    }

    // And now to showcase how insane Java generics can get
    /**
     * Simple comparator to order objects by natural ordering
     */
    public static class SimpleComparator<T extends Comparable<T>> implements Comparator<T>, Serializable {
        /**
         * 
         */
        private static final long serialVersionUID = -2712787010868605898L;

        @Override
        public int compare(T o1, T o2) {
            if (o1 == null) {
                if (o2 == null)
                    return 0;
                return -1;
            }
            if (o2 == null)
                return 1;
            return o1.compareTo(o2);
        }
    }

    public String getPrefix() {
        return recursiveCheck(new StringInfoVisitor("prefix"));
    }

    public String getSuffix() {
        return recursiveCheck(new StringInfoVisitor("suffix"));
    }

    public static LinkedHashSet<String> relevantPerms(String node) {
        if (node == null) {
            return null;
        }
        if (node.startsWith("-"))
            return relevantPerms(negationOf(node));
        LinkedHashSet<String> relevant = new LinkedHashSet<String>();
        if (!node.endsWith(".*")) {
            relevant.add(node);
            relevant.add(negationOf(node));
        }

        String[] split = node.split("\\.");
        List<String> rev = new ArrayList<String>(split.length);

        StringBuilder sb = new StringBuilder();
        sb.append("*");

        for (int i = 0; i < split.length; i++) { // Skip the last one
            String wild = sb.toString();
            String neg = negationOf(wild);
            rev.add(neg);
            rev.add(wild);
            sb.deleteCharAt(sb.length() - 1);
            sb.append(split[i]).append(".*");
        }

        for (ListIterator<String> iter = rev.listIterator(rev.size()); iter.hasPrevious();) {
            relevant.add(iter.previous());
        }

        return relevant;
    }

    public static String negationOf(String node) {
        return node == null ? null : node.startsWith("-") ? node.substring(1) : "-" + node;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((world == null) ? 0 : world.hashCode());
        EntryType type = getType();
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof Entry))
            return false;

        Entry other = (Entry) obj;

        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;

        if (world == null) {
            if (other.world != null)
                return false;
        } else if (!world.equals(other.world))
            return false;

        EntryType type = getType();
        EntryType otherType = other.getType();
        if (type == null) {
            if (otherType != null)
                return false;
        } else if (!type.equals(otherType))
            return false;
        return true;
    }
}
