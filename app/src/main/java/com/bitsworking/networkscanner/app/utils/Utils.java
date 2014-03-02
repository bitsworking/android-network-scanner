package com.bitsworking.networkscanner.app.utils;

import android.content.SharedPreferences;
import android.util.Log;

import com.bitsworking.networkscanner.app.MemberDescriptor;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

/**
 * Created by chris on 27/02/14.
 */
public class Utils {
    public static ArrayList<MemberDescriptor> getIpAddress(final SharedPreferences settings) {
        ArrayList<MemberDescriptor> ret = new ArrayList<MemberDescriptor>();
        try {
            for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = (NetworkInterface) en.nextElement();
                for (Enumeration enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = (InetAddress) enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()&&inetAddress instanceof Inet4Address) {
                        String ipAddress = inetAddress.getHostAddress();
                        Log.e("IP address", ipAddress + ", iface=" + intf.getDisplayName() + "/" + intf.getName());

                        MemberDescriptor md = new MemberDescriptor(settings);
                        md.ip = ipAddress;
                        md.device = intf.getName();
                        md.isReachable = true;
                        md.isLocalInterface = true;
                        ret.add(md);
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("Socket exception in GetIP Address of Utilities", ex.toString());
        }
        return ret;
    }
}
