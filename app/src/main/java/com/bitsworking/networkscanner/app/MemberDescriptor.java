package com.bitsworking.networkscanner.app;

import com.bitsworking.networkscanner.app.utils.Port;

import java.util.ArrayList;

/**
 * Created by chris on 26/02/14.
 */
public class MemberDescriptor {
    public static final int DEVICE_TYPE_ROUTER = 1;
    public static final int DEVICE_TYPE_HOST_UNKNOWN = 2;
    public static final int DEVICE_TYPE_HOST_RASPBERRY = 3;
    public static final int DEVICE_TYPE_HOST_ANDROID = 4;

    public String ip = "";
    public String hw_type = "";
    public String flags = "";
    public String hw_address = "";
    public String mask = "";
    public String device = "";
    public String hostname = ""; // seems to be the same as up
    public String canonicalHostname = "";  // seems to be the same as up

    public ArrayList<Port> ports = new ArrayList<Port>();
    public boolean isPortScanCompleted = false;

    public String customDeviceName = "";  // eg. 'USB Looper v1.3'

    public boolean isReachable = false;
    public boolean isLocalInterface = false;

    public int deviceType = 2;
    public boolean isRaspberry = false;
    public boolean isAndroid = false;

    public int getHostType() {
        if (isRaspberry || deviceType == DEVICE_TYPE_HOST_RASPBERRY || hostname.toLowerCase().startsWith("raspberry"))
            return DEVICE_TYPE_HOST_RASPBERRY;

        else if (isLocalInterface || isAndroid || deviceType == DEVICE_TYPE_HOST_ANDROID || hostname.toLowerCase().startsWith("android"))
            return DEVICE_TYPE_HOST_ANDROID;

        else if (ip.endsWith(".1") || deviceType == DEVICE_TYPE_ROUTER)
            return DEVICE_TYPE_ROUTER;

        else
            return DEVICE_TYPE_HOST_UNKNOWN;
    }

    @Override
    public String toString() {
        return "MemberDescriptor{" +
                "ip='" + ip + '\'' +
                ", hw_type='" + hw_type + '\'' +
                ", flags='" + flags + '\'' +
                ", hw_address='" + hw_address + '\'' +
                ", device='" + device + '\'' +
                ", isReachable=" + isReachable +
                ", isLocalInterface=" + isLocalInterface +
                ", hostname='" + hostname + '\'' +
                ", customDeviceName='" + customDeviceName + '\'' +
                ", isRaspberry=" + isRaspberry +
                ", ports=" + ports +
//                ", isPortScanCompleted=" + isPortScanCompleted +
                '}';
    }
//    public String toString() {
//        String ret = "MD<" + ip + ", " + hostname + ", " + hw_type + ", " + flags + ", " + hw_address + ", " + mask + ", " + device + ", reachable=" + isReachable + ", ports: ";
//        for (Port port : ports)
//            ret += port.toString();
//        return ret + ">";
//    }
}
