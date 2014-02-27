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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
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
        setRefreshActionButtonState(true);
        refreshIPs();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.optionsMenu = menu;
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /**
     * Options click
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Toast.makeText(this, "Settings", Toast.LENGTH_LONG).show();
                return true;
            case R.id.action_refresh:
                refreshIPs();
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Handle click on a list item
     */
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

    /**
     * Enable / Disable the Action Bar refresh spinner
     * @param refreshing true to show spinner, false to show refresh icon
     */
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

    /**
     * Refresh all
     */
    private void refreshIPs() {
        setRefreshActionButtonState(true);

        adapter.clear();
        adapter.notifyDataSetChanged();

        // 2. Get IPs from ARP
        NetTask nt = new NetTask();
        nt.execute();
    }

    /**
     * Read ARP cache to build hosts list
     */
    public ArrayList<MemberDescriptor> getHostsFromARPCache() {
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

    /**
     * Background task to get more information about hosts, and to check
     * whether they can be reached.
     *
     * Also searches for 'usblooper' host on the local network.
     */
    class NetTask extends AsyncTask<String, Void, ArrayList<MemberDescriptor>> {
        protected ArrayList<MemberDescriptor> doInBackground(String... params) {
            ArrayList<MemberDescriptor> ret = new ArrayList<MemberDescriptor>();

            // 1. Get local IPs
            for (MemberDescriptor md : Utils.getIpAddress()) {
                ret.add(md);
            }

            // 2. Get ARP Cache
            for (MemberDescriptor md : getHostsFromARPCache()) {
                try {
                    InetAddress inetAddr = InetAddress.getByName(md.ip);
                    md.hostname = inetAddr.getHostName();
                    md.canonicalHostname = inetAddr.getCanonicalHostName();
                    md.isReachable = inetAddr.isReachable(1000);
                    Log.v(TAG, md.toString());
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ret.add(md);
            }

            // Try to find 'usblooper' hostname on the local network
            try {
                InetAddress inetAddr = InetAddress.getByName("usblooper");

                // If a 'usblooper' host is found, check whether its already in the
                // discovered arp hosts, else create a new MemberDescriptor
                boolean usbLooperAlreadyFound = false;
                for (MemberDescriptor md : ret) {
                    if (md.ip.equals(inetAddr.getHostAddress())) {
                        md.customDeviceName = "USB Looper";
                        md.isRaspberry = true;
                        usbLooperAlreadyFound = true;
                    }
                }

                if (!usbLooperAlreadyFound) {
                    MemberDescriptor md = new MemberDescriptor();
                    md.ip = inetAddr.getHostAddress();
                    md.customDeviceName = "USB Looper";
                    md.isReachable = inetAddr.isReachable(2000);
                    md.isRaspberry = true;
                    ret.add(md);
                }
            } catch (UnknownHostException e) {
//                e.printStackTrace();
            } catch (IOException e) {
//                e.printStackTrace();
            }

            return ret;
        }

        /**
         * After building the list of hosts, start port-scan and USBLooper check
         */
        protected void onPostExecute(ArrayList<MemberDescriptor> members) {
            for (MemberDescriptor md : members) {
                adapter.add(md);
                if (md.isReachable) {
                    new PortScanTask().execute(md);
                    new USBLooperScanTask().execute(md);
                }
            }
            adapter.notifyDataSetChanged();
            setRefreshActionButtonState(false);
        }
    }

    /**
     * Task to execute a port-scan on a specific ip
     */
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
        }
    }

    /**
     * Task to check whether this is a USBLooper Raspberry
     */
    class USBLooperScanTask extends AsyncTask<MemberDescriptor, Void, MemberDescriptor> {
        private String getWebserverInfo(String ip, int port) {
            Log.v(TAG, "Check " + ip +":" + port);
            try {
                URL url = new URL("http://" + ip + ":" + port + "/sup/what");
                InputStream is = (InputStream) url.getContent();
                return (new BufferedReader(new InputStreamReader(is))).readLine();
            } catch (MalformedURLException e) {
//                e.printStackTrace();
            } catch (IOException e) {
//                e.printStackTrace();
            }
            return null;
        }

        protected MemberDescriptor doInBackground(MemberDescriptor... mds) {
            MemberDescriptor md = mds[0];
            for (Port port : md.ports) {
                if (port.name.equals("http")) {
                    String s = getWebserverInfo(md.ip, port.port);
                    if (s != null) {
                        md.customDeviceName = s;
                        md.isRaspberry = true;
                    }
                }
            }
            return md;
        }

        protected void onPostExecute(MemberDescriptor md) {
            adapter.notifyDataSetChanged();
        }
    }

}
