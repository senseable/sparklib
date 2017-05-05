package eu.senseable.sparklib;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import eu.senseable.service.ISparkCallback;
import eu.senseable.service.ISparkService;

/** Main interface for interacting with the Spark Cloud and Bluetooth components.
 *
 * Created by phil on 4/27/17.
 */

public class Spark {
    private final String pkg = "eu.senseable.companion";
    private final SparkServiceConnection mConn;
    private final Callbacks callbacks;
    private final Handler mHandler;
    private final Intent mServiceIntent;
    private boolean mServiceBound = false;

    /** Bitfield values for transporting the status of the Spark Service to clients.
     * When adding to this field make sure to add powers of two.
     */
    public interface Status {
        int RUNNING = 1<<0,
            BLUETOOTH_DISABLED = 1<<1;
    }


    /**
     * register a context and callback receiver to get updates whenever a Spark is in the area
     * or has updated events. The main interface is the data handed over via the callbacks in
     * ab.
     *
     * @param c  Context to operate on
     * @param cb Callbacks to be called on new data
     */
    public Spark(Context c, Callbacks cb) {
        String cls = "eu.senseable.service.SparkService";

        mServiceIntent = new Intent();
        mServiceIntent.setComponent(new ComponentName(pkg,cls));
        mHandler = new Handler(c.getMainLooper());
        mConn = new SparkServiceConnection();
        callbacks = cb;

        try {
            PackageManager pm = c.getPackageManager();
            pm.getPackageInfo(pkg, 0);

            Intent i = new Intent();
            i.setData(Uri.parse(pkg));
            onSparkServiceInstalled.onReceive(c, i);

        } catch (PackageManager.NameNotFoundException e) {
            /* package is not installed */
            Uri uri = Uri.parse("market://details?id="+pkg);
            c.startActivity(new Intent(Intent.ACTION_VIEW,uri));

            /* register a receiver when a package is installed */
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
            intentFilter.addAction(Intent.ACTION_PACKAGE_INSTALL);
            intentFilter.addDataScheme("package");
            c.registerReceiver(onSparkServiceInstalled, intentFilter);
        }
    }

    private BroadcastReceiver onSparkServiceInstalled = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            Uri uri = intent.getData(), shouldbe = Uri.parse(pkg);
            if (!shouldbe.equals(uri)) {
                Log.d("spark", String.format("'%s' != '%s'", uri, shouldbe));
                return;
            }

            if (!c.bindService(mServiceIntent, mConn, Service.BIND_AUTO_CREATE))
                throw new Error("Unable to connect to SparkService");

