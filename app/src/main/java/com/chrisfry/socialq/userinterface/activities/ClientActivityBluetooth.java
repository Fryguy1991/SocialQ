package com.chrisfry.socialq.userinterface.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
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
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        switch (requestCode) {
            case AppConstants.REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    // Bluetooth is enabled.  Launch connection with host
                    connectToBluetoothHost();
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

    @Override
    protected void connectToHost() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mHostBTDevice = getIntent().getParcelableExtra(AppConstants.BT_DEVICE_EXTRA_KEY);

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, AppConstants.REQUEST_ENABLE_BT);
        } else {
            connectToBluetoothHost();
        }
    }

    private void connectToBluetoothHost() {
        if (mHostBTDevice != null) {
            mHostBTDevice.setPairingConfirmation(false);
        }
        new BluetoothConnectThread(BluetoothAdapter.getDefaultAdapter(), mHostBTDevice, this).start();
    }

    @Override
    protected void sendTrackToHost(String requestMessage) {
        try {
            OutputStream outputStream = mQueueSocket.getOutputStream();
            outputStream.write(requestMessage.getBytes(StandardCharsets.UTF_8));
            Log.d(TAG, "Track sent to output stream");
        } catch (IOException e) {
            Log.d(TAG, "Sending track failed");
            e.printStackTrace();
        }
    }
    // END BLUETOOTH METHODS
}
