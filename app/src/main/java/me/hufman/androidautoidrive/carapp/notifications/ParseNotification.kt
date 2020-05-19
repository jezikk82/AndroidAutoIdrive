package me.hufman.androidautoidrive.carapp.notifications

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.util.Log

object ParseNotification {

	/**
	 * Summarize an Android Notification into what should be shown in the car
	 */
	fun summarizeNotification(sbn: StatusBarNotification): CarNotification {
		var title:String? = null
		var text:String? = null
		var summary:String? = null
		val extras = sbn.notification.extras
		var icon = sbn.notification.smallIcon
		var picture: Bitmap? = null
		var pictureUri: String? = null

		// get the main title and text
		extras.getCharSequence(Notification.EXTRA_TITLE)?.let { title = it.toString() }
		extras.getCharSequence(Notification.EXTRA_TEXT)?.let { text = it.toString() }

		// full expanded view, like an email body
		extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.let { title = it.toString() }
		extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { text = it.toString() }
		extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.let { summary = it.toString() }
		extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.let { text = it.joinToString("\n") }

		// icon handling
		extras.getParcelable<Parcelable>(NotificationCompat.EXTRA_LARGE_ICON)?.let {
			// might have a user avatar, which might be an icon or a bitmap
			when (it) {
				is Icon -> icon = it
				is Bitmap -> icon = Icon.createWithBitmap(it)
			}
		}
		extras.getParcelable<Parcelable>(NotificationCompat.EXTRA_LARGE_ICON_BIG)?.let {
			// might have a user avatar, which might be an icon or a bitmap
			when (it) {
				is Icon -> icon = it
				is Bitmap -> icon = Icon.createWithBitmap(it)
			}
		}

		// maybe a picture too
		extras.getParcelable<Parcelable>(NotificationCompat.EXTRA_PICTURE)?.let {
			if (it is Bitmap) picture = it
		}

		// some extra handling for special notifications
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
				extras.getString(Notification.EXTRA_TEMPLATE) == "android.app.Notification\$MessagingStyle") {
			val parsed = summarizeMessagingNotification(sbn)
			text = parsed.text
			pictureUri = parsed.pictureUri
		}

		// clean out any emoji from the notification
		title = title?.let { UnicodeCleaner.clean(it) }
		summary = summary?.let { UnicodeCleaner.clean(it) }
		text = text?.let { UnicodeCleaner.clean(it) }

		val summarized = CarNotification(sbn.packageName, sbn.key, icon, sbn.isClearable, sbn.notification.actions ?: arrayOf(),
				title, summary, text?.trim(), picture, pictureUri)
		return summarized
	}

	@RequiresApi(Build.VERSION_CODES.O)
	fun summarizeMessagingNotification(sbn: StatusBarNotification): MessagingNotificationParsed {
		val extras = sbn.notification.extras
		val historicMessages = extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES) ?: arrayOf()
		val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: arrayOf()
		val recentMessages = (historicMessages + messages).filterIsInstance<Bundle>().takeLast(10)
		// parse out the lines of chat
		val text = recentMessages.joinToString("\n") {
			"${it.getCharSequence("sender")}: ${it.getCharSequence("text")}"
		}
		val pictureUri = recentMessages.filter {
			it.getCharSequence("type")?.startsWith("image/") == true
		}.map {
			it.getParcelable("uri") as Uri
		}.lastOrNull()?.toString()
		return MessagingNotificationParsed(text, pictureUri)
	}

	fun shouldPopupNotification(sbn: StatusBarNotification?): Boolean {
		if (sbn == null) return false
		if (!sbn.isClearable) return false
		if (sbn.notification.isGroupSummary()) return false
		val isMusic = sbn.notification.extras.getString(Notification.EXTRA_TEMPLATE) == "android.app.Notification\$MediaStyle"
		if (isMusic) return false
		return true

	}

	fun shouldShowNotification(sbn: StatusBarNotification): Boolean {
		return !sbn.notification.isGroupSummary() &&
				sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT) != null &&
				(sbn.isClearable || sbn.notification.actions?.isNotEmpty() == true)
	}

	fun dumpNotification(title: String, sbn: StatusBarNotification) {
		val extras = sbn.notification.extras
		val details = extras?.keySet()?.map { "  ${it}=>${extras.get(it)}" }?.joinToString("\n") ?: ""
		Log.i(NotificationListenerServiceImpl.TAG, "$title: ${extras?.get("android.title")} with the keys:\n$details")
	}
	fun dumpMessage(title: String, bundle: Bundle) {
		val details = bundle.keySet()?.map { key -> "  ${key}=>${bundle.get(key)}" }?.joinToString("\n") ?: ""
		Log.i(NotificationListenerServiceImpl.TAG, "$title $details")
	}
}

data class MessagingNotificationParsed(val text: String, val pictureUri: String?)