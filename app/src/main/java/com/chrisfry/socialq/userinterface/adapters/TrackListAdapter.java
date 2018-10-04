package com.chrisfry.socialq.userinterface.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import com.chrisfry.socialq.R;
import kaaes.spotify.webapi.android.models.Track;
import com.chrisfry.socialq.userinterface.adapters.holders.TrackHolder;
import com.chrisfry.socialq.utils.DisplayUtils;

/**
 * Adapter for displaying queue tracks
 */
public class TrackListAdapter extends RecyclerView.Adapter implements TrackHolder.ItemSelectionListener {
    // Reference to queue track list
    private List<Track> mTrackList;

    private TrackSelectionListener mTrackSelectionListener;

    public TrackListAdapter(List<Track> trackList) {
        mTrackList = trackList;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View trackView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.track_list_holder, parent, false);
        return new TrackHolder(trackView);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        TrackHolder trackHolder = (TrackHolder) holder;
        Track track = mTrackList.get(position);
        trackHolder.setArtistName(DisplayUtils.getTrackArtistString(track));
        trackHolder.setTrackName(track.name);
        trackHolder.setTrackUri(track.uri);
        trackHolder.setItemSelectionListener(this);
    }

    @Override
    public int getItemCount() {
        return mTrackList.size();
    }

    public void updateQueueList(List<Track> newQueue) {
        mTrackList = newQueue;
        notifyDataSetChanged();
    }

    @Override
    public void onItemSelected(String uri) {
        if (mTrackSelectionListener != null) {
            for (Track track : mTrackList) {
                if (track.uri.equals(uri)) {
                    mTrackSelectionListener.onTrackSelection(track);
                    break;
                }
            }
        }
    }

    public interface TrackSelectionListener {
        void onTrackSelection(Track track);
    }

    public void setTrackSelectionListener(TrackSelectionListener listener) {
        mTrackSelectionListener = listener;
    }
}
