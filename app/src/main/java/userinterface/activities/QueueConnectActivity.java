package userinterface.activities;

import android.Manifest;
import android.app.Activity;
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
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import business.AppConstants;
import chrisfry.socialq.R;
import userinterface.adapters.BluetoothDeviceAdapter;
import userinterface.widgets.QueueItemDecoration;

/**
 * Activity used to search for and connect to a SocialQ
 */
public class QueueConnectActivity extends Activity implements View.OnClickListener, BluetoothDeviceAdapter.DeviceSelectionListener {
    private final String TAG = QueueConnectActivity.class.getName();
    // Request code for turning on bluetooth
    private static final int REQUEST_ENABLE_BT = 12345;
    private static final int REQUEST_COURSE_LOCATION = 67890;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mQueueToJoin;
    private List<BluetoothDevice> mDiscoveredDevices = new ArrayList<>();
    private List<BluetoothDevice> mSocialQDevices = new ArrayList<>();

    private RecyclerView mDeviceRecyclerView;
    private BluetoothDeviceAdapter mDeviceListAdapter;

    private View mQueueSearchButton;
    private View mQueueJoinButton;

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

        mQueueJoinButton.setEnabled(false);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.queue_connect_screen);

        mQueueSearchButton = findViewById(R.id.btn_queue_search);
        mQueueSearchButton.setOnClickListener(this);

        mQueueJoinButton = findViewById(R.id.btn_queue_join);
        mQueueJoinButton.setOnClickListener(this);

        mDeviceRecyclerView = (RecyclerView) findViewById(R.id.rv_queue_list_view);
        mDeviceListAdapter = new BluetoothDeviceAdapter();
        mDeviceListAdapter.setDeviceSelectionListener(this);
        mDeviceRecyclerView.setAdapter(mDeviceListAdapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mDeviceRecyclerView.setLayoutManager(layoutManager);
        mDeviceRecyclerView.addItemDecoration(new QueueItemDecoration(getApplicationContext()));

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // TODO: Throw some sort of error/exception?  Need bluetooth for main application usage
        } else if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        IntentFilter foundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter startFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        IntentFilter finishFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        IntentFilter uuidFilter = new IntentFilter(BluetoothDevice.ACTION_UUID);
        registerReceiver(mReceiver, foundFilter);
        registerReceiver(mReceiver, startFilter);
        registerReceiver(mReceiver, finishFilter);
        registerReceiver(mReceiver, uuidFilter);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_COURSE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Starting BT discovery? : " + mBluetoothAdapter.startDiscovery());
                    mQueueSearchButton.setEnabled(false);
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
                            BluetoothDevice device = mDiscoveredDevices.remove(0);
                            device.fetchUuidsWithSdp();
                        }
                        break;
                    case BluetoothDevice.ACTION_UUID:
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        Parcelable[] uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);

                        // Check UUIDs to see if it's a SocialQ device
                        if (!mSocialQDevices.contains(device) && containsAppUuid(uuids)) {
                            Log.d(TAG, "Found SocialQ device");
                            mSocialQDevices.add(device);
                        } else {
                            String displayName = device.getName() == null ? device.getAddress() : device.getName();
                            Log.d(TAG, "UUIDs don't match for: " + displayName);
                        }

                        if (mDiscoveredDevices.isEmpty()) {
                            displayBTDevices();
                            mQueueSearchButton.setEnabled(true);
                        } else {
                            BluetoothDevice nextDiscovered = mDiscoveredDevices.remove(0);
                            nextDiscovered.fetchUuidsWithSdp();
                        }
                        break;
                    default:
                        // Do nothing or future implementation

                }
            }
        }
    };

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_queue_search:
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
                    mQueueSearchButton.setEnabled(false);
                }
                break;
            case R.id.btn_queue_join:
                if (mQueueToJoin != null) {
                    Intent clientIntent = new Intent(this, ClientActivity.class);
                    clientIntent.putExtra(AppConstants.BT_DEVICE_ADDRESS_EXTRA_KEY,
                            mQueueToJoin.getAddress());
                    clientIntent.putExtra(AppConstants.BT_DEVICE_EXTRA_KEY, mQueueToJoin);
                    startActivity(clientIntent);
                }
                break;
        }
    }

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
}
