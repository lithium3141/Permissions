package com.nijiko.permissions;

import java.util.Set;

import com.nijiko.data.GroupStorage;
import com.nijiko.data.GroupWorld;
import com.nijiko.data.Storage;

public class Group extends Entry {

    private GroupStorage data;

    Group(ModularControl controller, GroupStorage data, String name, PermissionWorld worldObj, boolean create) {
        super(controller, name, worldObj);
        this.data = data;
        if (create && !world.equals("?")) {
            System.out.println("Creating group " + name);
            data.create(name);
        }
    }

    public boolean isDefault() {
        if(data == null)
            return false;
        return data.isDefault(name);
    }

    @Override
    public EntryType getType() {
        return EntryType.GROUP;
    }

    @Override
    public String toString() {
        return "Group " + name + " in " + world;
    }

    public Set<String> getTracks() {
        if(data == null)
            return null;
        return data.getTracks();
    }

    @Override
    protected Storage getStorage() {
        return data;
    }
    
    @Override
    public boolean delete() {
        worldObj.delGrp(name);
        return super.delete();
    }
    
    public GroupWorld toGroupWorld() {
        return new GroupWorld(world, name);
    }
}
