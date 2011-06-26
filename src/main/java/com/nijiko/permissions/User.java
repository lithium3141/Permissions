package com.nijiko.permissions;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.ListIterator;

import com.nijiko.data.GroupStorage;
import com.nijiko.data.GroupWorld;
import com.nijiko.data.Storage;
import com.nijiko.data.UserStorage;

public class User extends Entry {
    private UserStorage data;

    User(ModularControl controller, UserStorage data, String name, PermissionWorld worldObj, boolean create) {
        super(controller, name, worldObj);
        this.data = data;
        if (create && !world.equals("?")) {
            System.out.println("Creating user " + name);
            data.create(name);
        }
        if (this.getRawParents().isEmpty()) {
            Group g = worldObj.getDefaultGroup();
            if (g != null) {
                this.addParent(g);
                if (this.world.equals("*")) {
                    Group qDef = controller.getGrp("?", g.name);
                    if (qDef != null)
                        this.addParent(qDef);
                }
            }
        }
    }

    @Override
    public EntryType getType() {
        return EntryType.USER;
    }

    @Override
    public String toString() {
        return "User " + name + " in " + world;
    }

    public Group getPrimaryGroup() {

        LinkedHashSet<Entry> parents = getParents();

        if (parents == null || parents.isEmpty())
            return null;

        for (Entry e : parents) {
            if (e instanceof Group) {
                return (Group) e;

            } else if (e instanceof User) {
                User p = (User) e;
                if (p.getWorld().equals("*") && !getWorld().equals("*")) { // Prevent infinite loops
                    LinkedHashSet<Entry> grandparents = p.getParents();
                    if (grandparents != null && !grandparents.isEmpty()) {
                        for (Entry pe : parents) {
                            if (pe instanceof Group) // One level of unrolled recursion only
                                return (Group) pe;
                        }
                    }
                }
            }
        }

        return null;
    }

    // TODO: Edit promote/demote code to take advantage of ? entry changes

    public void demote(Group g, String track) {
        // Demote's code is slightly different from promote's as in promote, groupW can be null, so the user can be bumped up to the first entry in the track, but there's no equivalent for demotion.
        if (g == null)
            return;
        if (!this.getParents(null).contains(g))
            return;

        GroupStorage gStore = worldObj.getGroupStorage();
        if (gStore == null)
            return;
        LinkedList<GroupWorld> trackGroups = gStore.getTrack(track);
        if (trackGroups == null)
            return;
        GroupWorld groupW = g.toGroupWorld();
        for (ListIterator<GroupWorld> iter = trackGroups.listIterator(trackGroups.size() - 1); iter.hasPrevious();) {
            GroupWorld gw = iter.previous();
            if (gw.equals(groupW)) {
                this.removeParent(g);
                if (iter.hasPrevious()) {
                    GroupWorld prev = iter.previous();
                    this.addParent(controller.getGrp(prev.getWorld(), prev.getName()));
                }
                break;
            }
        }
    }

    public void promote(Group g, String track) {
        if (!this.getParents(null).contains(g))
            return;

        GroupStorage gStore = worldObj.getGroupStorage();
        if (gStore == null)
            return;
        LinkedList<GroupWorld> trackGroups = gStore.getTrack(track);
        if (trackGroups == null)
            return;
        if (g == null) {
            ListIterator<GroupWorld> iter = trackGroups.listIterator();
            if (iter.hasNext()) {
                GroupWorld gw = iter.next();
                if (gw != null)
                    this.addParent(controller.getGrp(gw.getWorld(), gw.getName()));
            }
            return;
        }

        GroupWorld groupW = g.toGroupWorld();
        for (ListIterator<GroupWorld> iter = trackGroups.listIterator(); iter.hasNext();) {
            GroupWorld gw = iter.next();
            if (gw.equals(groupW)) {
                if (iter.hasNext()) {
                    GroupWorld next = iter.next();
                    this.removeParent(g);
                    this.addParent(controller.getGrp(next.getWorld(), next.getName()));
                    return;
                }
            }
        }
    }

    @Override
    protected Storage getStorage() {
        return data;
    }

    @Override
    public boolean delete() {
        worldObj.delUsr(name);
        return super.delete();
    }
}
