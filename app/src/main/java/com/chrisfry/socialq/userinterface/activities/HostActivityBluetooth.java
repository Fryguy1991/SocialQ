package com.chrisfry.socialq.userinterface.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.chrisfry.socialq.enums.RequestType;
import com.spotify.sdk.android.player.Error;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.chrisfry.socialq.business.listeners.BluetoothConnectionListener;
import com.chrisfry.socialq.services.BluetoothAcceptThread;

public class HostActivityBluetooth extends HostActivity implements BluetoothConnectionListener{
    private final String TAG = HostActivityBluetooth.class.getName();

    // Handler message values
    private static final int QUEUE_TRACK = 0;
    private static final String BUNDLE_TRACK_KEY = "bundle_track_key";

    // Bluetooth elements
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothServerSocket mBTServerSocket;
    private List<BluetoothSocket> mBluetoothClients = new ArrayList<>();


    private Handler mHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message inputMessage) {
            switch (inputMessage.what) {
                case QUEUE_TRACK:
                    String trackUri = inputMessage.getData().getString(BUNDLE_TRACK_KEY);
                    break;
                default:
                    // Do nothing
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.host_screen);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // TODO: Throw some sort of error/exception?  Need bluetooth for main application usage
        }
        mBluetoothAdapter.setName("SocialQ Host");
//        startHostConnection();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        RequestType requestType = RequestType.Companion.getRequestTypeFromRequestCode(requestCode);
        Log.d(TAG, "Received request type: " + requestType);

        // Handle request result
        switch (requestType) {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "Bluetooth Was Enabled");
                    // Request bluetooth discoverability
                    Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                    discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
                    startActivityForResult(discoverableIntent, RequestType.REQUEST_DISCOVER_BT.getRequestCode());
                } else {
                    // TODO: Bluetooth not enabled, need to handle here
                }
                break;
            case REQUEST_DISCOVER_BT:
                if (resultCode == RESULT_CANCELED) {
                    // TODO: User said no to discoverability, handle here
                } else {
                    Log.d(TAG, "Device should be discoverable.");
                    launchBluetoothServer();
                }
                break;
        }
    }

    @Override
    public void onLoggedIn() {
        Log.d(TAG, "User logged in");
    }

    @Override
    public void onLoggedOut() {
        Log.d(TAG, "User logged out");
    }

    @Override
    public void onLoginFailed(Error error) {
        Log.d(TAG, "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d(TAG, "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d(TAG, "Received connection message: " + message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            mBTServerSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // BEGIN BLUETOOTH METHODS
    private void launchBluetoothServer() {
        new BluetoothAcceptThread(mBluetoothAdapter, this).start();
    }

    @Override
    public void onConnectionEstablished(BluetoothSocket socket) {
        Log.d(TAG, "A Client has connected.  Send Queue");
        mBluetoothClients.add(socket);
        initiateNewClient(socket);
        try {
            new ReadThread(socket).start();
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Could not start ReadThread");
        }


//        try {
//            Log.d(TAG, "Attempting to send queue.");
//            OutputStream dataOut = socket.getOutputStream();
//            dataOut.write("start_queue".getBytes());
//            for (Track track : mCurrentTrackList) {
//                dataOut.write(track.uri.getBytes());
//            }
//            dataOut.write("end_queue".getBytes());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }
    // END BLUETOOTH METHODS


    @Override
    protected void startHostConnection(String queueTitle) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // TODO: Throw some sort of error/exception?  Need bluetooth for main application usage
        } else {
            mBluetoothAdapter.setName(queueTitle);
            if (!mBluetoothAdapter.isEnabled()) {
                Log.d(TAG, "Attempting to enable Bluetooth");
                // If bluetooth is not enabled request
                Intent bluetoothEnableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(bluetoothEnableIntent, RequestType.REQUEST_ENABLE_BT.getRequestCode());
            } else {
                // If bluetooth is not discoverable request
                Log.d(TAG, "Attempting to set device discoverable.");
                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
                startActivityForResult(discoverableIntent, RequestType.REQUEST_DISCOVER_BT.getRequestCode());
            }
        }
    }

    @Override
    public void initiateNewClient(Object client) {
        // TODO: IMPLEMENT SENDING QUEUE TO NEW CLIENT FOR BLUETOOTH
    }

    @Override
    protected void notifyClientsQueueUpdated(int currentPlayingIndex) {
        // TODO: IMPLEMENT QUEUE SENDING FOR BLUETOOTH
    }

    private class ReadThread extends Thread {
        BluetoothSocket mSocket;
        InputStream mInputStream;
        public ReadThread(BluetoothSocket socket) throws IOException {
            mSocket = socket;
            try {
                mInputStream = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Could not get input stream.");
                throw e;
            }
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytesRead;

            while (true) {
                try {
                    bytesRead = mInputStream.read(buffer);
                    if (bytesRead > 0) {
                        byte[] uriBytes = new byte[bytesRead];
                        for (int i = 0; i < bytesRead; i++) {
                            uriBytes[i] = buffer[i];
                        }
                        String trackUri = new String(uriBytes, StandardCharsets.UTF_8);
                        Log.d(TAG, "Read track URI! START: " + trackUri + " :END");
                        Message queueTrackMessage = mHandler.obtainMessage(QUEUE_TRACK);
                        Bundle trackBundle = new Bundle();
                        trackBundle.putString(BUNDLE_TRACK_KEY, trackUri);
                        queueTrackMessage.setData(trackBundle);
                        queueTrackMessage.sendToTarget();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d(TAG, "Could not read bytes.");
                    break;
                }
            }
        }
    }
}