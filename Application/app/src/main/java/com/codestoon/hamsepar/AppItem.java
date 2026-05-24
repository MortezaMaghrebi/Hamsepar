package com.codestoon.hamsepar;

import android.graphics.drawable.Drawable;

public class AppItem {
    private String appName;
    private String packageName;
    private String sourceDir;
    private Drawable icon;
    private boolean isSelected;

    public AppItem(String appName, String packageName, String sourceDir, Drawable icon) {
        this.appName = appName;
        this.packageName = packageName;
        this.sourceDir = sourceDir;
        this.icon = icon;
        this.isSelected = false;
    }

    public String getAppName() { return appName; }
    public String getPackageName() { return packageName; }
    public String getSourceDir() { return sourceDir; }
    public Drawable getIcon() { return icon; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
}