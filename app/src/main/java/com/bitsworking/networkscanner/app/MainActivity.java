package com.bitsworking.networkscanner.app;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.bitsworking.networkscanner.app.utils.MACPrefix;
import com.bitsworking.networkscanner.app.utils.Port;
import com.bitsworking.networkscanner.app.utils.Utils;
import com.google.analytics.tracking.android.EasyTracker;

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
    private static final String VERSION = "0.2.1";
    private static final String TAG = "NetworkScanner.MainActivity";
    public static final String PREFS_NAME = "NetworkScannerPrefs";
    private static final boolean TEST_FOR_BITSWORKING_WEBSERVER = false;

    private Menu optionsMenu;
    private IPListAdapter adapter;

    private boolean resumeHasRun = false;
    private boolean isRefreshing = false;
    private Handler mHandler = new Handler();
    private SharedPreferences settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = getSharedPreferences(PREFS_NAME, 0);
        adapter = new IPListAdapter(this, null);
        setListAdapter(adapter);

        final ListView mListView = getListView();
        mListView.setLongClickable(true);
        mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int pos, long id) {
//                Toast.makeText(MainActivity.this, "long clicked - pos: " + pos, Toast.LENGTH_LONG).show();
                customizeMember(adapter.items.get(pos));
                return true;
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        EasyTracker.getInstance(this).activityStart(this);  // Add this method.
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
    public void onStop() {
        super.onStop();
        EasyTracker.getInstance(this).activityStop(this);  // Add this method.
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.activity_main);
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
                Toast.makeText(this, "Settings coming soon", Toast.LENGTH_LONG).show();
                return true;

            case R.id.action_refresh:
                refreshIPs();
                return true;

            case R.id.action_about:
                showAboutDialog();
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
        if (item.isInterfaceGroup || (item.hw_address.isEmpty() && item.ports.size() == 0)) {
            Toast.makeText(this, "Nothing to do here", Toast.LENGTH_SHORT).show();
            return;
        }

        String ports[] = new String[item.ports.size() + 1];
        for (int i=0; i<item.ports.size(); i++)
            ports[i] = "Open port " + item.ports.get(i).toString();

        if (!item.hw_address.isEmpty())
            ports[item.ports.size()] = "Remember";

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(item.hw_address + ((!item.hostname.isEmpty()) ? " - " + item.hostname : "") + "\n(" + item.ip + ")")
                .setItems(ports, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Log.v("ListDialog", "Clicked on " + which);

                        if (which == item.ports.size()) {
                            // custom port
                            customizeMember(item);

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

                MemberDescriptor md = new MemberDescriptor(settings);
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

                ArrayList<MemberDescriptor> localInterfaces = Utils.getIpAddress(settings);
                for (MemberDescriptor localInterface : localInterfaces) {
                    // For each subnet
                    // - create local device
                    MemberDescriptor local = new MemberDescriptor(settings);
                    local.ip = localInterface.ip;
                    local.hw_address = localInterface.hw_address;
                    local.device = localInterface.device;
                    local.hostname = localInterface.hostname;
                    local.isReachable = true;
                    local.isAndroid = true;
                    local.isLocalInterface = true;

                    // - add the network group header
                    localInterface.ip = localInterface.ip.substring(0, localInterface.ip.lastIndexOf(".") + 1) + "0";
                    addListViewItem(localInterface);
                    addListViewItem(local);
                    mHandler.post(new Runnable() {
                        public void run() {
                            adapter.notifyDataSetChanged();
                        }
                    });

                    // - do a network scan
                    String[] ipParts = localInterface.ip.split("[.]");
                    String ipPrefix = ipParts[0] + "." + ipParts[1] + "." + ipParts[2] + ".";

                    final int ipMin = 1;
                    final int ipMax = 254;
                    Log.v(TAG, "Start ip scan from " + ipPrefix + ipMin + " to " + ipPrefix + ipMax);

                    // Start Threads which check if IP is reachable
                    Thread[] scanThreads = new Thread[ipMax - ipMin + 1];
                    for (int i=ipMin; i<=ipMax; i++) {
                        MemberDescriptor newHost = new MemberDescriptor(settings);
                        newHost.ip = ipPrefix + i;
                        if (newHost.ip.equals(local.ip))
                            continue;
                        _hosts.add(newHost);
                        scanThreads[i-ipMin] = startIPScanThread(newHost);
                    }

                    // Wait for threads to finish
                    Log.v(TAG, "Waiting for Threads...");
                    for (int i=0; i<scanThreads.length; i++) {
                        try {
                            if (scanThreads[i] != null)
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
                }

                // Remove unreachable hosts
                for (int i = _hosts.size()-1; i >= 0; i--){
                    if (!_hosts.get(i).isReachable) {
                        _hosts.remove(i);
                    }
                }

                // For all reachable hosts, enhance their info if possible
                for (MemberDescriptor md : _hosts) {
                    // ARP cache info
                    for (MemberDescriptor arpHost : getHostsFromARPCache()) {
                        if (arpHost.ip.equals(md.ip)) {
                            if (!arpHost.hw_address.isEmpty())
                                md.hw_address = arpHost.hw_address;
                            md.hw_type = arpHost.hw_type;
                            md.device = arpHost.device;
                            md.flags = arpHost.flags;
                            break;
                        }
                    }

                    // Load remembered infos
                    md.loadFromMemory();

                    // Check whether MAC prefix matches one of our presets
                    for (MACPrefix macPrefix : Settings.MAC_PREFIXES) {
                        if (md.hw_address.toLowerCase().startsWith(macPrefix.prefix.toLowerCase())) {
                            md.customDeviceName = macPrefix.name;
                            md.deviceType = macPrefix.deviceType;
                        }
                    }

                    // Add item to ListView adapter
                    addListViewItem(md);
//                    Log.v(TAG, "+ Host: " + md.toString());
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
                    md.isReachable = inetAddr.isReachable(2000);
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
                if (TEST_FOR_BITSWORKING_WEBSERVER) {
                    for (Port port : md.ports) {
                        if (port.name.equals("http")) {
                            try {
                                URL url = new URL("http://" + md.ip + ":" + port.port + "/sup/what");
                                InputStream is = (InputStream) url.getContent();
                                md.customDeviceName = (new BufferedReader(new InputStreamReader(is))).readLine();
                            } catch (MalformedURLException e) {
    //                            e.printStackTrace();
                            } catch (IOException e) {
    //                            e.printStackTrace();
                            }
                        }
                    }
                }

                md.isPortScanCompleted = true;
            }
        });
        t.start();
        return t;
    }

    private void customizeMember(final MemberDescriptor md) {
        // custom dialog
        if (md.isInterfaceGroup) return;
        LayoutInflater inflater = getLayoutInflater();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String title = md.hw_address + ((!md.hostname.isEmpty()) ? " - " + md.hostname : "") + "\n(" + md.ip + ")";

        View v = inflater.inflate(R.layout.dialog_customize_host, null);
        final EditText txt = (EditText) v.findViewById(R.id.txt);
        txt.setText(md.rememberedName);
        txt.setSelection(md.rememberedName.length());
        final Button btn_clear = (Button) v.findViewById(R.id.btn_clear);
        btn_clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                txt.setText("");
            }
        });

        builder.setView(v)
                .setTitle(title)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        final String name = txt.getText().toString();
                        if (name.isEmpty()) {
                            md.rememberedName = "";
                            md.isRemembered = false;
                        } else {
                            md.rememberedName = name;
                            md.isRemembered = true;
                        }
                        md.saveToMemory();
                        Log.v(TAG, "remembered " + md.toString() + " as " + txt.getText().toString());
                        adapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        builder.create().show();
    }

    private void showAboutDialog() {
        // custom dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("About this app")
                .setMessage("Minimal network host and port scanner.\n\nPlease send feedback to chris@linuxuser.at\n\nv" + VERSION)
                .setPositiveButton("OK", null)
                .create()
                .show();
    }
}
