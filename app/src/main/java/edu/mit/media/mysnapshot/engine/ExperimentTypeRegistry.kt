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
 *
 * Issue #31 adds a second, user-authored source: types created on-device with a custom
 * input/output signal, persisted via `CustomExperimentTypeDao` and merged in at runtime by
 * [refreshCustomTypes] -- [all] is always bundled + custom, bundled types taking priority on
 * a `typeKey` collision.
 */
object ExperimentTypeRegistry {

    private const val ASSET_FILE = "experiment_types.json"

    @Volatile
    private var bundled: List<ExperimentType>? = null

    @Volatile
    private var custom: List<ExperimentType> = emptyList()

    val all: List<ExperimentType>
        get() = (bundled ?: error(
            "ExperimentTypeRegistry.load(context) has not run yet -- " +
                "MyApplication.onCreate() should always run first."
        )) + custom

    fun load(context: Context) {
        if (bundled != null) return
        bundled = parseConfig(context.assets.open(ASSET_FILE).use { it.reader().readText() })
    }

    /**
     * Replaces the merged-in set of user-created types. Called from
     * `ExperimentRepository.loadCustomTypes()`, itself invoked from `MyApplication.onCreate()`
     * and after any custom-type create so a newly authored type is immediately usable without
     * a process restart.
     */
    fun refreshCustomTypes(customTypes: List<ExperimentType>) {
        custom = customTypes
    }

    /** Context-free so the JSON config itself is plain-JUnit testable (no Robolectric). */
    internal fun parseConfig(json: String): List<ExperimentType> {
        val dtos = Gson().fromJson(json, Array<ExperimentTypeDto>::class.java)
        return dtos.map { it.toExperimentType() }
    }

    /** Test-only seam: lets plain-JUnit tests of code that calls [ExperimentType.fromTypeKey]
     *  (e.g. [edu.mit.media.mysnapshot.data.ExperimentExporter]) populate the registry
     *  without a real Android Context. Also resets any merged-in custom types, so each test
     *  class starts from a clean, bundled-only registry. */
    internal fun loadForTest(json: String) {
        bundled = parseConfig(json)
        custom = emptyList()
    }

    /**
     * Parses one user-created experiment type from the same DTO shape the bundled JSON config
     * uses -- this is the format persisted in `CustomExperimentTypeEntity.json`.
     */
    fun parseCustomTypeJson(json: String): ExperimentType =
        Gson().fromJson(json, ExperimentTypeDto::class.java).toExperimentType()

    /** Inverse of [parseCustomTypeJson], for persisting a newly authored custom type. */
    fun toCustomTypeJson(type: ExperimentType): String = Gson().toJson(ExperimentTypeDto.from(type))

    private data class ExperimentTypeDto(
        val typeKey: String,
        val name: String,
        val ranges: RangeTable,
        val rangeSize: Float,
        val stableRange: Float,
        val useVariability: Boolean,
        val shouldMinimizeResult: Boolean,
        val usesSleepData: Boolean,
        val inputSignal: SignalSource?,
        val customInputSignal: CustomSignalDef?,
        val outputSignal: SignalSource?,
        val customOutputSignal: CustomSignalDef?,
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
            inputSignal = toSignalRef(inputSignal, customInputSignal),
            outputSignal = toSignalRef(outputSignal, customOutputSignal),
            targetFormatKind = targetFormatKind,
            resultFormatKind = resultFormatKind,
            instructionTemplate = instructionTemplate,
            resultTemplate = resultTemplate,
            targetTemplate = targetTemplate
        )

