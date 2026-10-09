package de.glut.launcher.service

import android.service.notification.NotificationListenerService

/**
 * Leerer Benachrichtigungs-Dienst. Android gibt die laufenden Mediensitzungen
 * (für das Musik-Widget) nur an Apps heraus, die als Benachrichtigungs-Listener
 * zugelassen sind. Benachrichtigungen selbst werden nicht gelesen.
 */
class MediaListenerService : NotificationListenerService()
