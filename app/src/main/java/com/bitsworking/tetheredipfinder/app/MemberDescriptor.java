package com.bitsworking.tetheredipfinder.app;

import com.bitsworking.tetheredipfinder.app.utils.Port;

import java.util.ArrayList;

/**
 * Created by chris on 26/02/14.
 */
public class MemberDescriptor {
    public String ip = "";
    public String hw_type = "";
    public String flags = "";
    public String hw_address = "";
    public String mask = "";
    public String device = "";

    public boolean isReachable = false;
    public boolean isLocalInterface = false;

    public String hostname = ""; // seems to be the same as up
    public String canonicalHostname = "";  // seems to be the same as up

    public String customDeviceName = "";  // eg. 'USB Looper v1.3'
    public boolean isRaspberry = false;

    public ArrayList<Port> ports = new ArrayList<Port>();
    public boolean isPortScanCompleted = false;

    public String toString() {
        String ret = ip + ", " + hw_type + ", " + flags + ", " + hw_address + ", " + mask + ", " + device + ", reachable=" + isReachable + ", ports: ";
        for (Port port : ports)
            ret += port.toString();
        return ret;
    }
}
