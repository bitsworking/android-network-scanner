package com.bitsworking.networkscanner.app;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import com.bitsworking.networkscanner.app.utils.MACPrefix;
import com.bitsworking.networkscanner.app.utils.Port;
import com.bitsworking.networkscanner.app.utils.Utils;

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
    private static final String TAG = "NetworkScanner.MainActivity";
    private Menu optionsMenu;
    private IPListAdapter adapter;

    private boolean resumeHasRun = false;
    private boolean isRefreshing = false;
    private int bgTasksRunning = 0;  // Counter to hide refresh spinner when all is done

    // Hosts list built up during search
    private ArrayList<MemberDescriptor> _hosts = new ArrayList<MemberDescriptor>();

    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        adapter = new IPListAdapter(this, null);
        setListAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!resumeHasRun) {
            refreshIPs();
            resumeHasRun = true;
            return;
        }
        // Normal case behavior follows
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.optionsMenu = menu;
        getMenuInflater().inflate(R.menu.main, menu);

        // Since we refresh at app start, auto-show spinner
        if (isRefreshing)
            setRefreshActionButtonState(true);
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
        isRefreshing = true;
        setRefreshActionButtonState(true);

        adapter.clear();
        adapter.notifyDataSetChanged();

        // 2. Get IPs from ARP
//        NetTask nt = new NetTask();
//        nt.execute();
        startNetworkScanThread();
    }

    private void refreshingDone() {
        isRefreshing = false;
        setRefreshActionButtonState(false);
    }

    private void addListViewItem(final MemberDescriptor md) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                adapter.add(md);
            }
        });
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
                String[] parts = line.replaceAll("\\s+", " ").trim().split(" ");

                MemberDescriptor md = new MemberDescriptor();
                md.ip = parts[0];
                md.hw_type = parts[1];
                md.flags = parts[2];
                md.hw_address = parts[3];
                md.mask = parts[4];
                md.device = parts[5];
//                Log.v(TAG, "arpHost: " + md.toString());
                ret.add(md);
            }
            br.close();
        }
        catch(Exception e) { Log.e(TAG, e.toString()); }

        // Remote first line (text header)
        if (ret.size() > 0)
            ret.remove(0);
        return ret;
    }

    private void startNetworkScanThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (MemberDescriptor _md : Utils.getIpAddress()) {
                    // For each subnet, check
                    String[] ipParts = _md.ip.split("[.]");
                    String ipPrefix = ipParts[0] + "." + ipParts[1] + "." + ipParts[2] + ".";

                    final int ipMin = 1;
                    final int ipMax = 110;
                    Log.v(TAG, "Start ip scan from " + ipPrefix + ipMin + " to " + ipPrefix + ipMax);

                    Thread[] scanThreads = new Thread[ipMax - ipMin + 1];
                    for (int i=ipMin; i<=ipMax; i++) {
                        scanThreads[i-ipMin] = startIPScanThread(ipPrefix + i);
                    }

                    Log.v(TAG, "Waiting for Threads...");
                    for (int i=0; i<scanThreads.length; i++) {
                        try {
                            scanThreads[i].join();
                        } catch (InterruptedException e) {}
                    }

                    Log.v(TAG, "Threads finished.");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    ArrayList<MemberDescriptor> arpHosts = getHostsFromARPCache();
                    for (MemberDescriptor md : _hosts) {
                        // Update with arp cache information
                        for (MemberDescriptor arpHost : arpHosts) {
                            if (arpHost.ip.equals(md.ip)) {
                                md.hw_address = arpHost.hw_address;
                                md.hw_type = arpHost.hw_type;
                                md.device = arpHost.device;
                                md.flags = arpHost.flags;
                            }
                        }

                        if (md.ip.equals(_md.ip)) {
                            md.isLocalInterface = true;
                            // we dont get any more information about the local
                        }

                        Log.v(TAG, "- Host updated: " + md.toString());

                    }
                }
            }
        }).start();
    }

    private Thread startIPScanThread(final String ip) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
