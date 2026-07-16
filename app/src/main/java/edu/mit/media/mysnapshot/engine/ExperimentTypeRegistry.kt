package edu.mit.media.mysnapshot.engine

import android.content.Context
import com.google.gson.Gson
import edu.mit.media.mysnapshot.R

/**
 * Loads the bundled `assets/experiment_types.json` config (Phase 5.4 of
 * AGENT_PLANS/MODERNIZE.md) into [ExperimentType] instances. [load] must run once before
 * any [ExperimentType.fromTypeKey]/[ExperimentType.getAllTypes] call -- `MyApplication`
 * does this synchronously in `onCreate()`, before any Activity can be created, so every
 * other call site can keep treating experiment types as always-available data (no Context
 * threading needed at each of the many call sites that pre-date this registry).
 */
object ExperimentTypeRegistry {

    private const val ASSET_FILE = "experiment_types.json"

    @Volatile
    private var loaded: List<ExperimentType>? = null

    val all: List<ExperimentType>
        get() = loaded ?: error(
            "ExperimentTypeRegistry.load(context) has not run yet -- " +
                "MyApplication.onCreate() should always run first."
        )

    fun load(context: Context) {
        if (loaded != null) return
        loaded = parseConfig(context.assets.open(ASSET_FILE).use { it.reader().readText() })
    }

    /** Context-free so the JSON config itself is plain-JUnit testable (no Robolectric). */
    internal fun parseConfig(json: String): List<ExperimentType> {
        val dtos = Gson().fromJson(json, Array<ExperimentTypeDto>::class.java)
        return dtos.map { it.toExperimentType() }
    }

    /** Test-only seam: lets plain-JUnit tests of code that calls [ExperimentType.fromTypeKey]
     *  (e.g. [edu.mit.media.mysnapshot.data.ExperimentExporter]) populate the registry
     *  without a real Android Context. */
    internal fun loadForTest(json: String) {
        loaded = parseConfig(json)
    }

    private data class ExperimentTypeDto(
        val typeKey: String,
        val name: String,
        val ranges: RangeTable,
        val rangeSize: Float,
        val stableRange: Float,
        val useVariability: Boolean,
        val shouldMinimizeResult: Boolean,
        val usesSleepData: Boolean,
        val inputSignal: SignalSource,
        val outputSignal: SignalSource,
        val targetFormatKind: FormatKind,
        val resultFormatKind: FormatKind,
        val instructionTemplate: String,
        val resultTemplate: String,
        val targetTemplate: String
    ) {
        fun toExperimentType() = ExperimentType(
            typeKey = typeKey,
            name = name,
            ranges = ranges,
            rangeSize = rangeSize,
            stableRange = stableRange,
            useVariability = useVariability,
            shouldMinimizeResult = shouldMinimizeResult,
            usesSleepData = usesSleepData,
            inputSignal = inputSignal,
            outputSignal = outputSignal,
            targetFormatKind = targetFormatKind,
            resultFormatKind = resultFormatKind,
            instructionTemplate = instructionTemplate,
            resultTemplate = resultTemplate,
            targetTemplate = targetTemplate
        )
    }

    // Android resource IDs are aapt-generated compile-time constants, so they can't live in
    // the JSON config itself -- see the doc comment on ExperimentType. This is the one place
    // per experiment type that still requires a Kotlin change (plus adding the resources
    // themselves), which the plan calls out as an accepted, unavoidable limit of a fully
    // local/non-OTA config.
    //
    // NOTE: the sleepvariabilitystress/sleepdurationproductivity icons are intentionally
    // swapped below -- that matches the pre-5.4 sealed-class definitions exactly (not a bug
    // introduced by this refactor; left as-is since fixing it is out of scope here).
    private val introLayoutByKey = mapOf(
        "leisurehappiness" to R.layout.experiment_intro_leisurehappy,
        "sleepvariabilitystress" to R.layout.experiment_intro_sleepvariabilitystress,
        "sleepdurationproductivity" to R.layout.experiment_intro_sleepdurationproductivity,
        "stepssleepefficiency" to R.layout.experiment_intro_stepssleepefficiency
    )

    private val iconByKey = mapOf(
        "leisurehappiness" to R.drawable.icon_experiment_leisurehappiness,
        "sleepvariabilitystress" to R.drawable.icon_experiment_sleepdurationproductivity,
        "sleepdurationproductivity" to R.drawable.icon_experiment_sleepvariabilitystress,
        "stepssleepefficiency" to R.drawable.icon_experiment_stepssleepefficiency
    )

    private val chooseBannerByKey = mapOf(
        "leisurehappiness" to R.drawable.experiment_choose_leisurehappy,
        "stepssleepefficiency" to R.drawable.experiment_choose_stepssleepefficiency,
        "sleepdurationproductivity" to R.drawable.experiment_choose_sleepdurationproductivity,
        "sleepvariabilitystress" to R.drawable.experiment_choose_sleepvariabilitystress
    )

    fun introLayoutId(typeKey: String): Int = introLayoutByKey.getValue(typeKey)
    fun iconId(typeKey: String): Int = iconByKey.getValue(typeKey)
    fun chooseBannerIconId(typeKey: String): Int = chooseBannerByKey.getValue(typeKey)
}