        companion object {
            fun from(type: ExperimentType): ExperimentTypeDto {
                val (inputSignal, customInputSignal) = fromSignalRef(type.inputSignal)
                val (outputSignal, customOutputSignal) = fromSignalRef(type.outputSignal)
                return ExperimentTypeDto(
                    typeKey = type.typeKey,
                    name = type.name,
                    ranges = type.ranges,
                    rangeSize = type.rangeSize,
                    stableRange = type.stableRange,
                    useVariability = type.useVariability,
                    shouldMinimizeResult = type.shouldMinimizeResult,
                    usesSleepData = type.usesSleepData,
                    inputSignal = inputSignal,
                    customInputSignal = customInputSignal,
                    outputSignal = outputSignal,
                    customOutputSignal = customOutputSignal,
                    targetFormatKind = type.targetFormatKind,
                    resultFormatKind = type.resultFormatKind,
                    instructionTemplate = type.instructionTemplate,
                    resultTemplate = type.resultTemplate,
                    targetTemplate = type.targetTemplate
                )
            }

            private fun fromSignalRef(ref: SignalRef): Pair<SignalSource?, CustomSignalDef?> =
                when (ref) {
                    is SignalRef.Builtin -> ref.source to null
                    is SignalRef.Custom -> null to ref.definition
                }
        }
    }

    private fun toSignalRef(source: SignalSource?, custom: CustomSignalDef?): SignalRef = when {
        custom != null -> SignalRef.Custom(custom)
        source != null -> SignalRef.Builtin(source)
        else -> error("Experiment type DTO has neither a builtin signal nor a custom definition")
    }

    // Android resource IDs are aapt-generated compile-time constants, so they can't live in
    // the JSON config itself -- see the doc comment on ExperimentType. This is the one place
    // per experiment type that still requires a Kotlin change (plus adding the resources
    // themselves), which the plan calls out as an accepted, unavoidable limit of a fully
    // local/non-OTA config. User-created types (#31) never have an entry here -- they fall
    // back to the generic icon/banner/layout below, which render their name/signals at
    // runtime instead of baking copy into a per-type XML.
    //
    // NOTE: the sleepvariabilitystress/sleepdurationproductivity icons are intentionally
    // swapped below -- that matches the pre-5.4 sealed-class definitions exactly (not a bug
    // introduced by this refactor; left as-is since fixing it is out of scope here).
    private val introLayoutByKey = mapOf(
        "leisurehappiness" to R.layout.experiment_intro_leisurehappy,
        "sleepvariabilitystress" to R.layout.experiment_intro_sleepvariabilitystress,
        "sleepdurationproductivity" to R.layout.experiment_intro_sleepdurationproductivity,
        "stepssleepefficiency" to R.layout.experiment_intro_stepssleepefficiency,
        "exercisestress" to R.layout.experiment_intro_exercisestress,
        "stepshappiness" to R.layout.experiment_intro_stepshappiness,
        "leisureproductivity" to R.layout.experiment_intro_leisureproductivity
    )

    private val iconByKey = mapOf(
        "leisurehappiness" to R.drawable.icon_experiment_leisurehappiness,
        "sleepvariabilitystress" to R.drawable.icon_experiment_sleepdurationproductivity,
        "sleepdurationproductivity" to R.drawable.icon_experiment_sleepvariabilitystress,
        "stepssleepefficiency" to R.drawable.icon_experiment_stepssleepefficiency,
        "exercisestress" to R.drawable.icon_experiment_exercisestress,
        "stepshappiness" to R.drawable.icon_experiment_stepshappiness,
        "leisureproductivity" to R.drawable.icon_experiment_leisureproductivity
    )

    private val chooseBannerByKey = mapOf(
        "leisurehappiness" to R.drawable.experiment_choose_leisurehappy,
        "stepssleepefficiency" to R.drawable.experiment_choose_stepssleepefficiency,
        "sleepdurationproductivity" to R.drawable.experiment_choose_sleepdurationproductivity,
        "sleepvariabilitystress" to R.drawable.experiment_choose_sleepvariabilitystress,
        "exercisestress" to R.drawable.experiment_choose_exercisestress,
        "stepshappiness" to R.drawable.experiment_choose_stepshappiness,
        "leisureproductivity" to R.drawable.experiment_choose_leisureproductivity
    )

    /** Whether [typeKey] has dedicated, hand-authored intro/icon/banner resources. */
    fun hasDedicatedResources(typeKey: String): Boolean = introLayoutByKey.containsKey(typeKey)

    fun introLayoutId(typeKey: String): Int =
        introLayoutByKey[typeKey] ?: R.layout.experiment_intro_generic
    fun iconId(typeKey: String): Int = iconByKey[typeKey] ?: R.drawable.icon_experiment_generic
    fun chooseBannerIconId(typeKey: String): Int =
        chooseBannerByKey[typeKey] ?: R.drawable.experiment_choose_generic
}
