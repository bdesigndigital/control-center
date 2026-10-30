package com.bdesign.controlcenter

import android.service.notification.NotificationListenerService

/**
 * Ne fait rien lui-même : l'accès « notifications » accordé à ce service
 * permet à l'app de lire la session média active (titre, pochette, lecture).
 */
class MediaListenerService : NotificationListenerService()
