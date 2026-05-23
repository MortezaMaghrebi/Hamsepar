package com.codestoon.hamsepar;

public class ClientItem {
    public String name;
    private String ip;
    private String os;

    public ClientItem(String name, String ip, String os) {
        this.name = name;
        this.ip = ip;
        this.os = os;
    }

    public String getName() { return name; }
    public String getIp() { return ip; }
    public String getOs() { return os; }
}