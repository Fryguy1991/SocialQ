package com.chrisfry.socialq.userinterface.activities;

import android.support.annotation.NonNull;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import com.chrisfry.socialq.business.AppConstants;

public class HostActivityNearbyDevices extends HostActivity {

    ConnectionLifecycleCallback mConnectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
            // For now.  Automatically accept connection.
            Nearby.getConnectionsClient(HostActivityNearbyDevices.this).acceptConnection(endpointId, mPayloadCallback);
        }

        @Override
        public void onConnectionResult(String endpointId, ConnectionResolution resolution) {
            switch (resolution.getStatus().getStatusCode()) {
                case ConnectionsStatusCodes.STATUS_OK:
                    // Connection is established.  Can transfer data.
                    break;
                case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                    // Connection was rejected
                    break;
                case ConnectionsStatusCodes.STATUS_ERROR:
                    // Error occurred before connection could be established
                    break;
            }
        }

        @Override
        public void onDisconnected(String endpointId) {
            // Connection has been ended.
        }
    };

    PayloadCallback mPayloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(String enpointId, Payload payload) {

        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate payloadTransferUpdate) {

        }
    };


    @Override
    void startHostConnection() {
        // Create advertising options (strategy)
        AdvertisingOptions options =
                new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build();

        Nearby.getConnectionsClient(this).startAdvertising("SocialQ Host",
                AppConstants.SERVICE_NAME,
                mConnectionLifecycleCallback,
                options).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                // Successfully started advertising service.
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // Failed to advertise service.
            }
        });
    }
}
