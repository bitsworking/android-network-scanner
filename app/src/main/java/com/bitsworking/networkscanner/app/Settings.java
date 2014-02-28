package com.bitsworking.networkscanner.app;

import com.bitsworking.networkscanner.app.utils.MACPrefix;
import com.bitsworking.networkscanner.app.utils.Port;

/**
 * Created by chris on 28/02/14.
 */
public final class Settings {
    public final static Port[] PORTS_TO_SCAN = {
            new Port(7, "echo"),
            new Port(21, "ftp"),
            new Port(22, "ssh"),
            new Port(23, "telnet"),
            new Port(25, "smtp"),
            new Port(80, "http"),
            new Port(8000, "http"),
            new Port(8888, "http"),
    };

//    private final String[] HOSTNAMES_RASPBERRY = {
//        "usblooper", "raspberrypi"
//    };


    public final static MACPrefix[] MAC_PREFIXES = {
            new MACPrefix("B8:27:EB", "Raspberry Pi", R.drawable.raspberry1)
    };

}
