package com.chrisfry.socialq.userinterface.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import com.chrisfry.socialq.R;
import kaaes.spotify.webapi.android.models.TrackSimple;

import com.chrisfry.socialq.userinterface.adapters.holders.TrackHolder;
import com.chrisfry.socialq.utils.DisplayUtils;

/**
 * Adapter for displaying queue tracks
 */
public class SearchTrackListAdapter extends RecyclerView.Adapter implements TrackHolder.ItemSelectionListener {
    // Reference to queue track list
    protected List<TrackSimple> mTrackList;
    // String for uri of track with exposed add button
    protected String mExposedUri = "";

    private TrackSelectionListener mTrackSelectionListener;

    public SearchTrackListAdapter(List<TrackSimple> trackList) {
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
        TrackSimple track = mTrackList.get(position);
        trackHolder.setArtistName(DisplayUtils.getTrackArtistString(track));
        trackHolder.setTrackName(track.name);
        trackHolder.setTrackUri(track.uri);
        trackHolder.setItemSelectionListener(this);
        trackHolder.setAddButtonStatus(mExposedUri.equals(track.uri));
    }

    @Override
    public int getItemCount() {
        return mTrackList.size();
    }

    public void updateQueueList(List<? extends TrackSimple> newQueue) {
        mTrackList = (List<TrackSimple>) newQueue;
        notifyDataSetChanged();
    }

    @Override
    public void onTrackSelected(String uri) {
        if (mTrackSelectionListener != null) {
            for (TrackSimple track : mTrackList) {
                if (track.uri.equals(uri)) {
                    mTrackSelectionListener.onTrackSelection(track);
                    break;
                }
            }
        }
    }

    @Override
    public void onAddTrackExposed(String uri) {
        mExposedUri = uri;

        // TODO: Look for better way to do this.  Updating entire data
        // set disables add button animation and is not very efficient.
        notifyDataSetChanged();
    }

    public interface TrackSelectionListener {
        void onTrackSelection(TrackSimple track);
    }

    public void setTrackSelectionListener(TrackSelectionListener listener) {
        mTrackSelectionListener = listener;
    }
}