//                Log.v(TAG, "Start ip scan for " + ip);
                MemberDescriptor md = new MemberDescriptor();
                md.ip = ip;

                try {
                    InetAddress inetAddr = InetAddress.getByName(md.ip);
                    md.hostname = inetAddr.getHostName();
                    md.canonicalHostname = inetAddr.getCanonicalHostName();
                    md.isReachable = inetAddr.isReachable(1000);

                    if (md.hostname.startsWith("raspberry"))
                        md.isRaspberry = true;

                    if (md.isReachable)
                        _hosts.add(md);
                } catch (UnknownHostException e) {
                } catch (IOException e) {}
            }
        });
        t.start();
        return t;
    }
























    /**
     * Background task to get more information about hosts, and to check
     * whether they can be reached.
     *
     * Also searches for 'usblooper' host on the local network.
     */
    class NetworkScanTask extends AsyncTask<Void, Void, ArrayList<MemberDescriptor>> {
        protected ArrayList<MemberDescriptor> doInBackground(Void... params) {
            ArrayList<MemberDescriptor> ret = new ArrayList<MemberDescriptor>();

            // 1. Get local IPs
            for (MemberDescriptor md : Utils.getIpAddress()) {
                ret.add(md);
            }
//
//            // 2. Get ARP Cache
//            for (MemberDescriptor md : getHostsFromARPCache()) {
//                try {
//                    InetAddress inetAddr = InetAddress.getByName(md.ip);
//                    md.hostname = inetAddr.getHostName();
//                    md.canonicalHostname = inetAddr.getCanonicalHostName();
//                    md.isReachable = inetAddr.isReachable(1000);
//                    Log.v(TAG, md.toString());
//                } catch (UnknownHostException e) {
//                    e.printStackTrace();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                ret.add(md);
//            }
//
//            // Try to find custom Raspberry hostnames on the local network
//            for (String hostname : HOSTNAMES_RASPBERRY) {
//                Log.v(TAG, "Trying to find hostname " + hostname + "...");
//                try {
//                    InetAddress inetAddr = InetAddress.getByName(hostname);
//
//                    // If hostname is found, check whether its already in the
//                    // discovered arp hosts, else create a new MemberDescriptor
//                    boolean hostnameAlreadyFound = false;
//                    for (MemberDescriptor md : ret) {
//                        if (md.ip.equals(inetAddr.getHostAddress())) {
//                            md.customDeviceName = hostname;
//                            md.isRaspberry = true;
//                            hostnameAlreadyFound = true;
//                        }
//                    }
//
//                    if (!hostnameAlreadyFound) {
//                        MemberDescriptor md = new MemberDescriptor();
//                        md.ip = inetAddr.getHostAddress();
//                        md.customDeviceName = hostname;
//                        md.isReachable = inetAddr.isReachable(2000);
//                        md.isRaspberry = true;
//                        ret.add(md);
//                    }
//                } catch (UnknownHostException e) {
//                } catch (IOException e) {
//                }
//            }

            return ret;
        }
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

            // Try to find custom Raspberry hostnames on the local network
//            for (String hostname : HOSTNAMES_RASPBERRY) {
//                Log.v(TAG, "Trying to find hostname " + hostname + "...");
//                try {
//                    InetAddress inetAddr = InetAddress.getByName(hostname);
//
//                    // If hostname is found, check whether its already in the
//                    // discovered arp hosts, else create a new MemberDescriptor
//                    boolean hostnameAlreadyFound = false;
//                    for (MemberDescriptor md : ret) {
//                        if (md.ip.equals(inetAddr.getHostAddress())) {
//                            md.customDeviceName = hostname;
//                            md.isRaspberry = true;
//                            hostnameAlreadyFound = true;
//                        }
//                    }
//
//                    if (!hostnameAlreadyFound) {
//                        MemberDescriptor md = new MemberDescriptor();
//                        md.ip = inetAddr.getHostAddress();
//                        md.customDeviceName = hostname;
//                        md.isReachable = inetAddr.isReachable(2000);
//                        md.isRaspberry = true;
//                        ret.add(md);
//                    }
//                } catch (UnknownHostException e) {
//                } catch (IOException e) {
//                }
//            }

            return ret;
        }

        /**
         * After building the list of hosts, start port-scan and USBLooper check
         */
        protected void onPostExecute(ArrayList<MemberDescriptor> members) {
            Log.v(TAG, "NetTask PostExecute...");
            for (MemberDescriptor md : members) {
                adapter.add(md);
                if (md.isReachable && !md.isLocalInterface) {
                    bgTasksRunning += 2;
                    new PortScanTask().execute(md);
                    new USBLooperScanTask().execute(md);
                }
            }
            adapter.notifyDataSetChanged();
            Log.v(TAG, "NetTask PostExecute -- bgTasks: " + bgTasksRunning);
            if (bgTasksRunning == 0) {
                setRefreshActionButtonState(false);
            }
        }
    }

    /**
     * Task to execute a port-scan on a specific ip
     */
    class PortScanTask extends AsyncTask<MemberDescriptor, Void, MemberDescriptor> {
        protected MemberDescriptor doInBackground(MemberDescriptor... mds) {
            MemberDescriptor md = mds[0];
            Log.v(TAG, "Start Port Scanning: " + md.ip);
//            for (Port port : PORTS_TO_SCAN) {
//                try {
//                    // Try to create the Socket on the given port.
//                    Socket socket = new Socket(md.ip, port.port);
//                    // If we arrive here, the port is open!
//                    md.ports.add(port);
//                    // Don't forget to close it
//                    socket.close();
//                } catch (IOException e) {
//                    // Failed to open the port. Booh.
//                }
//            }
            md.isPortScanCompleted = true;
            return md;
        }

        protected void onPostExecute(MemberDescriptor md) {
            adapter.notifyDataSetChanged();
            if (--bgTasksRunning == 0)
                setRefreshActionButtonState(false);
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
            if (--bgTasksRunning == 0)
                setRefreshActionButtonState(false);
        }
    }

}