            mServiceBound = true;
        }
    };

    public void close(Context c) { if (mServiceBound) c.unbindService(mConn);}

    /** returns the address of the currently bound Spark lighter, null if no lighter is bound.
     *
     * @return bluetooth address as string, null if not bound
     * @throws RemoteException
     */
    public String getAddr() throws RemoteException {
        return mConn.api.getAddr();
    }

    /** allows to modify the currently bound Spark lighter, identified with a bluetooth address.
     *
     * @param addr address to bind to, can be null to unbind
     * @return the original object to chain calls.
     * @throws RemoteException
     */
    public Spark setAddr(@Nullable String addr) throws RemoteException {
        mConn.api.setAddr(addr);
        return this;
    }

    /** returns the list of current events stored on this device. The string in those events
     * encodes a JSONObject with a beg, end, and sid field. The beg and end are UTC timestamp
     * of the beginning and end of a cigarette smoking event. For a list of java objects @see
     * getEvents().
     *
     * @return list of events
     * @throws RemoteException
     */
    public List<String> getJSONEvents() throws RemoteException {
        return mConn.api.getEvents();
    }

    /** modifies the list of stored events on the device, use carefully! The String in the supplied
     * list must be a JSONObject with a beg and end field, which are UTC encoded timestamps.
     *
     * @param events JSONObjects with UTC-encoded timestamp field in beg and end
     * @throws RemoteException
     */
    public void setJSONEvents(List<String> events) throws RemoteException {
        mConn.api.setEvents(events);
    }

    /** return a list of stored events on the device.
     *
     * @return list of Spark Events  designating the smoking instances
     * @throws RemoteException
     */
    public List<Event> getEvents() throws RemoteException {
        List<String> jsons = getJSONEvents();
        ArrayList<Event> s = new ArrayList<>(jsons.size());

        for (String json : jsons)
            s.add(new Event(json));

        return s;
    }

    /** set the currently list of events stored on the device.
     *
     * @param events list of Spark Events
     * @throws RemoteException
     */
    public void setEvents(List<Event> events) throws RemoteException {
        List<String> jsons = new ArrayList<>(events.size());

        for (Event e : events)
            jsons.add(e.toJSON());

        setJSONEvents(jsons);
    }

    private class SparkServiceConnection implements ServiceConnection {
        private ISparkService api;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            api = ISparkService.Stub.asInterface(service);
            try {
                api.register(new Adapter());
            } catch (RemoteException e) {
                e.printStackTrace();
                callbacks.onDestroy();
            }
            callbacks.onReady();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            callbacks.onDestroy();
            api = null;
        }
    }

    private class Adapter extends ISparkCallback.Stub {
        public void onEventsChanged(final List<String> events) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callbacks.onJSONEventsChanged(events);
                }
            });

            final LinkedList<Event> sparks = new LinkedList<>();
            for (String json : events)
                sparks.add(new Event(json));

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callbacks.onEventsChanged(sparks);
                }
            });
        }

        public void onNewSpark(final String addr) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callbacks.onNewSpark(addr);
                }
            });
        }

        public void onBrownout(final boolean status) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callbacks.onBrownout(status);
                }
            });
        }

        public void onStatusChanged(final int status) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callbacks.onStatusChanged(status);
                }
            });
        }
    }

    /**
     *  The main interace to the Spark Service for Android. Once a connection is established
     *  these callbacks can be called in no particular order.
     */
    public interface Callbacks {
        /**
         * called when a connection to the background service is established. Only after this
         * event has been thrown calls can be made.
         */
        public void onReady();

        /**
         * when the service is killed or exiting. This is intended to let the program know that
         * the retrieved api object is no longer valid.
         */
        public void onDestroy();

        /**
         * The list of events reported by the Spark was changed (either from the lighter, or
         * for some external reason). The current "version" of the events list is attached to
         * this function. Events are stored as ISO-formatted Date Strings.
         *
         * @param events list of JSONObject packed as strings, these objects contain a beg, end,
         *               and an id for each event. Times are encoded as ISO-standard times.
         */
        public void onJSONEventsChanged(List<String> events);

        /**
         * The same as onEventsChanged, but events are returned as Spark Event objects.
         *
         * @param events list of Spark Events
         */
        public void onEventsChanged(List<Event> events);

        /**
         *  called when a Spark is seen, while no address has been set yet. This only works
         *  as long as the UI is in the foreground and is the equivalent of a Bluetooth Scan
         *  to detect new devices. Use this to get and set the address of the Spark you wish
         *  to connect to.
         *
         *  @param addr bluetooth address of the device.
         */
        public void onNewSpark(String addr);

        /**
         * called when a Brownout was detected, i.e. the battery is flat and needs to be replaced.
         * This is currently the only to get some indication on the battery status of the device.
         *
         * @param batEmpty true if the battery is empty
         */
        public void onBrownout(boolean batEmpty);

        /**
         * called when the status of the service changes, for example when Bluetooth is deactivated.
         * The Status is an or'red combination of @see Status.
         *
         * @param status or'red combination of @see Status flags
         */
        public void onStatusChanged(int status);

        public class Stub implements Callbacks {
            public void onReady() {}
            public void onDestroy() {}
            public void onJSONEventsChanged(List<String> events) {}
            public void onEventsChanged(List<Event> events) {}
            public void onNewSpark(String addr) {}
            public void onBrownout(boolean batEmpty) {}
            public void onStatusChanged(int status) {}
        }

    }

    public class Event {
        private final DateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); // Quoted "Z" to indicate UTC, no timezone offset

        public Date beg, end;
        public long sid;

        public Event(String json) {
            try {
                JSONObject obj = new JSONObject(json);
                beg = iso.parse(obj.getString("beg"));
                end = iso.parse(obj.getString("end"));
                sid = obj.optLong("sid", 0);
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        public String toJSON() {
            try {
                JSONObject json = new JSONObject();
                json.put("beg", iso.format(beg));
                json.put("end", iso.format(end));
                json.put("sid", sid);
                return json.toString();
            } catch (Exception e) {
                e.printStackTrace();
                return "";
            }
        }
    }
}
