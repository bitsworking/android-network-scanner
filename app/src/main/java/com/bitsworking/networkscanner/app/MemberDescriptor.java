package com.bitsworking.networkscanner.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

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

    public boolean isRemembered = false;
    public String rememberedName = "";

    // Interface Groups will be displayed differently than normal items
    public boolean isInterfaceGroup = false;
    public String subnet_ipv4 = "";
    public String broadcast_ipv4 = "";
    public String subnet_ipv6 = "";
    public String broadcast_ipv6 = "";
    public boolean isHeaderToggled = false;

    private SharedPreferences settings;
    SharedPreferences.Editor editor;

    public MemberDescriptor(SharedPreferences settings) {
        this.settings = settings;
        this.editor = settings.edit();
    }

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
                ", isRemembered=" + isRemembered+
                ", ports=" + ports +
                ", isInterfaceGroup=" + isInterfaceGroup +
                ", ipv4 subnet=" + subnet_ipv4 +
                ", ipv6 subnet=" + subnet_ipv6 +
                ", ipv4 broadcast=" + broadcast_ipv4 +
                ", ipv6 broadcast=" + broadcast_ipv6 +
                ", ports=" + ports +
//                ", isPortScanCompleted=" + isPortScanCompleted +
                '}';
    }

    public boolean loadFromMemory() {
        if (hw_address.isEmpty()) return false;
        isRemembered = settings.getBoolean("isRemembered:" + hw_address, false);
        if (isRemembered) {
            this.rememberedName = settings.getString("rememberedName:" + hw_address, "");
        }
        Log.v("MemberDescriptor", "isRemembered " + hw_address + ": " + isRemembered);
        return isRemembered;
    }

    public void saveToMemory() {
        if (hw_address.isEmpty()) return;
        editor.putBoolean("isRemembered:" + hw_address, isRemembered);
        editor.putString("rememberedName:" + hw_address, rememberedName);
        editor.commit();
    }
}
