package com.bitsworking.tetheredipfinder.app;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private TextView tv_ip = null;
    private Menu optionsMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv_ip = (TextView) findViewById(R.id.txt_ip);
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
        NetTask nt = new NetTask();
        nt.execute();
    }

    public class MemberDescriptor {
        public String ip = "";
        public String hw_type = "";
        public String flags = "";
        public String hw_address = "";
        public String mask = "";
        public String device = "";

        public String hostname = "";
        public String canonicalHostname = "";
        public boolean isReachable = false;

        public String toString() {
            String ret = ip + ", " + hw_type + ", " + flags + ", " + hw_address + ", " + mask + ", " + device + ", hostname=" + hostname + ", canonicalHostname=" + canonicalHostname + ", reachable=" + isReachable;
            return ret;
        }
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
            String s = "";
            for (MemberDescriptor md : members) {
                s += md.toString();
                s += "\n\n";
            }
            tv_ip.setText(s);
            setRefreshActionButtonState(false);
        }
    }
}
