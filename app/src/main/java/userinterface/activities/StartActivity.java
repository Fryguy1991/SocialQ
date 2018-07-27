package userinterface.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import chrisfry.socialq.R;

/**
 * Start screen for the application
 */

public class StartActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start_screen);

        findViewById(R.id.btn_host_queue).setOnClickListener(mTypeSelect);
        findViewById(R.id.btn_join_queue).setOnClickListener(mTypeSelect);
    }

    View.OnClickListener mTypeSelect = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.btn_host_queue:
                    startActivity(new Intent(StartActivity.this, HostActivity.class));
                    break;
                case R.id.btn_join_queue:
                    startActivity(new Intent(StartActivity.this, QueueConnectActivity.class));
                    break;
            }
        }
    };
}
