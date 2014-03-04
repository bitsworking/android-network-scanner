package com.bitsworking.networkscanner.app;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bitsworking.networkscanner.app.utils.Port;

import java.util.ArrayList;

/**
 * Created by chris on 26/02/14.
 */
public class IPListAdapter extends ArrayAdapter<MemberDescriptor> {
    private final Context context;
    public ArrayList<MemberDescriptor> items = new ArrayList<MemberDescriptor>();

    private int colorLightGray;
    private int colorGray;

    public IPListAdapter(Context context, ArrayList<MemberDescriptor> mds) {
        super(context, R.layout.rowlayout, mds);
        this.context = context;

        Resources res = context.getResources();
        colorLightGray = res.getColor(R.color.LightGrey);
        colorGray = res.getColor(R.color.Gray);
    }

    @Override
    public void add(MemberDescriptor item) {
        int i = 0;
        Log.v("MemberDescriptor", "member=" + item.toString());
        for (i=0; i<items.size(); i++) {
//            int ip_end_item = Integer.valueOf(item.ip.split(".")[3]);
//            String[] parts = item.ip.split("[.]");
//            Log.v("MemberDescriptor", "parts=" + parts[3]);
//            if (items.get(i).ip.compareTo(item.ip) > 0)
            if (Integer.valueOf(items.get(i).ip.split("[.]")[2]) == Integer.valueOf(item.ip.split("[.]")[2])) {
                if (item.isInterfaceGroup) // insert group first
                    break;
                if (Integer.valueOf(items.get(i).ip.split("[.]")[3]) > Integer.valueOf(item.ip.split("[.]")[3])) // don't insert before groups
                    break;
            } else if (Integer.valueOf(items.get(i).ip.split("[.]")[2]) > Integer.valueOf(item.ip.split("[.]")[2])) {
                break;
            }
//            Log.v("ITEMS", "ip1=" + items.get(i).ip + ", ip2=" + item.ip);
//            int ip_end_item = Integer.valueOf(item.ip.split(".")[3]);
//            if (ip_end_cursor > ip_end_item)
//                break;
        }
        items.add(i, item);
    }

    @Override
    public void clear() {
        items.clear();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public MemberDescriptor getItem(int position) {
        return items.get(position);
    }

    @Override
    public int getPosition(MemberDescriptor item) {
        return super.getPosition(item);
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private View getInterfaceGroupView() {
        return null;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        MemberDescriptor item = items.get(position);
        // Interface header
        if (item.isInterfaceGroup) {
            View rowView = inflater.inflate(R.layout.rowlayout_header, parent, false);
            ImageView iv_device = (ImageView) rowView.findViewById(R.id.iv_device);
            LinearLayout ll_row = (LinearLayout) rowView.findViewById(R.id.ll_row);
            ll_row.setBackgroundColor(0xfffcfcf2);
            TextView tv_label = (TextView) rowView.findViewById(R.id.label);
            String title = "";
            if (item.device.startsWith("wlan")) {
                title += "Wi-Fi (" + item.device + ")";
                iv_device.setImageResource(R.drawable.wifi);
            } else if (item.device.startsWith("rndis")) {
                title += "USB Tethering (usb" + item.device.replaceFirst("rndis", "") + ")";
                iv_device.setImageResource(R.drawable.usb);
            } else
                title += item.device;
            iv_device.setAlpha(100);
            tv_label.setText(title);

            TextView tv_info = (TextView) rowView.findViewById(R.id.info);
            String info = "IPv4 Subnet: " + item.subnet_ipv4 + "\nIPv4 Broadcast: " + item.broadcast_ipv4;
            if (!item.subnet_ipv6.isEmpty()) info += "\nIPv6 Subnet: " + item.subnet_ipv6;
            tv_info.setText(info);

            return rowView;
        }

        // Normal items
        View rowView = inflater.inflate(R.layout.rowlayout, parent, false);
        LinearLayout ll_row = (LinearLayout) rowView.findViewById(R.id.ll_row);
        TextView tv_label = (TextView) rowView.findViewById(R.id.label);
        TextView tv_info = (TextView) rowView.findViewById(R.id.info);
        ImageView iv_device = (ImageView) rowView.findViewById(R.id.iv_device);

        item.hw_address = item.hw_address.toLowerCase();

        tv_label.setText(item.ip);

        if (item.device.equals("rndis0"))
            item.device = "usb";

        String label = "";
//        if (!item.device.isEmpty()) label += item.device + " - ";

        if (!item.rememberedName.isEmpty()) label += item.rememberedName + " - ";
        if (item.rememberedName.isEmpty() && !item.customDeviceName.isEmpty()) label += item.customDeviceName + " - ";

        if (item.isLocalInterface) {
            label += "Local Android device - ";
            if (!item.hostname.isEmpty()) label += item.hostname + " - ";
            if (!item.hw_address.isEmpty()) label += item.hw_address + " - ";
            tv_label.setTextColor(colorGray);
            tv_info.setTextColor(colorGray);
            iv_device.setImageResource(R.drawable.android1);
            ll_row.setBackgroundColor(0xfffcfcf2);
//            iv_device.setAlpha(150);

        } else if (item.getHostType() == MemberDescriptor.DEVICE_TYPE_ROUTER) {
            // Router
//            tv_label.setTextColor(colorGray);
//            tv_info.setTextColor(colorGray);
            iv_device.setImageResource(R.drawable.router2);

            label += "Probably a Router" + ((item.isReachable) ? "" : " (not reachable)") + " - ";
            if (!item.hostname.isEmpty()) label += item.hostname + " - ";
            if (!item.hw_address.isEmpty()) label += item.hw_address + " - ";

        } else {
            // Normal Host
            if (item.getHostType() == MemberDescriptor.DEVICE_TYPE_HOST_RASPBERRY)
                iv_device.setImageResource(R.drawable.raspberry1);
            else if (item.getHostType() == MemberDescriptor.DEVICE_TYPE_HOST_ANDROID)
                iv_device.setImageResource(R.drawable.android1);

            if (!item.hostname.isEmpty()) label += item.hostname + " - ";
            if (!item.hw_address.isEmpty()) label += item.hw_address + " - ";
//            if (!item.customDeviceName.isEmpty()) label += "\n";
        }

        if (item.ports.size() > 0) {
            label += "Ports: ";
            for (Port port : item.ports) label += port.toString() + ", ";
            if (label.trim().endsWith(",")) label = label.trim().substring(0, label.trim().length()-1);
        } else {
            label = label.trim();
            if (label.endsWith("-")) label = label.substring(0, label.length()-1).trim();
        }

        tv_info.setText(label.trim());

        if (item.isRemembered) {
            ll_row.setBackgroundColor(0x33B4F8A9);
        }

        if (!item.isReachable) {
            tv_label.setTextColor(colorLightGray);
            tv_info.setTextColor(colorLightGray);
            iv_device.setAlpha(100);
        }

        return rowView;
    }
}
