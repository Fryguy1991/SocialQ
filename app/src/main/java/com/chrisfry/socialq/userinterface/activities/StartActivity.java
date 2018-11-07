package com.chrisfry.socialq.userinterface.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatCheckBox;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;

import com.chrisfry.socialq.R;
import com.chrisfry.socialq.business.AppConstants;

/**
 * Start screen for the application
 */

public class StartActivity extends AppCompatActivity {
    private static final String TAG = StartActivity.class.getName();

    private boolean mIsFairPlayChecked;
    private String mQueueTitle;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case AppConstants.PERMISSION_REQUEST:
                if (grantResults.length > 0) {
                    // Received requested permissions
                } else {
                    // Permissions rejected.
                    // TODO: This doesn't seem to be closing the activity.
                    StartActivity.this.finish();
                }
                break;
            default :
                // Do nothing
                break;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start_screen);

        mIsFairPlayChecked = getResources().getBoolean(R.bool.fair_play_default);
        mQueueTitle = getResources().getString(R.string.queue_title_default_value);

        findViewById(R.id.btn_host_queue).setOnClickListener(mTypeSelect);
        findViewById(R.id.btn_join_queue).setOnClickListener(mTypeSelect);

        // Check and see if we need to request permissions at runtime
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, Process.myPid(), Process.myUid()) !=
                    PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, AppConstants.PERMISSION_REQUEST);
            }
        }
    }

    View.OnClickListener mTypeSelect = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.btn_host_queue:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(StartActivity.this);
                        dialogBuilder.setTitle(R.string.queue_options);

                        // Inflate content view and get references to UI elements
                        View contentView = getLayoutInflater().inflate(R.layout.new_queue_dialog_content_layout, null);
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
                                Intent startQueueIntent = new Intent(StartActivity.this, HostActivityNearbyDevices.class);
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

                        dialogBuilder.create().show();
                    }
                    break;
                case R.id.btn_join_queue:
                    startActivity(new Intent(StartActivity.this, QueueConnectActivityNearbyDevices.class));
                    break;
            }
        }
    };

    private void hideKeyboard(View focusedView) {
        InputMethodManager inputManager = ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE));
        if (inputManager != null && focusedView != null && focusedView.isFocused()) {
            inputManager.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
        }
    }
}
