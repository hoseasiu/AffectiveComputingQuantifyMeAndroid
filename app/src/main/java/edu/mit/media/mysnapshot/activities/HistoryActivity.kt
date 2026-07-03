package edu.mit.media.mysnapshot.activities

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import dagger.hilt.android.AndroidEntryPoint
import edu.mit.media.mysnapshot.R
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.database.ExperimentEntity
import edu.mit.media.mysnapshot.engine.ExperimentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class HistoryActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: ExperimentRepository

    private lateinit var swiper: SwipeRefreshLayout
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_history)

        list = findViewById(R.id.list)
        list.adapter = ExperimentAdapter(this)
        list.emptyView = findViewById(R.id.empty)

        swiper = findViewById(R.id.swiper)
        swiper.setOnRefreshListener { loadHistory() }

        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {}

            override fun onScroll(listView: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                val topRowVerticalPosition = if (listView.childCount == 0) 0 else listView.getChildAt(0).top
                swiper.isEnabled = topRowVerticalPosition >= 0
            }
        })

        loadHistory()
    }

    private fun loadHistory() {
        setRefreshing(true)
        lifecycleScope.launch {
            val experiments = repository.getAllExperiments().first()
            (list.adapter as ExperimentAdapter).setItems(experiments)
            setRefreshing(false)
        }
    }

    private inner class ExperimentAdapter(context: Context) :
        ArrayAdapter<ExperimentEntity>(context, R.layout.view_history_experiment) {

        fun setItems(items: List<ExperimentEntity>) {
            clear()
            addAll(items)
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val experiment = getItem(position)!!
            val experimentType = ExperimentType.fromTypeKey(experiment.type)

            val root = convertView ?: LayoutInflater.from(context).inflate(R.layout.view_history_experiment, parent, false)

            val background = root.findViewById<View>(R.id.background)

            val icon = root.findViewById<ImageView>(R.id.icon)
            icon.setImageResource(experimentType.iconId)

            val title = root.findViewById<TextView>(R.id.title)
            title.text = experimentType.name

            val days = Days.daysBetween(
                LocalDate(experiment.startTime),
                LocalDate(experiment.endTime ?: DateTime.now())
            ).days

            val dayCount = root.findViewById<TextView>(R.id.day_count)
            dayCount.text = "$days Days" +
                (if (experiment.isCancelled) " (Canceled)" else "") +
                (if (experiment.isActive) " (Active)" else "")

            val result = root.findViewById<TextView>(R.id.result)
            result.text = experiment.resultValue?.let { experimentType.formatInstruction(it) } ?: ""

            val confidence = root.findViewById<TextView>(R.id.confidence)
            confidence.text = Math.round((experiment.resultConfidence ?: 0f) * 100f).toString() + "%"

            val content = root.findViewById<ViewGroup>(R.id.content)
            for (i in 0 until content.childCount) {
                content.getChildAt(i).visibility = View.GONE
            }

            val cancelButton = root.findViewById<View>(R.id.cancel_button)
            when {
                experiment.isCancelled -> {
                    background.setBackgroundColor(resources.getColor(R.color.pageindicator_disabled))
                    root.findViewById<View>(R.id.canceled).visibility = View.VISIBLE
                    cancelButton.setOnClickListener(null)
                }
                experiment.isActive -> {
                    background.setBackgroundColor(resources.getColor(R.color.fadered))
                    root.findViewById<View>(R.id.in_progress).visibility = View.VISIBLE
                    cancelButton.setOnClickListener { cancelExperiment(experiment) }
                }
                else -> {
                    background.setBackgroundColor(resources.getColor(R.color.white))
                    root.findViewById<View>(R.id.finished).visibility = View.VISIBLE
                    cancelButton.setOnClickListener(null)
                }
            }

            return root
        }
    }

    private fun setRefreshing(refreshing: Boolean) {
        swiper.post { swiper.isRefreshing = refreshing }
    }

    private var dialog: AlertDialog? = null

    private fun cancelExperiment(experiment: ExperimentEntity) {
        val editText = android.widget.EditText(this)
        editText.setSingleLine()
        editText.hint = "Reason to Quit"
        editText.imeOptions = EditorInfo.IME_ACTION_GO

        editText.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO && editText.text.toString().isNotEmpty()) {
                cancelConfirmed(experiment)
                dialog?.hide()
                true
            } else {
                false
            }
        })

        dialog = AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle("Really Stop Experiment?")
            .setView(editText)
            .setMessage("All your progress will be lost forever! If you want to quit, please let us know why.")
            .setPositiveButton("Continue") { _, _ ->
                if (editText.text.toString().isNotEmpty()) {
                    cancelConfirmed(experiment)
                } else {
                    android.widget.Toast.makeText(this, "Please enter a reason", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel!", null)
            .show()
    }

    private fun cancelConfirmed(experiment: ExperimentEntity) {
        val progressDialog = AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_menu_delete)
            .setCancelable(false)
            .setTitle("Stopping Experiment")
            .show()

        lifecycleScope.launch {
            repository.cancelExperiment(experiment.id)
            progressDialog.dismiss()
            loadHistory()
        }
    }

    companion object {
        const val LOGTAG = "HistoryActivity"
    }
}
