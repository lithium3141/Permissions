package com.nijiko.permissions;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
//import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
//import java.util.HashMap;
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
public abstract class Entry {

    protected ModularControl controller;
    protected PermissionWorld worldObj;
    protected String name;
    protected String world;
    protected Map<String, CheckResult> cache = new ConcurrentHashMap<String, CheckResult>();
    protected Set<String> transientPerms = new HashSet<String>();
    private final ConcurrentMap<String, Long> timedPerms = new ConcurrentHashMap<String, Long>();

    Entry(ModularControl controller, String name, PermissionWorld worldObj) {
        this.controller = controller;
        this.name = name;
        this.world = worldObj.getWorldName();
        this.worldObj = worldObj;
    }

    public void clearCache() {
        for(Iterator<CheckResult> iter = cache.values().iterator();iter.hasNext();) {
            iter.next().invalidate();
            iter.remove();
        }
    }

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

    public void addTransientPermission(String node) {
        if (node == null)
            return;
        clearCacheNode(node);
        transientPerms.add(node);
    }

    public void removeTransientPermission(String node) {
        if (node == null)
            return;
        clearCacheNode(node);
        transientPerms.remove(node);
    }

    public void clearTransientPerms() {
        Set<String> cloned = new HashSet<String>(transientPerms);
        transientPerms.clear();
        for (String node : cloned)
            clearCacheNode(node);
    }

    public Long addTimedPermission(String node, long duration) {
        if (node == null)
            throw new NullPointerException("Supplied node is null");
        clearCacheNode(node);
        return timedPerms.put(node, duration);
    }

    public Long removeTimedPermission(String node) {
        if (node == null)
            throw new NullPointerException("Supplied node is null");
        clearCacheNode(node);
        return timedPerms.remove(node);
    }

    public Long getDuration(String node) {
        return timedPerms.get(node);
    }

    public void clearTimedPerms() {
        Set<String> cloned = new HashSet<String>(timedPerms.keySet());
        timedPerms.clear();
        for (String node : cloned)
            clearCacheNode(node);
    }

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

    // Used by PermissionWorld to copy timed perms to the new entry object
    void copyTimedMap(Entry e) {
        if (!this.equals(e))
            return;
        timedPerms.putAll(e.timedPerms);
    }

    protected Set<String> getTimedPermissions() {
        return new HashSet<String>(timedPerms.keySet());
    }

    public Map<String, CheckResult> getCache() {
        return cache;
    }

    protected abstract Storage getStorage();

    public Set<String> getPermissions() {
        Set<String> perms = new HashSet<String>();
        Storage store = getStorage();
        if (store != null)
            perms.addAll(store.getPermissions(name));
        resolvePerms(perms, transientPerms);
        resolvePerms(perms, this.getTimedPermissions());
        return perms;
    }

    public LinkedHashSet<GroupWorld> getRawParents() {
        Storage store = getStorage();
        if (store != null)
            return store.getParents(name);
        return null;
    }

    public void setPermission(final String permission, final boolean add) {
        if (add)
            addPermission(permission);
        else
            removePermission(permission);
    }

    public void addPermission(final String permission) {
        clearCacheNode(permission);
        Storage store = getStorage();
        if (store != null)
            store.addPermission(name, permission);
    }

    public void removePermission(final String permission) {
        clearCacheNode(permission);
        Storage store = getStorage();
        if (store != null)
            store.removePermission(name, permission);
    }

    public void addParent(Group group) {
        clearCache();
        Storage store = getStorage();
        if (store != null)
            store.addParent(name, group.world, group.name);
    }

    public void removeParent(Group group) {
        clearCache();
        Storage store = getStorage();
        if (store != null)
            store.removeParent(name, group.world, group.name);
    }

    public boolean hasPermission(String permission) {
        if (permission == null)
            return true;
        CheckResult cr = has(permission, relevantPerms(permission), new LinkedHashSet<Entry>(), world);
        return cr.getResult();
    }
    
    private void clearCacheNode(String node) {
        if(node == null)
            return;
        CheckResult cr = cache.remove(node);
        if(cr != null)
            cr.invalidate();
    }

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
                    if (parentCr.getMostRelevantNode() != null) {
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

    protected void cache(CheckResult cr) {
        if (cr == null || cr.getNode() == null || cr.shouldSkipCache() || !cr.isValid())
            return;
        cache.put(cr.getNode(), cr);
    }

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

    public Set<String> getAllPermissions() {
        return getAllPermissions(new LinkedHashSet<Entry>(), world);
    }

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
            perms = resolvePerms(perms, e.getAllPermissions(chain, world));
        }
        if (chain.contains(this))
            chain.remove(this);
        resolvePerms(perms, this.getPermissions());
        return perms;
    }

    protected static Set<String> resolvePerms(Set<String> perms, Set<String> rawPerms) {
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
        return perms;
    }

    public LinkedHashSet<Entry> getParents() {
        return getParents(world);
    }

    protected LinkedHashSet<Entry> getUnspecialisedParents() {
        return getParents(null);
    }

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

    public int getWeight() {
        Integer value = getInt("weight");
        return value == null ? -1 : value;
    }

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

    public boolean inGroup(String world, String group) {
        if (this.getType() == EntryType.GROUP && this.world.equalsIgnoreCase(world) && this.name.equalsIgnoreCase(group))
            return true;

        Boolean val = this.recursiveCheck(new GroupChecker(world, group));
        return val == null ? false : val;
    }

    public boolean canBuild() {
        Boolean value = this.recursiveCheck(new BooleanInfoVisitor("build"));
        return value == null ? false : value;
    }

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

    public abstract EntryType getType();

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
