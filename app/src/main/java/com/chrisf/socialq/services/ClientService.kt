package com.chrisf.socialq.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chrisf.socialq.R
import com.chrisf.socialq.AppConstants
import com.chrisf.socialq.dagger.components.ServiceComponent
import com.chrisf.socialq.enums.PayloadTransferUpdateStatus
import com.chrisf.socialq.model.spotify.PlaylistTrack
import com.chrisf.socialq.model.spotify.Track
import com.chrisf.socialq.network.BitmapListener
import com.chrisf.socialq.network.GetBitmapTask
import com.chrisf.socialq.processor.ClientProcessor
import com.chrisf.socialq.processor.ClientProcessor.ClientAction
import com.chrisf.socialq.processor.ClientProcessor.ClientAction.*
import com.chrisf.socialq.processor.ClientProcessor.ClientState
import com.chrisf.socialq.processor.ClientProcessor.ClientState.*
import com.chrisf.socialq.userinterface.App
import com.chrisf.socialq.userinterface.activities.ClientActivity
import com.chrisf.socialq.utils.DisplayUtils
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import timber.log.Timber
import java.io.IOException

class ClientService : BaseService<ClientState, ClientAction, ClientProcessor>(), BitmapListener {

    inner class ClientServiceBinder : Binder() {
        fun getService(): ClientService {
            return this@ClientService
        }
    }

    // SERVICE ELEMENTS
    // Binder for talking from bound activity to host
    private val clientServiceBinder = ClientServiceBinder()
    // Flag indicating if the service is bound to an activity
    private var isBound = false
    // Object listening for events from the service
    private var listener: ClientServiceListener? = null

    // NOTIFICATION ELEMENTS
    // Reference to notification manager
    private lateinit var notificationManager: NotificationManager
    // Builder for foreground notification
    private lateinit var notificationBuilder: NotificationCompat.Builder
    // Reference to media session
    private lateinit var mediaSession: MediaSessionCompat
    // Reference to meta data builder
    private val metaDataBuilder = MediaMetadataCompat.Builder()
    // Notification subtext content
    private lateinit var notificationSubtext: String
    // Media notification style object
    private val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()

    override fun resolveDependencies(serviceComponent: ServiceComponent) {
        serviceComponent.inject(this)
    }

