package userinterface.adapters.holders;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import chrisfry.spotifydj.R;

/**
 * Holder for displaying a track's name and artist
 */
public class TrackHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
    // Get references to UI elements
    private TextView mTrackNameView;
    private TextView mArtistNameView;
    private String mTrackUri;

    private ItemSelectionListener mItemSelectionListener;

    public TrackHolder(View v) {
        super(v);
        mTrackNameView = (TextView) v.findViewById(R.id.tv_track_name);
        mArtistNameView = (TextView) v.findViewById(R.id.tv_artist_name);
        v.setOnClickListener(this);
    }

    public void setArtistName(String artistName) {
        mArtistNameView.setText(artistName);
    }

    public void setTrackName(String trackName) {
        mTrackNameView.setText(trackName);
    }

    public void setTrackUri(String uri) {
        mTrackUri = uri;
    }

    @Override
    public void onClick(View view) {
        mItemSelectionListener.onItemSelected(mTrackUri);
    }

    public interface ItemSelectionListener {
        void onItemSelected(String uri);
    }

    public void setItemSelectionListener(ItemSelectionListener listener) {
        mItemSelectionListener = listener;
    }
}
