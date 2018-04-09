package userinterface.activities;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;

import chrisfry.spotifydj.R;

/**
 * Start screen for the application
 */

public class StartActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start_screen);
    }
}
