package com.chrisfry.socialq.userinterface.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatCheckBox;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;

import com.chrisfry.socialq.R;
import com.chrisfry.socialq.business.AppConstants;
import com.chrisfry.socialq.enums.RequestType;
import com.chrisfry.socialq.enums.UserType;

/**
 * Start screen for the application
 */

public class StartActivity extends AppCompatActivity {
    private static final String TAG = StartActivity.class.getName();

    // Used as a flag to determine if we need to launch a host or client after a permission request
    private UserType mUserType = UserType.NONE;

    // Values for handling user input during host dialog
    private boolean mIsFairPlayChecked;
    private String mQueueTitle;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Closing StartActivity");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        RequestType requestType = RequestType.Companion.getRequestTypeFromRequestCode(requestCode);
        Log.d(TAG, "Received request type: " + requestType);

        // Handle request result
        switch (requestType) {
            case LOCATION_PERMISSION_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Received location permission");
                    // Received location permission.  If button for host/client was pressed launch respective action
                    switch (mUserType) {
                        case HOST:
                            handleHostStart();
                            break;
                        case CLIENT:
                            handleClientStart();
                            break;
                        case NONE:
                        default:
                            // Nothing to handle here
                    }
                } else {
                    // Permissions rejected. User will see permissions request until permission is granted or else
                    // the application will not be able to function
                    Log.d(TAG, "Location permission rejected");
                    mUserType = UserType.NONE;
                }
                break;
            default:
                // Do nothing
                break;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start_screen);

        findViewById(R.id.btn_host_queue).setOnClickListener(mTypeSelect);
        findViewById(R.id.btn_join_queue).setOnClickListener(mTypeSelect);

        // This method checks if we have the needed permissions and requests them if needed
        hasLocationPermission();
    }

    /**
     * Determines if ACCESS_COARSE_LOCATION permission has been granted and requests it if needed
     *
     * @return - true if permission is already granted, false (and requests) if not
     */
    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, RequestType.LOCATION_PERMISSION_REQUEST.getRequestCode());
                return false;
            }
        }
        // If low enough SDK version, manifest contains permission and doesn't need to be requested at runtime
        return true;
    }

    View.OnClickListener mTypeSelect = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.btn_host_queue:
                    mUserType = UserType.HOST;
                    // Ensure we have location permission before starting a host
                    if (hasLocationPermission()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            handleHostStart();
                        }
                    }
                    break;
                case R.id.btn_join_queue:
                    mUserType = UserType.CLIENT;
                    // Ensure we have location permission before starting a client
                    if (hasLocationPermission()) {
                        handleClientStart();
                    }
                    break;
            }
        }
    };

    private void handleClientStart() {
        startActivity(new Intent(StartActivity.this, QueueConnectActivityNearbyDevices.class));
    }

    private void handleHostStart() {
        Log.d(TAG, "Launching host dialog");
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(StartActivity.this);
        dialogBuilder.setTitle(R.string.queue_options);

        // Reset default options
        mIsFairPlayChecked = getResources().getBoolean(R.bool.fair_play_default);
        mQueueTitle = getResources().getString(R.string.queue_title_default_value);

        // Inflate content view and get references to UI elements
        View contentView = getLayoutInflater().inflate(R.layout.new_queue_dialog, null);
        EditText queueNameEditText = contentView.findViewById(R.id.et_queue_name);
        AppCompatCheckBox fairPlayCheckbox = contentView.findViewById(R.id.cb_fairplay_checkbox);

        // Set dialog content view
        dialogBuilder.setView(contentView);

        queueNameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Don't care before text changed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Don't care on text changed
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Only care about result
                mQueueTitle = s.toString();
            }
        });

        fairPlayCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                buttonView.clearFocus();
                mIsFairPlayChecked = isChecked;
            }
        });

        dialogBuilder.setPositiveButton(R.string.start, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (dialog instanceof AlertDialog) {
                    hideKeyboard(((AlertDialog) dialog).getCurrentFocus());
                }

                // Ensure we don't display an empty queue title
                if (mQueueTitle.isEmpty()) {
                    mQueueTitle = getResources().getString(R.string.queue_title_default_value);
                }

                Intent startQueueIntent = new Intent(StartActivity.this, HostActivityKotlin.class);
                startQueueIntent.putExtra(AppConstants.QUEUE_TITLE_KEY, mQueueTitle);
                startQueueIntent.putExtra(AppConstants.FAIR_PLAY_KEY, mIsFairPlayChecked);
                startActivity(startQueueIntent);
                dialog.dismiss();
            }
        });
        dialogBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        // Show host queue options dialog
        dialogBuilder.create().show();
    }

    private void hideKeyboard(View focusedView) {
        InputMethodManager inputManager = ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE));
        if (inputManager != null && focusedView != null && focusedView.isFocused()) {
            inputManager.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
        }
    }
}
