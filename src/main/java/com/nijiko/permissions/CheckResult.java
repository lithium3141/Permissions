package com.nijiko.permissions;

class CheckResult {
    private final Entry source;
    private final String mostRelevantNode;
    private final Entry checked;
    private final String node;
    private final String world;
    private final boolean skipCache;
    private boolean valid = true;
    private final CheckResult parent;

    private final Object lock = new Object();

    public CheckResult(Entry source, String mrn, Entry checked, String node, String world, boolean skipCache) {
        this(source, mrn, checked, node, world, skipCache, null);
    }

    public CheckResult(Entry source, String mrn, Entry checked, String node, String world, boolean skipCache, CheckResult parent) {
        this.source = source;
        this.mostRelevantNode = mrn;
        this.checked = checked;
        this.node = node;
        this.world = world;
        this.skipCache = skipCache;
        this.parent = parent;
    }

    public Entry getSource() {
        return source;
    }

    public String getMostRelevantNode() {
        return mostRelevantNode;
    }

    public Entry getChecked() {
        return checked;
    }

    public String getNode() {
        return node;
    }

    public boolean getResult() {
        return this.mostRelevantNode == null ? false : !this.mostRelevantNode.startsWith("-");
    }

    public CheckResult setChecked(Entry e) {
        if (e == null)
            return null;
        // System.out.println(this);
        return new CheckResult(source, mostRelevantNode, e, node, world, skipCache, this);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Checked: ").append(checked.toString());
        b.append(" , Node: ").append(node);
        b.append(" , Source: ").append(source.toString());
        b.append(" , MRN: ").append(mostRelevantNode);
        b.append(" , World: ").append(world);
        b.append(" , SkipCache: ").append(skipCache);
        b.append(" , Valid: ");
        synchronized (lock) {
            b.append(valid);
        }
        return b.toString();
    }

    public boolean shouldSkipCache() {
        return skipCache;
    }

    public String getWorld() {
        return world;
    }

    public void invalidate() {
        synchronized (lock) {
            valid = false;
        }
    }

    //Possible infinite recursion!
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
