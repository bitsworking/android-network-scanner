package com.bitsworking.tetheredipfinder.app;

import android.app.ActionBar;
import android.app.Activity;
import android.os.AsyncTask;
import android.support.v4.app.Fragment;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileReader;
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
                setRefreshActionButtonState(false);
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

    public ArrayList<String> getTetheredIPAddresses() {
        ArrayList<String> ret = new ArrayList<String>();
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"));
            String line = "";
            while((line = br.readLine()) != null) {
                String[] parts = line.split(" ");
                ret.add(parts[0]);
                Log.v(TAG, line);
            }
            br.close();
        }
        catch(Exception e) { Log.e(TAG, e.toString()); }

        if (ret.size() > 0)
            ret.remove(0);

        return ret;
    }

    public class MemberDescriptor {
        public String ip = "";
        public String hostname = "";
        public String canonicalHostname = "";
    }

    class NetTask extends AsyncTask<String, Void, ArrayList<MemberDescriptor>> {
        protected ArrayList<MemberDescriptor> doInBackground(String... params) {
            ArrayList<MemberDescriptor> ret = new ArrayList<MemberDescriptor>();
            for (String l : getTetheredIPAddresses()) {
                MemberDescriptor md = new MemberDescriptor();
                md.ip = l;
                try {
                    InetAddress inetAddr = InetAddress.getByName(l);
                    md.hostname = inetAddr.getHostName();
                    md.canonicalHostname = inetAddr.getCanonicalHostName();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                ret.add(md);
            }
            return ret;
        }

        protected void onPostExecute(ArrayList<MemberDescriptor> members) {
            String s = "";
            for (MemberDescriptor md : members) {
                s += md.ip;
                if (!md.hostname.equals(md.ip))
                    s += " / " + md.hostname;
                if (!md.canonicalHostname.equals(md.ip))
                    s += " / " + md.canonicalHostname;
                s += "\n";
            }
            tv_ip.setText(s);
            setRefreshActionButtonState(false);
        }
    }
}
