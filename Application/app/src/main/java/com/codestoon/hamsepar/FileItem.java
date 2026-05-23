package com.codestoon.hamsepar;

public class FileItem {
    private String name;
    private long size;
    private String modified;

    public FileItem(String name, long size, String modified) {
        this.name = name;
        this.size = size;
        this.modified = modified;
    }

    public String getName() { return name; }
    public long getSize() { return size; }
    public String getModified() { return modified; }
}