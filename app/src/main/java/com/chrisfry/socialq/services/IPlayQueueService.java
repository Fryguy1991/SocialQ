package com.chrisfry.socialq.services;

import kaaes.spotify.webapi.android.models.Track;

/**
 * Interface for interracting with play queue service.
 */
public interface IPlayQueueService {

    void play();

    void pause();

    void playNextInQueue();

    void addSongToQueue(Track track);

    boolean isPlaying();
}
