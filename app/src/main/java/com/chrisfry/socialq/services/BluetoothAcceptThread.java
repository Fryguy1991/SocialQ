package com.chrisfry.socialq.services;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;

import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.business.listeners.BluetoothConnectionListener;

import static android.content.ContentValues.TAG;

/**
 * Thread used for accepting bluetooth connections
 */
public class BluetoothAcceptThread extends Thread {
    private BluetoothAdapter mBluetoothAdapter;
    private final BluetoothServerSocket mServerSocket;
    private BluetoothConnectionListener mConnectionListener;

    public BluetoothAcceptThread(BluetoothAdapter adapter, BluetoothConnectionListener listener) {
        mConnectionListener = listener;
        mBluetoothAdapter = adapter;
        // Use a temporary object that is later assigned to mmServerSocket
        // because mmServerSocket is final.
        BluetoothServerSocket tmp = null;
        try {
            // MY_UUID is the app's UUID string, also used by the client code.
            tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("SocialQ", AppConstants.APPLICATION_UUID);
        } catch (IOException e) {
            Log.e(TAG, "Socket's listen() method failed", e);
        }
        mServerSocket = tmp;
    }

    public void run() {
        BluetoothSocket socket = null;
        // Keep listening until exception occurs or a socket is returned.
        while (true) {
            try {
                Log.d(TAG, "Start listening for Bluetooth connections.");
                socket = mServerSocket.accept();

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    Log.d(TAG, "Bluetooth connection was accepted.");
                    mConnectionListener.onConnectionEstablished(socket);
                    mServerSocket.close();
                    break;
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket's accept() method failed", e);
                break;
            }
        }
    }

    // Closes the connect socket and causes the thread to finish.
    public void cancel() {
        try {
            mServerSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the connect socket", e);
        }
    }
}
