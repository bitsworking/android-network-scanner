package com.bitsworking.tetheredipfinder.app;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import com.bitsworking.tetheredipfinder.app.utils.Port;
import com.bitsworking.tetheredipfinder.app.utils.Utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class MainActivity extends ListActivity {
    private static final String TAG = "MainActivity";
    private Menu optionsMenu;

    private IPListAdapter adapter;

    private final Port[] PORTS_TO_SCAN = {
            new Port(7, "echo"),
            new Port(21, "ftp"),
            new Port(22, "ssh"),
            new Port(23, "telnet"),
            new Port(25, "smtp"),
            new Port(80, "http"),
            new Port(8000, "http"),
            new Port(8888, "http"),
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = new IPListAdapter(this, null);
        setListAdapter(adapter);
        setContentView(R.layout.activity_main);
        refreshIPs();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.optionsMenu = menu;
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Toast.makeText(this, "Settings", Toast.LENGTH_LONG).show();
                return true;
            case R.id.action_refresh:
                setRefreshActionButtonState(true);
                refreshIPs();
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final MemberDescriptor item = (MemberDescriptor) getListAdapter().getItem(position);

        if (item.ports.size() == 0) {
            Toast.makeText(this, "No open ports found for " + item.ip, Toast.LENGTH_LONG).show();
            return;
        }

        String ports[] = new String[item.ports.size()];
        for (int i=0; i<item.ports.size(); i++)
            ports[i] = "Port " + item.ports.get(i).toString();
//        ports[item.ports.size()] = "Custom port";

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Open " + item.ip)
                .setItems(ports, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Log.v("ListDialog", "Clicked on " + which);

                        if (which == item.ports.size()) {
                            // custom port
                        } else {
                            Port port = item.ports.get(which);
                            String _location = port.name + "://" + item.ip;
                            if ((port.port == 8000) || (port.port == 8888))
                                _location += ":" + port.port;
                            Uri location = Uri.parse(_location);
                            Intent intent = new Intent(Intent.ACTION_VIEW, location);
                            Intent chooser = Intent.createChooser(intent, "Choose");
                            startActivity(chooser);
                        }
                    }
                })
                .create();
        d.show();

    }

    public void setRefreshActionButtonState(final boolean refreshing) {
        if (optionsMenu != null) {
            final MenuItem refreshItem = optionsMenu.findItem(R.id.action_refresh);
            if (refreshItem != null) {
                if (refreshing) {
                    refreshItem.setActionView(R.layout.actionbar_indeterminate_progress);
                } else {
                    refreshItem.setActionView(null);
                }
            }
        }
    }

    private void refreshIPs() {
        adapter.clear();
        adapter.notifyDataSetChanged();

        // 1. Get local IPs
        for (MemberDescriptor md : Utils.getIpAddress()) {
            adapter.add(md);
        }

        // 2. Get IPs from ARP
        NetTask nt = new NetTask();
        nt.execute();
    }

    public ArrayList<MemberDescriptor> getTetheredIPAddresses() {
        ArrayList<MemberDescriptor> ret = new ArrayList<MemberDescriptor>();
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"));
            String line = "";
            while((line = br.readLine()) != null) {
                Log.v(TAG, line);
                String[] parts = line.replaceAll("\\s+", " ").trim().split(" ");

                MemberDescriptor md = new MemberDescriptor();
                md.ip = parts[0];
                md.hw_type = parts[1];
                md.flags = parts[2];
                md.hw_address = parts[3];
                md.mask = parts[4];
                md.device = parts[5];
                ret.add(md);
            }
            br.close();
        }
        catch(Exception e) { Log.e(TAG, e.toString()); }

        if (ret.size() > 0)
            ret.remove(0);
        return ret;
    }

    class NetTask extends AsyncTask<String, Void, ArrayList<MemberDescriptor>> {
        protected ArrayList<MemberDescriptor> doInBackground(String... params) {
            ArrayList<MemberDescriptor> ret = new ArrayList<MemberDescriptor>();
            for (MemberDescriptor md : getTetheredIPAddresses()) {
                try {
                    InetAddress inetAddr = InetAddress.getByName(md.ip);
                    md.hostname = inetAddr.getHostName();
                    md.canonicalHostname = inetAddr.getCanonicalHostName();
                    md.isReachable = inetAddr.isReachable(2000);
                    Log.v(TAG, md.toString());
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ret.add(md);
            }
            return ret;
        }

        protected void onPostExecute(ArrayList<MemberDescriptor> members) {
            for (MemberDescriptor md : members) {
                adapter.add(md);
                if (md.isReachable)
                    new PortScanTask().execute(md);
            }
            adapter.notifyDataSetChanged();
            setRefreshActionButtonState(false);
        }
    }

    class PortScanTask extends AsyncTask<MemberDescriptor, Void, MemberDescriptor> {
        protected MemberDescriptor doInBackground(MemberDescriptor... mds) {
            MemberDescriptor md = mds[0];
            Log.v(TAG, "Start Port Scanning: " + md.ip);
            for (Port port : PORTS_TO_SCAN) {
                try {
                    // Try to create the Socket on the given port.
                    Socket socket = new Socket(md.ip, port.port);
                    // If we arrive here, the port is open!
                    md.ports.add(port);
                    // Don't forget to close it
                    socket.close();
                } catch (IOException e) {
                    // Failed to open the port. Booh.
                }
            }
            md.isPortScanCompleted = true;
            return md;
        }

        protected void onPostExecute(MemberDescriptor md) {
            adapter.notifyDataSetChanged();
//            setRefreshActionButtonState(false);
        }
    }
}
