package com.bitsworking.networkscanner.app;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
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
     * Start network scan
     */
    private void refreshIPs() {
        isRefreshing = true;
        setRefreshActionButtonState(true);

        adapter.clear();
        adapter.notifyDataSetChanged();

        startNetworkScanThread();
    }

    private void addListViewItem(final MemberDescriptor md) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.add(md);
            }
        });
    }

    /**
     * Get Host infos from ARP cache
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

        // Remove first line (text header)
        if (ret.size() > 0)
            ret.remove(0);
        return ret;
    }

    /**
     * Main Network Scanner Thread. Starts all other threads and waits until finished.
     */
    private void startNetworkScanThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<MemberDescriptor> _hosts = new ArrayList<MemberDescriptor>();

                // For each subnet, do a network scan
                for (MemberDescriptor _md : Utils.getIpAddress()) {
                    String[] ipParts = _md.ip.split("[.]");
                    String ipPrefix = ipParts[0] + "." + ipParts[1] + "." + ipParts[2] + ".";

                    final int ipMin = 1;
                    final int ipMax = 254;
                    Log.v(TAG, "Start ip scan from " + ipPrefix + ipMin + " to " + ipPrefix + ipMax);

                    // Start Threads which check if IP is reachable
                    Thread[] scanThreads = new Thread[ipMax - ipMin + 1];
                    for (int i=ipMin; i<=ipMax; i++) {
                        MemberDescriptor newHost = new MemberDescriptor();
                        newHost.ip = ipPrefix + i;
                        _hosts.add(newHost);
                        scanThreads[i-ipMin] = startIPScanThread(newHost);
                    }

                    // Wait for threads to finish
                    Log.v(TAG, "Waiting for Threads...");
                    for (int i=0; i<scanThreads.length; i++) {
                        try {
                            scanThreads[i].join();
                        } catch (InterruptedException e) {}
                    }

                    // Short pause seems to be necessary for ARP cache to update
                    Log.v(TAG, "Threads finished.");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // Remove unreachable hosts
                    for (int i = _hosts.size()-1; i >= 0; i--){
                        if (!_hosts.get(i).isReachable) {
                            _hosts.remove(i);
                        }
                    }

                    // Update available hosts with ARP information and other infos
                    ArrayList<MemberDescriptor> arpHosts = getHostsFromARPCache();
                    for (MemberDescriptor md : _hosts) {
                        for (MemberDescriptor arpHost : arpHosts) {
                            if (arpHost.ip.equals(md.ip)) {
                                md.hw_address = arpHost.hw_address;
                                md.hw_type = arpHost.hw_type;
                                md.device = arpHost.device;
                                md.flags = arpHost.flags;
                                break;
                            }
                        }

                        // Check whether MAC prefix matches one of our presets
                        for (MACPrefix macPrefix : Settings.MAC_PREFIXES) {
                            if (macPrefix.prefix.toLowerCase().equals(md.hw_address.toLowerCase())) {
                                md.customDeviceName = macPrefix.name;
                                md.deviceType = macPrefix.deviceType;
                            }
                        }

                        // Check whether this is a local interface ip
                        if (md.ip.equals(_md.ip)) {
                            md.isLocalInterface = true;
                        }

                        // Add item to ListView adapter
                        addListViewItem(md);

                        Log.v(TAG, "+ Host: " + md.toString());
                    }

                    // Items have been added to adapter. Refresh ListView now.
                    mHandler.post(new Runnable() {
                        public void run() {
                            adapter.notifyDataSetChanged();
                        }
                    });

                    // Start port scanning
                    Thread[] portScanThreads = new Thread[_hosts.size()];
                    for (int i=0; i<_hosts.size(); i++) {
                        portScanThreads[i] = startPortScanThread(_hosts.get(i));
                    }

                    // Wait for port-scanning threads to finish
                    Log.v(TAG, "Waiting for Portscan Threads...");
                    for (int i=0; i<portScanThreads.length; i++) {
                        try {
                            portScanThreads[i].join();
                        } catch (InterruptedException e) {}
                    }

                    // All done. Last UI update for this refresh!
                    mHandler.post(new Runnable() {
                        public void run() {
                            adapter.notifyDataSetChanged();
                            setRefreshActionButtonState(false);
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Checks whether a given IP can be reached
     */
    private Thread startIPScanThread(final MemberDescriptor md) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
//                Log.v(TAG, "Start ip scan for " + ip);
                try {
                    InetAddress inetAddr = InetAddress.getByName(md.ip);
                    if (!inetAddr.getHostName().equals(md.ip))
                        md.hostname = inetAddr.getHostName();
                    if (!inetAddr.getCanonicalHostName().equals(md.ip))
                        md.canonicalHostname = inetAddr.getCanonicalHostName();
                    md.isReachable = inetAddr.isReachable(1000);
                } catch (UnknownHostException e) {
                } catch (IOException e) {}
            }
        });
        t.start();
        return t;
    }

    /**
     * Does a portscan and webserver scan on a given IP
     */
    private Thread startPortScanThread(final MemberDescriptor md) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "Start port+server scan for " + md.ip);
                for (Port port : Settings.PORTS_TO_SCAN) {
                    try {
                        // Try to create the Socket on the given port.
                        Socket socket = new Socket(md.ip, port.port);
                        // If we arrive here, the port is open!
                        md.ports.add(port);
                        // Don't forget to close it
                        socket.close();
                    } catch (IOException e) {
                        // Failed to open the port. Socket is closed.
                    }
                }

                // Custom Bitsworking webserver scan
                for (Port port : md.ports) {
                    if (port.name.equals("http")) {
                        try {
                            URL url = new URL("http://" + md.ip + ":" + port + "/sup/what");
                            InputStream is = (InputStream) url.getContent();
                            md.customDeviceName = (new BufferedReader(new InputStreamReader(is))).readLine();
                        } catch (MalformedURLException e) {
                        } catch (IOException e) {
                        }
                    }
                }

                md.isPortScanCompleted = true;
            }
        });
        t.start();
        return t;
    }
}
