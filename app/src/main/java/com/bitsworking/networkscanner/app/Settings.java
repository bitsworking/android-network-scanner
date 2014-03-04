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
            new Port(37, "time"),
            new Port(43, "whois"),
            new Port(53, "dns"),
            new Port(69, "tftp"),
            new Port(70, "gopher"),
            new Port(79, "finger"),
            new Port(80, "http"),
            new Port(110, "pop3"),
            new Port(123, "ntp"),
            new Port(139, "netbios"),
            new Port(161, "snmp"),
            new Port(311, "http"),
            new Port(443, "http"),
            new Port(444, "snpp"),
            new Port(3306, "mysql"),
            new Port(6379, "redis"),
            new Port(8000, "http"),
            new Port(8888, "http"),
            new Port(11211, "memcache"),
    };

//    private final String[] HOSTNAMES_RASPBERRY = {
//        "usblooper", "raspberrypi"
//    };


    public final static MACPrefix[] MAC_PREFIXES = {
            new MACPrefix("B8:27:EB", "Raspberry Pi", MemberDescriptor.DEVICE_TYPE_HOST_RASPBERRY)
    };

}
