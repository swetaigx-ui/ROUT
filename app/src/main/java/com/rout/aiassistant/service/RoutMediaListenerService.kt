package com.rout.aiassistant.service

import android.service.notification.NotificationListenerService

/**
 * Exists only so ROUT can act as a MediaSessionManager listener for media control.
 * Enabled explicitly by the user in system notification-access settings.
 * ROUT does not read notification contents.
 */
class RoutMediaListenerService : NotificationListenerService()
