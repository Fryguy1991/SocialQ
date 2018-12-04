package com.chrisfry.socialq.userinterface.activities;

import android.app.Activity;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;

import com.chrisfry.socialq.R;
import com.chrisfry.socialq.userinterface.views.QueueItemDecoration;

/**
 * Activity used to search for and connect to a SocialQ
 */
public abstract class QueueConnectActivity extends Activity implements View.OnClickListener{
    private final String TAG = QueueConnectActivity.class.getName();

    private RecyclerView mQueueRecyclerView;

    protected View mQueueJoinButton;

    @Override
    protected void onResume() {
        super.onResume();

        mQueueJoinButton.setEnabled(false);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.queue_connect_screen);

        mQueueJoinButton = findViewById(R.id.btn_queue_join);
        mQueueJoinButton.setOnClickListener(this);

        mQueueRecyclerView = (RecyclerView) findViewById(R.id.rv_queue_list_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mQueueRecyclerView.setLayoutManager(layoutManager);
        mQueueRecyclerView.addItemDecoration(new QueueItemDecoration(getApplicationContext()));
        setupAdapter(mQueueRecyclerView);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_queue_join:
                connectToQueue();
                break;
        }
    }

    protected abstract void setupAdapter(RecyclerView recyclerView);

    protected abstract void searchForQueues();

    protected abstract void connectToQueue();
}
