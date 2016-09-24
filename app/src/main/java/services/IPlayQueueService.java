package services;

import android.os.Binder;
import android.os.IBinder;

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
