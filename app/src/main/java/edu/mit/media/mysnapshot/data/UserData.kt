package edu.mit.media.mysnapshot.data

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.google.gson.Gson
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

/**
 * Onboarding/settings answers, persisted as a single Gson-serialized blob under
 * [USERDATAPREF] in the default [SharedPreferences] -- unchanged from the pre-Compose
 * `SettingsActivity.UserData`/`QuestionNotificationFragment.NotificationData` split (issue
 * #22 moved these here since they're a data concern, not a UI one, but kept every field name
 * identical so already-persisted blobs on real devices still round-trip through Gson).
 */
const val USERDATAPREF = "userdataprefyo"
const val DEFAULT_NOTIFICATION_TIME = "09:30"

class UserData {
    var acceptedTerms = false
    var gender: String? = null
    var race: String? = null
    var dobString: String? = null
    var happy = 0
    var stress = 0
    var sleepQuality = 0
    var activity: String? = null
    var healthConnectGranted: Boolean? = null
    var timezone: String? = null

    var notificationData: NotificationData? = null
        get() = field ?: NotificationData()
}

class NotificationData {
    // Nullable despite always being constructed with a default: Gson populates fields via
    // reflection (bypassing Kotlin's null-safety), so an old or hand-edited SharedPreferences
    // blob with an explicit `"notificationTime": null` can still leave this null at runtime --
    // matches the legacy Java field's real (unenforced) nullability.
    var notificationTime: String? = DEFAULT_NOTIFICATION_TIME
    var notificationSet: Boolean = true
}

class UserDataLoaded {
    var userData: UserData = UserData()
    var existed: Boolean = true
}

fun hasSetUserData(sharedPreferences: SharedPreferences): Boolean =
    sharedPreferences.contains(USERDATAPREF)

fun loadUserData(context: Context): UserDataLoaded {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val str = sharedPreferences.getString(USERDATAPREF, "")

    var existed = true
    var userData: UserData? = try {
        Gson().fromJson(str, UserData::class.java)
    } catch (e: Exception) {
        null
    }

    if (userData == null) {
        userData = UserData()
        existed = false
    }

    val loaded = UserDataLoaded()
    loaded.existed = existed
    loaded.userData = userData
    return loaded
}

fun saveUserData(context: Context, userData: UserData?) {
    val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
    if (userData != null) {
        editor.putString(USERDATAPREF, Gson().toJson(userData))
    } else {
        editor.remove(USERDATAPREF)
    }
    editor.apply()
}

/** `HH:mm` <-> [DateTime], the notification-time-of-day encoding used throughout Settings/schedulers. */
fun parseNotificationTime(value: String?): DateTime? {
    if (value == null) return null
    return DateTimeFormat.forPattern("HH:mm").parseDateTime(value)
}

fun encodeNotificationTime(value: DateTime?): String? {
    if (value == null) return null
    return DateTimeFormat.forPattern("HH:mm").print(value)
}
