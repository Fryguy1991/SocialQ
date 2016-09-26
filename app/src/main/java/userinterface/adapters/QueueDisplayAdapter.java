package userinterface.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import chrisfry.spotifydj.R;
import kaaes.spotify.webapi.android.models.Track;
import utils.DisplayUtils;

/**
 * Adapter for displaying queue tracks
 */
public class QueueDisplayAdapter extends RecyclerView.Adapter {
    // Reference to queue track list
    List<Track> mTrackList;

    public QueueDisplayAdapter(List<Track> trackList) {
        mTrackList = trackList;
    }

    public static class TrackHolder extends RecyclerView.ViewHolder {
        // Get references to UI elements
        private TextView mTrackNameView;
        private TextView mArtistNameView;

        public TrackHolder(View v) {
            super(v);
            mTrackNameView = (TextView) v.findViewById(R.id.tv_track_name);
            mArtistNameView = (TextView) v.findViewById(R.id.tv_artist_name);
        }

        public void setArtistName(String artistName) {
            mArtistNameView.setText(artistName);
        }

        public void setTrackName(String trackName) {
            mTrackNameView.setText(trackName);
        }
    }


    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View trackView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.queue_list_item, parent, false);
        return new TrackHolder(trackView);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        TrackHolder trackHolder = (TrackHolder) holder;
        Track track = mTrackList.get(position);
        trackHolder.setArtistName(DisplayUtils.getTrackArtistString(track));
        trackHolder.setTrackName(track.name);
    }

    @Override
    public int getItemCount() {
        return mTrackList.size();
    }

    public void updateQueueList(List<Track> newQueue) {
        mTrackList = newQueue;
        notifyDataSetChanged();
    }
}
