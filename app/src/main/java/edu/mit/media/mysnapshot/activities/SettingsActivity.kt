package edu.mit.media.mysnapshot.activities

import android.app.DialogFragment
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.preference.PreferenceManager
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.health.connect.client.PermissionController
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.activities.fragments.CreditsFragment
import edu.mit.media.mysnapshot.activities.questions.QuestionActivity
import edu.mit.media.mysnapshot.activities.questions.QuestionListener
import edu.mit.media.mysnapshot.activities.questions.fragment.QuestionCheckboxFragment
import edu.mit.media.mysnapshot.activities.questions.fragment.QuestionChoiceFragment
import edu.mit.media.mysnapshot.activities.questions.fragment.QuestionDateFragment
import edu.mit.media.mysnapshot.activities.questions.fragment.QuestionFragment
import edu.mit.media.mysnapshot.activities.questions.fragment.QuestionHealthConnectFragment
import edu.mit.media.mysnapshot.activities.questions.fragment.QuestionNotificationFragment
import edu.mit.media.mysnapshot.activities.questions.fragment.QuestionRadioGroupFragment
import edu.mit.media.mysnapshot.activities.questions.fragment.QuestionSpinnerFragment
import edu.mit.media.mysnapshot.health.HealthConnectManager
import edu.mit.media.mysnapshot.view.SelectableIcon
import java.util.TimeZone

@AndroidEntryPoint
class SettingsActivity : QuestionActivity() {

    lateinit var sharedPreferences: SharedPreferences
    var userData: UserData? = null

    lateinit var terms: QuestionCheckboxFragment
    lateinit var healthConnect: QuestionHealthConnectFragment
    lateinit var notification: QuestionNotificationFragment
    lateinit var birthdate: QuestionDateFragment
    lateinit var race: QuestionSpinnerFragment
    lateinit var genders: QuestionChoiceFragment
    lateinit var happy: QuestionRadioGroupFragment
    lateinit var stress: QuestionRadioGroupFragment
    lateinit var activityLevel: QuestionSpinnerFragment
    lateinit var sleepQuality: QuestionRadioGroupFragment

