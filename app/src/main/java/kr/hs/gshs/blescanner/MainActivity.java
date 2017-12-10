package kr.hs.gshs.blescanner;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import kr.hs.gshs.blebeaconprotocollibrary.PacketData;
import kr.hs.gshs.blebeaconprotocollibrary.PacketTypeFilter;
import kr.hs.gshs.blebeaconprotocollibrary.PacketTypes;
import kr.hs.gshs.blebeaconprotocollibrary.ScanResultParser;

/**
 * Scans for Bluetooth Low Energy Advertisements matching a filter and displays them to the user.
 */
public class MainActivity extends AppCompatActivity {

    public static final int REQUEST_ENABLE_BT = 1;

    private static final String TAG = MainActivity.class.getSimpleName();

    private int currentView;

    // currentView == 0
    private ConstraintLayout viewScanResults;
    private Switch switchScan;
    private ListView listViewScanResults;

    // currentView == 1
    private ConstraintLayout viewFilterSettings;
    private ListView listViewFilterSettings;

    public PacketTypeFilter mPacketTypeFilter;

    /**
     * Stops scanning after 5 seconds.
     */
    private static final long SCAN_PERIOD = 10000;

    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothLeScanner mBluetoothLeScanner;

    private ScanCallback mScanCallback;

    private ScanResultAdapter scanResultAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Scan Results");
        currentView = 0;

        if (savedInstanceState == null) {
            checkBluetooth();
        }

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        mPacketTypeFilter = new PacketTypeFilter();

        setupViewScanResults();

        setupViewFilterSettings();

        // Trigger refresh on app's 1st load
        startScanning();
        switchScan.setChecked(true);
    }

    private void checkBluetooth() {
        mBluetoothAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

        // Is Bluetooth supported on this device?
        if (mBluetoothAdapter != null) {

            // Is Bluetooth turned on?
            if (!mBluetoothAdapter.isEnabled()) {

                // Prompt user to turn on Bluetooth (logic continues in onActivityResult()).
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        } else {

            // Bluetooth is not supported.
            Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void setupViewScanResults() {
        viewScanResults = (ConstraintLayout) findViewById(R.id.viewScanResults);

        switchScan = (Switch) findViewById(R.id.switchScan);
        switchScan.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startScanning();
                } else {
                    stopScanning();
                }
            }
        });

        listViewScanResults = (ListView) findViewById(R.id.listViewScanResults);
        scanResultAdapter = new ScanResultAdapter(getApplicationContext(), getLayoutInflater(), mPacketTypeFilter);
        listViewScanResults.setAdapter(scanResultAdapter);
    }

    private void setupViewFilterSettings() {
        viewFilterSettings = (ConstraintLayout) findViewById(R.id.viewFilterSettings);

        PacketTypes[] packetTypes = PacketTypes.getValues();
        String[] packetTypeNames = new String[packetTypes.length];
        for (int i=0; i<packetTypes.length; ++i)
            packetTypeNames[i] = packetTypes[i].displayName();

        listViewFilterSettings = (ListView) findViewById(R.id.listViewFilterSettings);
        ArrayAdapter<String> adapterFilterSettings = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, packetTypeNames);
        listViewFilterSettings.setAdapter(adapterFilterSettings);
    }

    // continued from checkBluetooth()
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:

                if (resultCode != RESULT_OK) {

                    // User declined to enable Bluetooth, exit the app.
                    Toast.makeText(this, "User declined to enable Bluetooth, exiting Bluetooth Advertisements.", Toast.LENGTH_SHORT).show();
                    finish();
                }

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuItem:
                if (currentView == 0) {
                    setTitle("Filter Settings");
                    item.setTitle("Save");

                    viewScanResults.setVisibility(View.INVISIBLE);
                    viewFilterSettings.setVisibility(View.VISIBLE);

                    currentView = 1;
                } else if (currentView == 1) {
                    setTitle("Scan Results");
                    item.setTitle("Filter");

                    viewFilterSettings.setVisibility(View.INVISIBLE);
                    viewScanResults.setVisibility(View.VISIBLE);

                    SparseBooleanArray filter = listViewFilterSettings.getCheckedItemPositions();
                    for(int i=0; i<filter.size(); ++i) {
                        if (filter.get(i)) {
                            mPacketTypeFilter.block(PacketTypes.fromOrdinal(i));
                        } else {
                            mPacketTypeFilter.unblock(PacketTypes.fromOrdinal(i));
                        }
                    }

                    currentView = 0;
                }

                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Start scanning for BLE Advertisements (& set it up to stop after a set period of time).
     */
    public void startScanning() {
        if (mScanCallback == null) {
            Log.d(TAG, "Starting Scanning");

            // Kick off a new scan.
            mScanCallback = new SampleScanCallback();
            mBluetoothLeScanner.startScan(buildScanFilters(), buildScanSettings(), mScanCallback);

            scanResultAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "Scanning already started.", Toast.LENGTH_SHORT);
        }
    }

    /**
     * Stop scanning for BLE Advertisements.
     */
    public void stopScanning() {
        if (mScanCallback != null) {
            Log.d(TAG, "Stopping Scanning");

            // Stop the scan, wipe the callback.
            mBluetoothLeScanner.stopScan(mScanCallback);
            mScanCallback = null;

            // Even if no new results, update 'last seen' times.
            scanResultAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "Scanning already stopped.", Toast.LENGTH_LONG);
        }
    }

    /**
     * Return a List of {@link ScanFilter} objects to filter by Service UUID.
     */
    private List<ScanFilter> buildScanFilters() {
        List<ScanFilter> scanFilters = new ArrayList<>();

        ScanFilter.Builder builder = new ScanFilter.Builder();
        scanFilters.add(builder.build());

        return scanFilters;
    }

    /**
     * Return a {@link ScanSettings} object set to use low power (to preserve battery life).
     */
    private ScanSettings buildScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        return builder.build();
    }

    /**
     * Custom ScanCallback object - adds to adapter on success, displays error on failure.
     */
    private class SampleScanCallback extends ScanCallback {

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);

            for (ScanResult result : results) {
                PacketData packet = ScanResultParser.parse(result);
                scanResultAdapter.add(packet, result.getTimestampNanos());
            }
            scanResultAdapter.notifyDataSetChanged();
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            PacketData packet = ScanResultParser.parse(result);
            scanResultAdapter.add(packet, result.getTimestampNanos());
            scanResultAdapter.notifyDataSetChanged();
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Toast.makeText(getApplicationContext(), "Scan failed with error: " + errorCode, Toast.LENGTH_LONG).show();
        }
    }
}
