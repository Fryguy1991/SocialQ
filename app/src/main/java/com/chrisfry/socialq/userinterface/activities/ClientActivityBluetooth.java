package com.chrisfry.socialq.userinterface.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.business.listeners.BluetoothConnectionListener;
import com.chrisfry.socialq.services.BluetoothConnectThread;

/**
 * Activity class for client of a queue
 */
public class ClientActivityBluetooth extends ClientActivity implements BluetoothConnectionListener {
    private final String TAG = ClientActivityBluetooth.class.getName();

    // Bluetooth elements
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mHostBTDevice;
    private BluetoothSocket mQueueSocket;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);
        BluetoothDevice newDevice = intent.getParcelableExtra(AppConstants.BT_DEVICE_EXTRA_KEY);
        if (!mHostBTDevice.equals(newDevice)) {
            mHostBTDevice = null;
            // TODO: Close BT socket since we have a new host
            if (newDevice != null) {
                // New host device.  Launch connection.
                mHostBTDevice = newDevice;
                // TODO: Open BT connection with new BT device
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mHostBTDevice = getIntent().getParcelableExtra(AppConstants.BT_DEVICE_EXTRA_KEY);

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, AppConstants.REQUEST_ENABLE_BT);
        } else {
            connectToHost();
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        switch (requestCode) {
            case AppConstants.REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    // Bluetooth is enabled.  Launch connection with host
                    connectToHost();
                } else {
                    // TODO: Bluetooth not enabled, need to handle here
                }
                break;
            default:
                // Do nothing
        }
    }

    // BEGIN BLUETOOTH METHODS
    @Override
    public void onConnectionEstablished(BluetoothSocket socket) {
        Log.d(TAG, "WE ARE CONNECTED!");
        mQueueSocket = socket;
//        try {
//            socket.close();
//            Log.e(TAG, "Successfully closed socket");
//        } catch (IOException e) {
//            Log.e(TAG, "Could not close the client socket", e);
//        }
    }

//    @Override
//    public void onConnectionFailed() {
//        Log.d(TAG, "Connection failed");
//    }

    private void connectToHost() {
        if (mHostBTDevice != null) {
            mHostBTDevice.setPairingConfirmation(false);
        }
        new BluetoothConnectThread(BluetoothAdapter.getDefaultAdapter(), mHostBTDevice, this).start();
    }

    @Override
    protected void sendTrackToHost(String trackUri) {
        try {
            OutputStream outputStream = mQueueSocket.getOutputStream();
//            outputStream.write(BluetoothMessage.START_MESSAGE.getMessageId());
//            outputStream.write(BluetoothMessage.SEND_TRACK.getMessageId());
            outputStream.write(trackUri.getBytes(StandardCharsets.UTF_8));
//            outputStream.write(BluetoothMessage.END_MESSAGE.getMessageId());
            Log.d(TAG, "Track sent to output stream");
        } catch (IOException e) {
            Log.d(TAG, "Sending track failed");
            e.printStackTrace();
        }
    }
    // END BLUETOOTH METHODS
}
