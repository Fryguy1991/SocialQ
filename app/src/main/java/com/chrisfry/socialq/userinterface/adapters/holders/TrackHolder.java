package com.chrisfry.socialq.userinterface.adapters.holders;

import android.graphics.Typeface;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.chrisfry.socialq.R;

/**
 * Holder for displaying a track's name and artist
 */
public class TrackHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
    // Get references to UI elements
    private TextView mTrackNameView;
    private TextView mArtistNameView;
    private View mAddButton;
    private String mTrackUri;

    private ItemSelectionListener mItemSelectionListener;

    public TrackHolder(View v) {
        super(v);
        mTrackNameView = v.findViewById(R.id.tv_name);
        mArtistNameView = v.findViewById(R.id.tv_artist_name);
        mAddButton = v.findViewById(R.id.iv_add_button);

        v.setOnClickListener(this);
        mAddButton.setOnClickListener(this);
    }

    public void setArtistName(String artistName) {
        mArtistNameView.setVisibility(View.VISIBLE);
        mArtistNameView.setText(artistName);
    }

    public void setTrackName(String trackName, boolean shouldBeBold) {
        mTrackNameView.setTypeface(null, shouldBeBold ? Typeface.BOLD : Typeface.NORMAL);
        setTrackName(trackName);
    }

    public void setTrackName(String trackName) {
        mTrackNameView.setText(trackName);
    }

    public void setTrackUri(String uri) {
        mTrackUri = uri;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.cl_track_holder:
                boolean isAddCurrentlyActive = mAddButton.isEnabled();
                setAddButtonStatus(!isAddCurrentlyActive);
                mItemSelectionListener.onAddTrackExposed(isAddCurrentlyActive ? "" : mTrackUri);
                break;
            case R.id.iv_add_button:
                mItemSelectionListener.onTrackSelected(mTrackUri);
                break;
            default:
                // Do nothing
        }
    }

    public void setAddButtonStatus(boolean shouldBeActive) {
        mAddButton.setVisibility(shouldBeActive ? View.VISIBLE : View.INVISIBLE);
        mAddButton.setEnabled(shouldBeActive);
    }

    public interface ItemSelectionListener {
        void onTrackSelected(String uri);

        void onAddTrackExposed(String uri);
    }

    public void setItemSelectionListener(ItemSelectionListener listener) {
        mItemSelectionListener = listener;
    }
}
