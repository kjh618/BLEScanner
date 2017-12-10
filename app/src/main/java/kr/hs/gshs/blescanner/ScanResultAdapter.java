package kr.hs.gshs.blescanner;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import kr.hs.gshs.blebeaconprotocollibrary.PacketData;
import kr.hs.gshs.blebeaconprotocollibrary.PacketTypeFilter;
import kr.hs.gshs.blebeaconprotocollibrary.PacketTypes;
import kr.hs.gshs.blebeaconprotocollibrary.Struct;

/**
 * Holds and displays {@link ScanResult}s, used by {@link MainActivity}.
 */
public class ScanResultAdapter extends BaseAdapter {

    private ArrayList<PacketData> mArrayList;
    private ArrayList<Long> timestamps;

    private Context mContext;

    private LayoutInflater mInflater;

    private PacketTypeFilter mPacketTypeFilter;

    ScanResultAdapter(Context context, LayoutInflater inflater, PacketTypeFilter packetTypeFilter) {
        super();
        mArrayList = new ArrayList<>();
        timestamps = new ArrayList<>();
        mContext = context;
        mInflater = inflater;
        mPacketTypeFilter = packetTypeFilter;
    }

    @Override
    public int getCount() {
        return mArrayList.size();
    }

    @Override
    public Object getItem(int position) {
        return mArrayList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mArrayList.get(position).hashCode();
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {

        // Reuse an old view if we can, otherwise create a new one.
        if (view == null) {
            view = mInflater.inflate(R.layout.list_item_scan_result, null);
        }

        PacketData packet = mArrayList.get(position);

        TextView textViewPacketType = (TextView) view.findViewById(R.id.textViewPacketType);

        ListView listViewStructs = (ListView) view.findViewById(R.id.listViewStructs);
        StructAdapter structAdapter = new StructAdapter(view.getContext(), LayoutInflater.from(view.getContext()));
        listViewStructs.setAdapter(structAdapter);

        if (!packet.isSupportedPacket()) {
            textViewPacketType.setText("(Unsupported packet)");
        } else {
            PacketTypes packetType = packet.getPacketType();
            String blocked;
            if (mPacketTypeFilter.isBlocked(packetType)) {
                blocked = " (Blocked)";
            } else {
                blocked = "";
            }
            textViewPacketType.setText(packetType.displayName() + blocked);

            ArrayList<Struct> structs = packet.getStructs();
            for (Struct s : structs) {
                structAdapter.addItem(s);
            }
            structAdapter.notifyDataSetChanged();
        }

        TextView textViewLastSeen = (TextView) view.findViewById(R.id.textViewLastSeen);
        textViewLastSeen.setText(getTimeSinceString(mContext, timestamps.get(position)));

        return view;
    }

    /**
     * Search the adapter for an existing device address and return it, otherwise return -1.
     */
    private int getPosition(PacketData packet) {
        int position = -1;
        for (int i = 0; i < mArrayList.size(); i++) {
            if (mArrayList.get(i).equals(packet)) {
                position = i;
                break;
            }
        }
        return position;
    }

    /**
     * Add a ScanResult item to the adapter if a result from that device isn't already present.
     * Otherwise updates the existing position with the new ScanResult.
     */
    public void add(PacketData packet, long timestamp) {

        int existingPosition = getPosition(packet);

        if (existingPosition >= 0) {
            // Device is already in list, update its record.
            timestamps.set(existingPosition, timestamp);
        } else {
            // Add new Device's ScanResult to list.
            mArrayList.add(packet);
            timestamps.add(timestamp);
            notification(packet);
        }
    }

    private void notification(PacketData packet) {
        if(!packet.isSupportedPacket() || mPacketTypeFilter.isBlocked(packet.getPacketType()))
            return;

        ArrayList<Struct> structs = packet.getStructs();
        String notificationText = "";
        for (Struct s : structs) {
            notificationText += ", " + s.getData();
        }
        if (notificationText.length() >= 2)
            notificationText = notificationText.substring(2);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(mContext)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(packet.getPacketType().displayName())
                        .setContentText(notificationText)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(Notification.DEFAULT_ALL);
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(mContext, MainActivity.class);

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(mContext);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        mNotificationManager.notify(0, mBuilder.build());
    }

    /**
     * Clear out the adapter.
     */
    public void clear() {
        mArrayList.clear();
        timestamps.clear();
    }

    /**
     * Takes in a number of nanoseconds and returns a human-readable string giving a vague
     * description of how long ago that was.
     */
    private static String getTimeSinceString(Context context, long timeNanoseconds) {
        String lastSeenText;

        long timeSince = SystemClock.elapsedRealtimeNanos() - timeNanoseconds;
        long secondsSince = TimeUnit.SECONDS.convert(timeSince, TimeUnit.NANOSECONDS);

        if (secondsSince == 1) {
            lastSeenText = secondsSince + " second ago";
        } else if (secondsSince < 60) {
            lastSeenText = secondsSince + " seconds ago";
        } else {
            long minutesSince = TimeUnit.MINUTES.convert(secondsSince, TimeUnit.SECONDS);
            if (minutesSince < 60) {
                if (minutesSince == 1) {
                    lastSeenText = minutesSince + " minute ago";
                } else {
                    lastSeenText = minutesSince + " minutes ago";
                }
            } else {
                long hoursSince = TimeUnit.HOURS.convert(minutesSince, TimeUnit.MINUTES);
                if (hoursSince == 1) {
                    lastSeenText = hoursSince + " hour ago";
                } else {
                    lastSeenText = hoursSince + " hours ago";
                }
            }
        }

        return lastSeenText;
    }
}
