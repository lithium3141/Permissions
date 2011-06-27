package com.nijiko.data;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class NullStorage implements Storage {

    private final String world;
    
    protected NullStorage(String world) {
        this.world = world;
    }
    @Override
    public Set<String> getPermissions(String name) {
        return new HashSet<String>();
    }

    @Override
    public void addPermission(String name, String permission) {
    }

    @Override
    public void removePermission(String name, String permission) {
    }

    @Override
    public LinkedHashSet<GroupWorld> getParents(String name) {
        return new LinkedHashSet<GroupWorld>();
    }

    @Override
    public void addParent(String name, String groupWorld, String groupName) {
    }

    @Override
    public void removeParent(String name, String groupWorld, String groupName) {
    }

    @Override
    public Set<String> getEntries() {
        return new HashSet<String>();
    }

    @Override
    public boolean create(String name) {
        return false;
    }

    @Override
    public boolean delete(String name) {
        return false;
    }

    @Override
    public String getWorld() {
        return world;
    }

    @Override
    public void forceSave() {
    }

    @Override
    public void save() {
    }

    @Override
    public void reload() {
    }

    @Override
    public boolean isAutoSave() {
        return false;
    }

    @Override
    public void setAutoSave(boolean autoSave) {
    }

    @Override
    public String getString(String name, String path) {
        return null;
    }

    @Override
    public Integer getInt(String name, String path) {
        return null;
    }

    @Override
    public Double getDouble(String name, String path) {
        return null;
    }

    @Override
    public Boolean getBool(String name, String path) {
        return null;
    }

    @Override
    public void setData(String name, String path, Object data) {
    }

    @Override
    public void removeData(String name, String path) {
    }

}