    override fun handleState(state: ClientState) {
        when (state) {
            is ShutdownService -> handleShutdownService(state)
            is ConnectToHost -> connectToHost(state)
            ShowHostDisconnect -> showHostDisconnect()
            is SendPayloadToHost -> sendPayloadToHost(state)
            CloseClient -> handleCloseClient()
            is DisplayTracks -> displayTrackList(state)
            is ClientInitiationComplete -> handleClientInitiationComplete(state)
            DisplayLoading -> listener?.showLoadingScreen()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("Client service is being started")

        var titleString = getString(R.string.queue_title_default_value)
        if (intent != null) {
            // Get name of queue from intent
            val intentTitle = intent.getStringExtra(AppConstants.QUEUE_TITLE_KEY)
            if (!intentTitle.isNullOrEmpty()) {
                titleString = intentTitle
            }

            // Ensure our endpoint is valid and store it
            val endpointString = intent.getStringExtra(AppConstants.ND_ENDPOINT_ID_EXTRA_KEY)

            actionStream.accept(ServiceStarted(titleString, endpointString))
        }

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create media session so Android colorizes based on album art
        mediaSession = MediaSessionCompat(baseContext, AppConstants.CLIENT_MEDIA_SESSION_TAG)
        mediaSession.isActive = true

        val token = mediaSession.sessionToken
        if (token != null) {

            // Create intent/pending intent for returning to application when touching notification
            val notificationIntent = Intent(this, ClientActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

            // TODO: DO we need this color?
            val colorResInt = ContextCompat.getColor(this, R.color.Active_Button_Color)

            notificationSubtext = String.format(getString(R.string.client_notification_title_n_plus, titleString))

            //TODO: This style may not be the best for older versions of Android
            mediaStyle.setMediaSession(token)

            notificationBuilder = NotificationCompat.Builder(this, App.CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(notificationSubtext)
                    .setSmallIcon(R.drawable.app_notification_icon)
                    .setContentIntent(pendingIntent)
                    .setColorized(true)
                    .setShowWhen(false)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)

            // Start service in the foreground
            startForeground(CLIENT_SERVICE_ID, notificationBuilder.build())

            // Let app object know that a service has been started
            App.hasServiceBeenStarted = true
        } else {
            Timber.e("Something went wrong initializing the media session")

            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        Timber.d("Client service is being bound")
        isBound = true
        return clientServiceBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Timber.d("Client service is completely unbound")
        isBound = false
        return true
    }

    override fun onRebind(intent: Intent?) {
        Timber.d("Client service is being rebound")
        isBound = true
    }

    override fun onDestroy() {
        Timber.d("Client service is ending")

        Timber.d("Disconnecting from host")
        Nearby.getConnectionsClient(this).stopAllEndpoints()

        App.hasServiceBeenStarted = false
        super.onDestroy()
    }

    private fun handleShutdownService(state: ShutdownService) {
        Toast.makeText(this, state.messageResourceId, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    private fun connectToHost(state: ConnectToHost) {
        Nearby.getConnectionsClient(this).requestConnection(
                "SocialQ Client",
                state.endpoint,
                mConnectionLifecycleCallback)
                .addOnSuccessListener { Timber.d("Successfully sent a connection request") }
                .addOnFailureListener {
                    Timber.e("Failed to send a connection request, can't connect")
                    Timber.e(it)
                    actionStream.accept(ConnectionToHostFailed)
                }
    }

    private fun sendPayloadToHost(state: SendPayloadToHost) {
        Nearby.getConnectionsClient(this).sendPayload(state.endpoint, state.payload)
    }

    private fun showHostDisconnect() {
        listener?.showHostDisconnectDialog()
    }

    private fun handleCloseClient() {
        Nearby.getConnectionsClient(this).stopAllEndpoints()
        listener?.closeClient()
    }

    private fun displayTrackList(state: DisplayTracks) {
        listener?.onQueueUpdated(state.trackList)

        if (state.trackList.isEmpty()) {
            clearTrackInfoFromNotification()
        } else {
            showTrackInNotification(state.trackList[0].track)
        }
    }

    private fun handleClientInitiationComplete(state: ClientInitiationComplete) {
        listener?.initiateView(state.queueTitle)
    }

    fun setClientServiceListener(listener: ClientServiceListener) {
        this.listener = listener
    }

    fun removeClientServiceListener() {
        listener = null
    }

    fun followPlaylist() {
        actionStream.accept(ClientRequestedPlaylistFollow)
    }

    fun sendTrackToHost(trackUri: String) {
        actionStream.accept(ClientSelectedATrack(trackUri))
    }

    fun requestDisconnect() {
        actionStream.accept(ClientRequestedDisconnect)
    }

    private val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Nearby.getConnectionsClient(this@ClientService).acceptConnection(endpointId, mPayloadCallback)
        }

        override fun onConnectionResult(endPoint: String, connectionResolution: ConnectionResolution) {
            when (connectionResolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Timber.d("Connection to host successful!")
                    actionStream.accept(ConnectionToHostSuccessful)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Timber.e("Connection to host rejected")
                    actionStream.accept(HostRejectedConnection)
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Timber.e("Failed to connect to host")
                    actionStream.accept(ConnectionToHostFailed)
                }
            }
        }

        override fun onDisconnected(endPoint: String) {
            Timber.e("Lost connection to host")
            actionStream.accept(ConnectionToHostFailed)
        }
    }

    private val mPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Timber.d("Client received a payload")
            when (payload.type) {
                Payload.Type.BYTES -> actionStream.accept(HandleHostPayload(payload))
                Payload.Type.STREAM, Payload.Type.FILE -> TODO("not implemented")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, payloadTransferUpdate: PayloadTransferUpdate) {
            val status = PayloadTransferUpdateStatus.getStatusFromConstant(payloadTransferUpdate.status)
            Timber.d("Payload Transfer to/from $endpointId has status $status")
        }
    }

    /**
     * Sets up metadata for displaying a track in the service notification and updates that notification.
     * WARNING: Notification manager and builder need to be setup before using this method.
     */
    private fun showTrackInNotification(trackToShow: Track) {
        // Update metadata for media session
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, trackToShow.album.name)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, trackToShow.name)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, trackToShow.name)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, DisplayUtils.getTrackArtistString(trackToShow))

        // Attempt to update album art in notification and metadata
        if (trackToShow.album.images.isNotEmpty()) {
            try {
                // Attempt to update album art in notification and metadata
                if (trackToShow.album.images.isNotEmpty()) {
                    val task = GetBitmapTask(this)
                    task.execute(trackToShow.album.images[0].url)
                }
            } catch (exception: IOException) {
                Timber.e("Error retrieving image bitmap: ${exception.message.toString()}")
                Timber.e(exception)
            }
        }
        mediaSession.setMetadata(metaDataBuilder.build())

        // Update notification data
        notificationBuilder.setContentTitle(trackToShow.name)
        notificationBuilder.setContentText(DisplayUtils.getTrackArtistString(trackToShow))
        notificationBuilder.setStyle(mediaStyle)

        val notificationId = CLIENT_SERVICE_ID

        // Display updated notification
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun clearTrackInfoFromNotification() {
        mediaSession.setMetadata(null)

        @Suppress("RestrictedApi")
        notificationBuilder.mActions.clear()

        notificationBuilder.setContentTitle(getString(R.string.app_name))
        notificationBuilder.setContentText(notificationSubtext)
        notificationBuilder.setSubText("")
        notificationBuilder.setLargeIcon(null)
        notificationBuilder.setStyle(null)

        // Display updated notification
        notificationManager.notify(CLIENT_SERVICE_ID, notificationBuilder.build())
    }

    override fun displayBitmap(bitmap: Bitmap?) {
        // Set bitmap data for lock screen display
        metaDataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
        // Set bitmap data for notification
        notificationBuilder.setLargeIcon(bitmap)
        // Display updated notification
        notificationManager.notify(CLIENT_SERVICE_ID, notificationBuilder.build())
    }

    companion object {
        private const val CLIENT_SERVICE_ID = 2
    }

    // Interface used to cast listeners for client service events
    interface ClientServiceListener {

        fun onQueueUpdated(queueTracks: List<PlaylistTrack>)

        fun showLoadingScreen()

        fun showHostDisconnectDialog()

        fun closeClient()

        fun initiateView(queueTitle: String)

        fun failedToConnect()
    }
}