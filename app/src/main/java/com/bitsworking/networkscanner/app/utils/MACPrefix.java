package com.bitsworking.networkscanner.app.utils;

/**
 * Created by chris on 28/02/14.
 */
public class MACPrefix {
    public String prefix = "";
    public String name = "";
    public int deviceType = 0;
    public MACPrefix(String prefix, String name, int deviceType) {
        this.prefix = prefix;
        this.name = name;
        this.deviceType = deviceType;
    }
}