    private val healthConnectPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        healthConnect.onHealthConnectPermissionResult(
            grantedPermissions.containsAll(HealthConnectManager.PERMISSIONS)
        )
    }

    fun requestHealthConnectPermissions() {
        healthConnectPermissionLauncher.launch(HealthConnectManager.PERMISSIONS)
    }

    override fun getLayoutId(): Int = R.layout.activity_settings

    override fun initFragments(fragments: MutableList<Fragment>, icons: MutableList<Drawable>) {
        initTerms(fragments)
        initHealthConnect(fragments)
        initBirthdate(fragments)
        initRace(fragments)
        initGenders(fragments)
        initNotification(fragments)
        initHappy(fragments)
        initStress(fragments)
        initActivity(fragments)
        initSleepQuality(fragments)

        findViewById<View>(R.id.title).setOnClickListener { showCreditsDialog() }
    }

    override fun loadInitialData(): Boolean {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        val loaded = loadUserData(this)
        userData = loaded.userData

        if (!loaded.existed) {
            findViewById<View>(R.id.controls).visibility = View.GONE
        } else {
            findViewById<View>(R.id.backbutton).setOnClickListener { finish() }
            findViewById<View>(R.id.savebutton).setOnClickListener { onFinish() }
        }

        return loaded.existed
    }

    override fun onFinish() {
        getDataFromQuestions()

        val timezone = TimeZone.getDefault().id
        userData!!.timezone = timezone

        saveUserData()

        if (isBuildingData()) {
            val intent = Intent(this@SettingsActivity, IntroThanksActivity::class.java)
            startActivity(intent)

            finish()
            overridePendingTransition(0, 0)
        } else {
            Toast.makeText(this, "Saved!", Toast.LENGTH_LONG).show()
            val intent = Intent(this@SettingsActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun initGenders(fragments: MutableList<Fragment>) {
        genders = QuestionChoiceFragment()
        genders.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_gender, "What's your gender?"))
        genders.addChoice(SelectableIcon.IconChoice("Male", R.drawable.question_icon_male, "m", resources.getColor(R.color.gender_male)))
            .addChoice(SelectableIcon.IconChoice("Female", R.drawable.question_icon_female, "f", resources.getColor(R.color.gender_female)))
        genders.setListener(object : QuestionListener<String>() {
            override fun onSelected(value: String) {
                onPageComplete()
            }
        })
        if (!isBuildingData()) {
            genders.setValue(userData!!.gender)
        }
        fragments.add(genders)
    }

    private fun initTerms(fragments: MutableList<Fragment>) {
        terms = QuestionCheckboxFragment()
        terms.setLayout(QuestionFragment.Layout(R.drawable.art_icon, "Terms and Conditions\nof Science!"))
        terms.setText(resources.getString(R.string.terms_text))
        terms.setListener(object : QuestionListener<Boolean>() {
            override fun onSelected(value: Boolean) {
                if (value) {
                    onPageComplete()
                    terms.checkbox.isChecked = true
                    terms.checkbox.isEnabled = false
                    userData!!.acceptedTerms = value
                }
            }
        })
        if (!isBuildingData()) {
            terms.setValue(userData!!.acceptedTerms)
        }
        fragments.add(terms)
    }

    private fun initNotification(fragments: MutableList<Fragment>) {
        notification = QuestionNotificationFragment()
        notification.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_alarm, "Would you like daily notification reminders?"))
        notification.setListener(object : QuestionListener<QuestionNotificationFragment.NotificationData>() {
            override fun onSelected(value: QuestionNotificationFragment.NotificationData) {
                onPageComplete()
            }

            override fun onDataSave(value: QuestionNotificationFragment.NotificationData) {
                onPageComplete()
                waitThenSlidePage()
            }
        })
        notification.setValue(userData!!.notificationData)
        fragments.add(notification)
    }

    private fun initBirthdate(fragments: MutableList<Fragment>) {
        birthdate = QuestionDateFragment()
        birthdate.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_birthday, "When is your birthdate?"))
        birthdate.setListener(object : QuestionListener<String>() {
            override fun onSelected(value: String) {
                onPageComplete()
            }
        })
        if (!isBuildingData()) {
            birthdate.setValue(userData!!.dobString)
        }
        fragments.add(birthdate)
    }

    private fun initRace(fragments: MutableList<Fragment>) {
        race = QuestionSpinnerFragment()
        race.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_race, "Which of these describes you the best?"))
        race.init(R.array.racevalues, R.array.races, "Please Select an Option")
        race.setListener(object : QuestionListener<String>() {
            override fun onSelected(value: String) {
                onPageComplete()
            }
        })
        if (!isBuildingData()) {
            race.setValue(userData!!.race)
        }
        fragments.add(race)
    }

    private fun initHealthConnect(fragments: MutableList<Fragment>) {
        healthConnect = QuestionHealthConnectFragment()
        healthConnect.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_activity, "Please share your step and sleep data from Health Connect."))
        healthConnect.setListener(object : QuestionListener<Boolean>() {
            override fun onSelected(value: Boolean) {
                onPageComplete()
            }

            override fun onDataSave(value: Boolean) {
                onPageComplete()
                waitThenSlidePage()
            }
        })
        if (!isBuildingData()) {
            healthConnect.setValue(userData!!.healthConnectGranted)
        }
        fragments.add(healthConnect)
    }

    private fun initActivity(fragments: MutableList<Fragment>) {
        activityLevel = QuestionSpinnerFragment()
        activityLevel.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_activity, "On average, how many hours are you active each day?"))
        activityLevel.init(R.array.activityvalues, R.array.activity, "Please Select an Option")
        activityLevel.setListener(object : QuestionListener<String>() {
            override fun onSelected(value: String) {
                onPageComplete()
            }
        })
        if (!isBuildingData()) {
            activityLevel.setValue(userData!!.activity)
        }
        fragments.add(activityLevel)
    }

    private fun initStress(fragments: MutableList<Fragment>) {
        stress = QuestionRadioGroupFragment()
        stress.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_stress, "What is your average stress level?"))
        stress.init("Very Low", "Very High", resources.getColor(R.color.radio_green), resources.getColor(R.color.radio_red), 7)
        stress.setListener(object : QuestionListener<Int>() {
            override fun onSelected(value: Int) {
                onPageComplete()
            }
        })
        if (!isBuildingData()) {
            stress.setValue(userData!!.stress)
        }
        fragments.add(stress)
    }

    private fun initHappy(fragments: MutableList<Fragment>) {
        happy = QuestionRadioGroupFragment()
        happy.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_happiness, "What is your average happiness level?"))
        happy.init("Very Unhappy", "Very Happy")
        happy.setListener(object : QuestionListener<Int>() {
            override fun onSelected(value: Int) {
                onPageComplete()
            }
        })
        if (!isBuildingData()) {
            happy.setValue(userData!!.happy)
        }
        fragments.add(happy)
    }

    private fun initSleepQuality(fragments: MutableList<Fragment>) {
        sleepQuality = QuestionRadioGroupFragment()
        sleepQuality.setLayout(QuestionFragment.Layout(R.drawable.icon_settings_sleep, "On average, how well do you sleep?"))
        sleepQuality.init("Terrible", "Great!")
        sleepQuality.setListener(object : QuestionListener<Int>() {
            override fun onSelected(value: Int) {
                onPageComplete()
            }
        })
        if (!isBuildingData()) {
            sleepQuality.setValue(userData!!.sleepQuality)
        }
        fragments.add(sleepQuality)
    }

    private fun getDataFromQuestions() {
        val data = userData!!
        data.acceptedTerms = terms.value
        data.healthConnectGranted = healthConnect.value
        data.dobString = birthdate.value
        data.race = race.value
        data.gender = genders.value
        data.happy = happy.value
        data.stress = stress.value
        data.activity = activityLevel.value
        data.sleepQuality = sleepQuality.value
        data.notificationData = notification.value
    }

    private fun saveUserData() {
        val editor = sharedPreferences.edit()

        val data = userData
        if (data != null) {
            editor.putString(USERDATAPREF, Gson().toJson(data))
        } else {
            editor.remove(USERDATAPREF)
        }
        editor.apply()
    }

    private val DIALOG_TAG = "DialogFragment"

    private fun showCreditsDialog() {
        val fragment = CreditsFragment()
        fragment.setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Light_NoTitleBar_Fullscreen)
        fragment.isCancelable = false

        fragment.show(fragmentManager.beginTransaction(), DIALOG_TAG)
    }

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

        var notificationData: QuestionNotificationFragment.NotificationData? = null
            get() = field ?: QuestionNotificationFragment.NotificationData()
    }

    companion object {
        const val LOGTAG = "SettingsActivity"
        const val USERDATAPREF = "userdataprefyo"

        @JvmStatic
        fun hasSetUserData(sharedPreferences: SharedPreferences): Boolean =
            sharedPreferences.contains(USERDATAPREF)

        class UserDataLoaded {
            var userData: UserData = UserData()
            var existed = true
        }

        @JvmStatic
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
    }
}
