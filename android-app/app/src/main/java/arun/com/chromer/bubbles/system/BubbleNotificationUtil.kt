package arun.com.chromer.bubbles.system

import android.app.*
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toBitmap
import arun.com.chromer.R
import arun.com.chromer.browsing.webview.EmbeddableWebViewActivity
import arun.com.chromer.shared.Constants
import arun.com.chromer.util.Utils
import dev.arunkumar.common.context.dpToPx
import io.reactivex.Single
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val BUBBLE_NOTIFICATION_CHANNEL_ID = "BUBBLE_NOTIFICATION_CHANNEL_ID"
private const val BUBBLE_NOTIFICATION_GROUP = "bubbles"

@Singleton
@RequiresApi(Build.VERSION_CODES.Q)
class BubbleNotificationUtil
@Inject
constructor(
        private val application: Application
) {

    private val notificationManager by lazy {
        application.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun createNotificationChannel() {
        var channel = notificationManager.getNotificationChannel(BUBBLE_NOTIFICATION_CHANNEL_ID)
        if (channel == null) {
            channel = NotificationChannel(
                    BUBBLE_NOTIFICATION_CHANNEL_ID,
                    "Bubbles notification channel",
                    IMPORTANCE_HIGH
            ).apply {
                description = "Channel for showing bubbles"
                setAllowBubbles(true)
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
                setBypassDnd(true)
                importance = IMPORTANCE_HIGH
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateGroupSummaryNotification(lastNotificationColor: Int) {
        val bubblesSummaryNotification = Notification.Builder(application, BUBBLE_NOTIFICATION_CHANNEL_ID).run {
            setContentTitle(application.getString(R.string.bubble_notification_group_title))
            setContentText(application.getString(R.string.bubble_notification_group_description))
            setGroup(BUBBLE_NOTIFICATION_GROUP)
            setGroupSummary(true)
            setAllowSystemGeneratedContextualActions(true)
            setColor(lastNotificationColor)
            setColorized(true)
            setLocalOnly(true)
            setOngoing(false)
            setSmallIcon(Icon.createWithResource(application, R.drawable.ic_chromer_notification))
            setLargeIcon(Icon.createWithResource(application, R.mipmap.ic_launcher_round))
            build()
        }
        notificationManager.notify(BUBBLE_NOTIFICATION_GROUP.hashCode(), bubblesSummaryNotification)
    }

    fun showBubbles(bubbleData: BubbleLoadData): Single<BubbleLoadData> = Single.fromCallable {
        createNotificationChannel()
        val context = bubbleData.contextRef.get() ?: application
        val website = bubbleData.website

        val bubbleIntent = PendingIntent.getActivity(
                context,
                website.url.hashCode(),
                Intent(context, EmbeddableWebViewActivity::class.java).apply {
                    data = Uri.parse(website.url)
                },
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val bubbleIcon: Icon = bubbleData.icon
                ?.let(Icon::createWithAdaptiveBitmap)
                ?: bubbleData.fallbackIcon()

        val displayHeight = (application.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay
                .let { display -> Point().apply(display::getSize).y }

        val bubbleNotification = Notification.Builder(context, BUBBLE_NOTIFICATION_CHANNEL_ID).run {
            setContentTitle(website.safeLabel())
            setContentText(website.preferredUrl())
            setGroup(BUBBLE_NOTIFICATION_GROUP)
            setAllowSystemGeneratedContextualActions(true)
            bubbleData.color.takeIf { it != Constants.NO_COLOR }?.let(::setColor)
            setColorized(true)
            setLocalOnly(true)
            setOngoing(false) // TODO Register a broadcast receiver and call notificationManager.cancel
            setSmallIcon(Icon.createWithResource(context, R.drawable.ic_chromer_notification))
            setLargeIcon(bubbleIcon)
            setBubbleMetadata(Notification.BubbleMetadata.Builder().run {
                setIcon(bubbleIcon)
                setIntent(bubbleIntent)
                setAutoExpandBubble(false)
                setSuppressNotification(true)
                setDesiredHeight(Utils.pxToDp((displayHeight * 0.8).toInt()))
                build()
            })
            addPerson(Person.Builder().run {
                setBot(false)
                setIcon(bubbleIcon)
                setName(website.safeLabel())
                setImportant(true)
                build()
            })
            build()
        }
        updateGroupSummaryNotification(bubbleData.color)
        notificationManager.notify(website.url.hashCode(), bubbleNotification)
        bubbleData
    }.doOnError(Timber::e).onErrorReturnItem(bubbleData)

    private fun BubbleLoadData.fallbackIcon(): Icon = if (color != Constants.NO_COLOR) {
        val iconSize = application.dpToPx(108.0)
        Icon.createWithAdaptiveBitmap(ColorDrawable(color).toBitmap(width = iconSize, height = iconSize))
    } else {
        Icon.createWithResource(application, R.mipmap.ic_launcher)
    }
}

