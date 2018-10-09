package com.chrisfry.socialq.userinterface.activities;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.R;
import com.chrisfry.socialq.userinterface.adapters.BluetoothDeviceAdapter;

/**
 * Activity used to search for and connect to a SocialQ
 */
public class QueueConnectActivityBluetooth extends QueueConnectActivity implements
        BluetoothDeviceAdapter.DeviceSelectionListener {
    private final String TAG = QueueConnectActivityBluetooth.class.getName();
    // Request code for turning on bluetooth
    private static final int REQUEST_ENABLE_BT = 12345;
    private static final int REQUEST_COURSE_LOCATION = 67890;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mQueueToJoin;
    private List<BluetoothDevice> mDiscoveredDevices = new ArrayList<>();
    private List<BluetoothDevice> mSocialQDevices = new ArrayList<>();

    private BluetoothDeviceAdapter mDeviceListAdapter;


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode != RESULT_OK) {
                    // TODO: Bluetooth not enabled, need to handle here
                } else {
                    // Attempt to connect to the host
                }
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mQueueToJoin = null;
        mDiscoveredDevices.clear();
        mSocialQDevices.clear();
        mDeviceListAdapter.updateDeviceList(new ArrayList<BluetoothDevice>());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register to receive results of Bluetooth method calls
        IntentFilter foundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter startFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        IntentFilter finishFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        IntentFilter uuidFilter = new IntentFilter(BluetoothDevice.ACTION_UUID);
        registerReceiver(mReceiver, foundFilter);
        registerReceiver(mReceiver, startFilter);
        registerReceiver(mReceiver, finishFilter);
        registerReceiver(mReceiver, uuidFilter);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // TODO: Throw some sort of error/exception?  Need bluetooth for main application usage
        } else if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            searchForQueues();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_COURSE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Starting BT discovery? : " + mBluetoothAdapter.startDiscovery());
                }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mReceiver);
    }

    private void displayBTDevices() {
        mDeviceListAdapter.updateDeviceList(mSocialQDevices);
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "BroadcastReceiver received: " + intent.getAction());
            if (action != null) {
                switch (action) {
                    case BluetoothDevice.ACTION_FOUND:
                        // Discovery has found a device. Get the BluetoothDevice
                        // object and store it.
                        BluetoothDevice discoveredDevice =
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (containsAppUuid(discoveredDevice.getUuids())) {
                            Log.d(TAG, "Found SocialQ device");
                            mSocialQDevices.add(discoveredDevice);
                        } else {
                            mDiscoveredDevices.add(discoveredDevice);
                        }
                        break;
                    case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                        // Bluetooth discovery started.  Clear stored list
                        mDiscoveredDevices.clear();
                        break;
                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                        // Bluetooth discovery finished.
                        Log.d(TAG, "Identify which devices are SocialQ Hosts");
                        mBluetoothAdapter.cancelDiscovery();
                        if (!mDiscoveredDevices.isEmpty()) {
                            boolean result = mDiscoveredDevices.remove(0).fetchUuidsWithSdp();
                            if (!result) {
                                Log.d(TAG, "Cannot fetch uuids");
                            }
                        }
                        break;
                    case BluetoothDevice.ACTION_UUID:
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        Parcelable[] uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);

                        // Check UUIDs to see if it's a SocialQ device
                        // TODO: Should be able to identify by UUID and not by device name
                        if (!mSocialQDevices.contains(device) && (containsAppUuid(uuids))) {
                            Log.d(TAG, "Found SocialQ device");
                            mSocialQDevices.add(device);
                        } else {
                            String displayName = device.getName() == null ? device.getAddress() : device.getName();
                            Log.d(TAG, "UUIDs don't match for: " + displayName);
                        }

                        if (mDiscoveredDevices.isEmpty()) {
                            displayBTDevices();
                        } else {
                            boolean result = mDiscoveredDevices.remove(0).fetchUuidsWithSdp();
                            if (!result) {
                                Log.d(TAG, "Cannot fetch uuids");
                            }
                        }
                        break;
                    default:
                        // Do nothing or future implementation

                }
            }
        }
    };

    private boolean containsAppUuid(Parcelable[] uuids) {
        if (uuids != null) {
            for (Parcelable currentUuid : uuids) {
                ParcelUuid parcelUuid = (ParcelUuid) currentUuid;
                if (parcelUuid.getUuid().equals(AppConstants.APPLICATION_UUID)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onDeviceSelected(BluetoothDevice device) {
        mQueueToJoin = device;
        mQueueJoinButton.setEnabled(device != null);
    }

    @Override
    protected void setupAdapter(RecyclerView recyclerView) {
        mDeviceListAdapter = new BluetoothDeviceAdapter();
        mDeviceListAdapter.setDeviceSelectionListener(this);
        recyclerView.setAdapter(mDeviceListAdapter);
    }

    @Override
    protected void searchForQueues() {
        mSocialQDevices.clear();
        mDiscoveredDevices.clear();
        displayBTDevices();
        // Request course location permission if it is not already granted
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_COURSE_LOCATION);
        } else {
            // Start Bluetooth discovery
            Log.d(TAG, "Starting BT discovery? : " + mBluetoothAdapter.startDiscovery());
        }
    }

    @Override
    protected void connectToQueue() {
        if (mQueueToJoin != null) {
            Intent clientIntent = new Intent(this, ClientActivityBluetooth.class);
            clientIntent.putExtra(AppConstants.BT_DEVICE_ADDRESS_EXTRA_KEY,
                    mQueueToJoin.getAddress());
            clientIntent.putExtra(AppConstants.BT_DEVICE_EXTRA_KEY, mQueueToJoin);
            startActivity(clientIntent);
        }
    }
}
