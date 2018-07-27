package services;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;

import business.AppConstants;
import business.listeners.BluetoothConnectionListener;

/**
 * Thread used for initiating bluetooth connections
 */
public class BluetoothConnectThread extends Thread {
    private final String TAG = BluetoothConnectThread.class.getName();

    private BluetoothSocket mSocket;
    private BluetoothDevice mDevice;
    private BluetoothConnectionListener mListener;
    private BluetoothAdapter mAdapter;

    public BluetoothConnectThread(BluetoothAdapter adapter, BluetoothDevice device, BluetoothConnectionListener listener) {
        // Use a temporary object that is later assigned to mmSocket
        // because mmSocket is final.
        BluetoothSocket tmp = null;
        mDevice = device;
        mAdapter = adapter;
        mListener = listener;

        try {
            // Get a BluetoothSocket to connect with the given BluetoothDevice.
            // MY_UUID is the app's UUID string, also used in the server code.
            tmp = device.createRfcommSocketToServiceRecord(AppConstants.APPLICATION_UUID);
        } catch (IOException e) {
            Log.e(TAG, "Socket's create() method failed", e);
        }
        mSocket = tmp;
    }

    public void run() {
        // Cancel discovery because it otherwise slows down the connection.
        mAdapter.cancelDiscovery();

        try {
            // Connect to the remote device through the socket. This call blocks
            // until it succeeds or throws an exception.
            mSocket.connect();
        } catch (IOException connectException) {
            // Unable to connect; close the socket and return.
            Log.d(TAG, "Unable to connect");
            try {
                mSocket.close();
            } catch (IOException closeException) {
                Log.e(TAG, "Could not close the client socket", closeException);
            }
            return;
        }

        // The connection attempt succeeded. Perform work associated with
        // the connection in a separate thread.
        mListener.onConnectionEstablished(mSocket);
    }

    // Closes the client socket and causes the thread to finish.
    public void cancel() {
        try {
            mSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the client socket", e);
        }
    }
}
