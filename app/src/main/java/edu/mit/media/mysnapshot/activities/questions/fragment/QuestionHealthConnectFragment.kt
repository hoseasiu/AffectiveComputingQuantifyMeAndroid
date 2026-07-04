package edu.mit.media.mysnapshot.activities.questions.fragment

import android.view.View
import android.view.ViewGroup
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.activities.SettingsActivity

/** Phase 3 replacement for the deleted QuestionJawboneFragment (Jawbone UP OAuth). */
class QuestionHealthConnectFragment : QuestionFragment<Boolean>() {

    private lateinit var doneButton: View

    override fun initViews(root: ViewGroup) {
        val connectButton = root.findViewById<View>(R.id.healthConnectButton)
        connectButton.setOnClickListener {
            (activity as SettingsActivity).requestHealthConnectPermissions()
        }

        doneButton = root.findViewById(R.id.doneButton)
        doneButton.setOnClickListener {
            listener.onDataSave(value)
        }

        if (isBuildingData) {
            doneButton.visibility = View.GONE
        }
    }

    fun onHealthConnectPermissionResult(granted: Boolean) {
        value = granted
        listener.onDataSave(value)
    }

    override fun getLayoutId(): Int = R.layout.fragment_question_health_connect
}
