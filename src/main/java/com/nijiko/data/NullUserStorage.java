package com.nijiko.data;

import java.util.LinkedHashSet;
import java.util.Set;

import com.nijiko.permissions.EntryType;

public class NullUserStorage extends NullStorage implements UserStorage {

    public NullUserStorage(String world) {
        super(world);
    }

    @Override
    public EntryType getType() {
        return EntryType.USER;
    }

}
