package com.bitsworking.networkscanner.app.utils;

import android.content.SharedPreferences;
import android.util.Log;

import com.bitsworking.networkscanner.app.MemberDescriptor;

import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

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

                        byte[] mac = intf.getHardwareAddress();
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < mac.length; i++) {
                            sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
                        }

                        Log.v("IP address", ipAddress +
                                ", iface=" + intf.getDisplayName() +
                                "hwaddr=" + sb.toString());

                        MemberDescriptor md = new MemberDescriptor(settings);
                        md.isInterfaceGroup = true;
                        md.isLocalInterface = true;
                        md.ip = ipAddress;
                        md.device = intf.getDisplayName();
                        md.hw_address = sb.toString().replace("-", ":");
                        md.isReachable = true;

                        List<InterfaceAddress> interfaceAddresses = intf.getInterfaceAddresses();
                        for (InterfaceAddress ia : interfaceAddresses) {
                            String ia_str = ia.getAddress().toString();
                            if (ia_str.startsWith("/")) ia_str = ia_str.substring(1);

                            Log.v("IP address", ia.toString() +
                                    ", address=" +  ia_str +
                                    "/ " + ia.getNetworkPrefixLength() +
                                    ", hostname: " + ia.getAddress().getHostName() +
                                    ", chostname: " + ia.getAddress().getCanonicalHostName() +
                                    ", broadcast: " + ia.getBroadcast());


                            String bc_str = (ia.getBroadcast() != null) ? ia.getBroadcast().toString() : "";
                            if (bc_str.startsWith("/")) bc_str = bc_str.substring(1);

                            if (ia_str.contains(":")) {
                                // IPv6
                                if (ia_str.contains("%")) ia_str = ia_str.substring(0, ia_str.indexOf("%"));
                                md.subnet_ipv6 = ia_str + "/" + ia.getNetworkPrefixLength();
                                md.broadcast_ipv6 = bc_str;
                            } else if (ia_str.contains(".")) {
                                // IPv4
                                md.subnet_ipv4 = ia_str + "/" + ia.getNetworkPrefixLength();
                                md.broadcast_ipv4 = bc_str;
                                md.hostname = ia.getAddress().getHostName();
                            }
                        }

                        Log.v("IP Address", "=== md: " + md.toString());
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
