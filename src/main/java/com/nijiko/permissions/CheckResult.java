package com.nijiko.permissions;

/**
 * This class contains the results of a permissions check.
 * It includes the checked node, the checked entry,
 * the relevant node, the parent that is the source of the relevant node,
 * and the checked world (used by wildcard specialization).
 * @author rcjrrjcr
 */
class CheckResult {
    /**
     * Source of the relevant node
     */
    private final Entry source;
    /**
     * Relevant node that successfully matched the checked node
     */
    private final String relevantNode;
    /**
     * The entry that was checked
     */
    private final Entry checked;
    /**
     * The node that was being searched for
     */
    private final String node;
    /**
     * Wildcard specialisation world. Used to prevent caches from mixing CheckResutlts
     * with a different specialisation, and possibly a different group
     */
    private final String world;
    /**
     * Whether to skip cacheing this node. This flag will be true if the relevant node is a timed one.
     */
    private final boolean skipCache;
    /**
     * Validity of this result
     */
    private boolean valid = true;
    /**
     * Parent result, which this result was derived from using setChecked
     */
    private final CheckResult parent;

    /**
     * Object used to synch access to the validity of this CheckResult object
     */
    private final Object lock = new Object();

    public CheckResult(Entry source, String relevantNode, Entry checked, String node, String world, boolean skipCache) {
        this(source, relevantNode, checked, node, world, skipCache, null);
    }

    public CheckResult(Entry source, String relevantNode, Entry checked, String node, String world, boolean skipCache, CheckResult parent) {
        this.source = source;
        this.relevantNode = relevantNode;
        this.checked = checked;
        this.node = node;
        this.world = world;
        this.skipCache = skipCache;
        this.parent = parent;
    }

    /**
     * Returns the entry which is/was source of the relevant node.
     * As long this result is valid, the source entry is always a parent of the checked entry.
     * @return Source entry
     */
    public Entry getSource() {
        return source;
    }

    /**
     * Returns the relevant node that successfully matched the node that was being checked for.
     * The relevant node will be null if no nodes matched the checked node.
     * @return Relevant node
     */
    public String getRelevantNode() {
        return relevantNode;
    }

    /**
     * Returns the entry that was checked for the checked node
     * @return Checked entry
     */
    public Entry getChecked() {
        return checked;
    }

    /**
     * Returns the node that was checked for (the argument passed to hasPermission)
     * @return Node that was checked for.
     */
    public String getNode() {
        return node;
    }

    /**
     * Returns the result of the check.
     * @return Result of the permission check
     */
    public boolean getResult() {
        return this.relevantNode == null ? false : !this.relevantNode.startsWith("-");
    }

    /**
     * Creates a new child instance of CheckResult, which is identical to this,
     * except the checked entry will be set to the provided entry.
     * Also, if this instance is invalidated, all child instances will be invalidated (lazily).
     * @param e
     * @return
     */
    public CheckResult setChecked(Entry e) {
        if (e == null)
            return null;
        // System.out.println(this);
        return new CheckResult(source, relevantNode, e, node, world, skipCache, this);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Checked: ").append(checked.toString());
        b.append(" , Node: ").append(node);
        b.append(" , Source: ").append(source.toString());
        b.append(" , MRN: ").append(relevantNode);
        b.append(" , World: ").append(world);
        b.append(" , SkipCache: ").append(skipCache);
        b.append(" , Valid: ");
        synchronized (lock) {
            b.append(valid);
        }
        return b.toString();
    }

    /**
     * Returns whether the relevant node was a timed permission
     * and that this CheckResult should not be cached.
     * @return Whether this should be cached
     */
    public boolean shouldSkipCache() {
        return skipCache;
    }

    /**
     * Returns specialising world. This is used to prevent different worlds
     * from sharing a cache result when world inheritance/global groups are used
     * with "?"-world (specialisable) groups.
     * @return
     */
    public String getWorld() {
        return world;
    }

    /**
     * Invalidates this result and all child instances.
     */
    public void invalidate() {
        synchronized (lock) {
            valid = false;
        }
    }

    /**
     * This checks whether this or any parent instance has been invalidated.
     * @return Whether the results provided by this instance is valid
     */
    //Possible infinite recursion! However, Entry.has() prevents cycles. Other sources may not be safe.
    public boolean isValid() {
        synchronized (lock) {
            if (!valid)
                return false;
            else if (parent != null) {
                if (parent.isValid())
                    return true;
                else {
                    valid = false;
                    return false;
                }
            }
            return true;
        }
    }
}
