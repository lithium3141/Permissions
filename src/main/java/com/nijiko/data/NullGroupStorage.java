package com.nijiko.data;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import com.nijiko.permissions.EntryType;

public class NullGroupStorage extends NullStorage implements GroupStorage {

    public NullGroupStorage(String world) {
        super(world);
    }

    @Override
    public EntryType getType() {
        return EntryType.GROUP;
    }

    @Override
    public boolean isDefault(String name) {
        return false;
    }

    @Override
    public Set<String> getTracks() {
        return new HashSet<String>();
    }

    @Override
    public LinkedList<GroupWorld> getTrack(String track) {
        return new LinkedList<GroupWorld>();
    }

}
