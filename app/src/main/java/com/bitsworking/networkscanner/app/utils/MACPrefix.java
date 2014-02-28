package com.bitsworking.networkscanner.app.utils;

/**
 * Created by chris on 28/02/14.
 */
public class MACPrefix {
    public String prefix = "";
    public String name = "";
    public int drawable = -1;
    public MACPrefix(String prefix, String name, int drawable) {
        this.prefix = prefix;
        this.name = name;
        this.drawable = drawable;
    }
}
