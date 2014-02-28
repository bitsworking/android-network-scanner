package com.bitsworking.networkscanner.app.utils;

/**
 * Created by chris on 27/02/14.
 */
public class Port {
    public int port;
    public String name;
    public Port(int port, String name) {
        this.port = port;
        this.name = name;
    }
    public String toString() {
        return String.valueOf(this.port) + " (" + name + ")";
    }
}
