package com.bitsworking.networkscanner.app;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
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
        items.add(item);
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

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.rowlayout, parent, false);
        TextView tv_label = (TextView) rowView.findViewById(R.id.label);
        TextView tv_info = (TextView) rowView.findViewById(R.id.info);
        ImageView iv_device = (ImageView) rowView.findViewById(R.id.iv_device);

        MemberDescriptor item = items.get(position);
        tv_label.setText(item.ip);

        if (item.device.equals("rndis0"))
            item.device = "usb";

        String label = "";
        if (!item.device.isEmpty()) label += item.device + " - ";
        if (!item.customDeviceName.isEmpty()) label += item.customDeviceName + " - ";
        if (item.isLocalInterface) {
            label += "Local Android IP";
            if (!item.hostname.isEmpty()) label += " - " + item.hostname;
            tv_label.setTextColor(colorGray);
            tv_info.setTextColor(colorGray);
            iv_device.setImageResource(R.drawable.android1);
            iv_device.setAlpha(150);

        } else if (item.getHostType() == MemberDescriptor.DEVICE_TYPE_ROUTER) {
            // Router
            label += "Probably a Router" + ((item.isReachable) ? "" : " (not reachable)");
            if (!item.hostname.isEmpty()) label += " - " + item.hostname;
            tv_label.setTextColor(colorGray);
            tv_info.setTextColor(colorGray);
            iv_device.setImageResource(R.drawable.router2);

        } else {
            // Normal Host
            if (!item.customDeviceName.isEmpty()) label += item.customDeviceName + " - ";
            if (!item.hostname.isEmpty()) label += item.hostname;
            if (!item.customDeviceName.isEmpty() && item.isReachable) label += "\n";

            if (item.getHostType() == MemberDescriptor.DEVICE_TYPE_HOST_RASPBERRY)
                iv_device.setImageResource(R.drawable.raspberry1);

            else if (item.getHostType() == MemberDescriptor.DEVICE_TYPE_HOST_ANDROID)
                iv_device.setImageResource(R.drawable.android1);
        }

        if (item.ports.size() > 0) {
            label += " - Ports: ";
            for (Port port : item.ports) label += port.toString() + ", ";
            if (label.trim().endsWith(",")) label = label.trim().substring(0, label.trim().length()-1);
        }

        tv_info.setText(label);

        if (!item.isReachable) {
            tv_label.setTextColor(colorLightGray);
            tv_info.setTextColor(colorLightGray);
            iv_device.setAlpha(100);
        }

        return rowView;
    }
}
