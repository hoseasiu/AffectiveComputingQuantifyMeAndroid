package edu.mit.media.mysnapshot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.mit.media.mysnapshot.data.ExperimentRepository
import edu.mit.media.mysnapshot.engine.CustomRangePresets
import edu.mit.media.mysnapshot.engine.CustomSignalDef
import edu.mit.media.mysnapshot.engine.CustomValueKind
import edu.mit.media.mysnapshot.engine.ExperimentType
import edu.mit.media.mysnapshot.engine.FormatKind
import edu.mit.media.mysnapshot.engine.RangeTable
import edu.mit.media.mysnapshot.engine.SignalRef
import edu.mit.media.mysnapshot.engine.SignalSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sentinel [SignalSelection.sourceKey] for "define a new one", steering the wizard into a
 *  [CreateExperimentStep.INPUT_CUSTOM_FORM]/[CreateExperimentStep.OUTPUT_CUSTOM_FORM] page
 *  instead of one of the fixed [SignalSource]s -- issue #33. */
const val CUSTOM_SIGNAL_SENTINEL = "CUSTOM"

/** [SignalSource] values paired with picker-friendly labels, in enum-declaration order, plus a
 *  trailing "define a new one" entry keyed by [CUSTOM_SIGNAL_SENTINEL] -- shared by the input
 *  and output signal steps' [edu.mit.media.mysnapshot.ui.wizard.DropdownStep] instances. */
val SIGNAL_SOURCE_VALUES: Array<String> =
    (SignalSource.entries.map { it.name } + CUSTOM_SIGNAL_SENTINEL).toTypedArray()
val SIGNAL_SOURCE_LABELS: Array<String> =
    (SignalSource.entries.map { signalSourceLabel(it) } + "Define a new signal...").toTypedArray()

private fun signalSourceLabel(source: SignalSource): String = when (source) {
    SignalSource.CHECKIN_LEISURE_TIME -> "Leisure time (daily check-in)"
    SignalSource.CHECKIN_HAPPINESS -> "Happiness (daily check-in)"
    SignalSource.CHECKIN_STRESS -> "Stress level (daily check-in)"
    SignalSource.CHECKIN_PRODUCTIVITY -> "Productivity (daily check-in)"
    SignalSource.HEALTH_CONNECT_SLEEP_START_MINUTE -> "Sleep start time (Health Connect)"
    SignalSource.HEALTH_CONNECT_SLEEP_DURATION_MINUTES -> "Sleep duration (Health Connect)"
    SignalSource.HEALTH_CONNECT_STEPS -> "Daily steps (Health Connect)"
    SignalSource.HEALTH_CONNECT_SLEEP_EFFICIENCY -> "Sleep efficiency (Health Connect)"
    SignalSource.HEALTH_CONNECT_EXERCISE_MINUTES -> "Exercise minutes (Health Connect)"
}

/**
 * Wizard pages for [edu.mit.media.mysnapshot.activities.CreateExperimentActivity] (issue #33).
 * Ordinal order is the natural forward order, but [CreateExperimentUiState.steps] -- not this
 * enum's fixed `entries` -- is authoritative for position, since the two custom-signal-form
 * pages only exist when the matching signal step picked [CUSTOM_SIGNAL_SENTINEL] (the same
 * "state decides step count/shape" idea [edu.mit.media.mysnapshot.viewmodel.CheckinUiState.totalSteps]
 * uses for its 0-2 appended custom-signal pages -- except here a page can be inserted in the
 * *middle* of the sequence, not just appended, so every step-index lookup in this ViewModel
 * goes through `steps.indexOf(...)` rather than assuming `ordinal == position`).
 */
enum class CreateExperimentStep {
    NAME, INPUT_SIGNAL, INPUT_CUSTOM_FORM, OUTPUT_SIGNAL, OUTPUT_CUSTOM_FORM, TARGET_RANGE, CONFIRM
}

/**
 * One signal slot's in-progress selection: either a builtin [SignalSource] (by name) or
 * [CUSTOM_SIGNAL_SENTINEL] plus the [CustomSignalDef] fields being filled in on the form page
 * that choice reveals. [toSignalRef] is non-null exactly when this slot has everything it
 * needs to be part of a submittable [ExperimentType].
 */
data class SignalSelection(
    val sourceKey: String? = null,
    val customLabel: String = "",
    val customQuestion: String = "",
    val customKind: CustomValueKind? = null,
    val customLowLabel: String = "",
    val customHighLabel: String = "",
    val customUnitLabel: String = ""
) {
    val isCustom: Boolean get() = sourceKey == CUSTOM_SIGNAL_SENTINEL

    fun toSignalRef(): SignalRef? {
        val key = sourceKey ?: return null
        if (key != CUSTOM_SIGNAL_SENTINEL) return SignalRef.Builtin(SignalSource.valueOf(key))

        val kind = customKind ?: return null
        if (customLabel.isBlank() || customQuestion.isBlank()) return null
        return SignalRef.Custom(
            CustomSignalDef(
                label = customLabel.trim(),
                question = customQuestion.trim(),
                kind = kind,
                lowLabel = customLowLabel.trim().ifBlank { null },
                highLabel = customHighLabel.trim().ifBlank { null },
                unitLabel = customUnitLabel.trim().ifBlank { null }
            )
        )
    }
}

data class CreateExperimentUiState(
    val currentStep: Int = 0,
    val revealedSteps: Int = 1,
    val name: String = "",
    val input: SignalSelection = SignalSelection(),
    val output: SignalSelection = SignalSelection(),
    val ranges: RangeTable? = null,
    val rangeSize: Float? = null,
    val stableRange: Float? = null,
    val rangesTouchedByUser: Boolean = false,
    val showAdvanced: Boolean = false,
    val isSubmitting: Boolean = false
) {
    /** The pages that actually exist right now. Recomputed from current selections rather than
     *  stored, so it's always consistent with [input]/[output] -- see the class doc on
     *  [CreateExperimentStep] for why position must always be looked up here, never assumed. */
    val steps: List<CreateExperimentStep>
        get() = buildList {
            add(CreateExperimentStep.NAME)
            add(CreateExperimentStep.INPUT_SIGNAL)
            if (input.isCustom) add(CreateExperimentStep.INPUT_CUSTOM_FORM)
            add(CreateExperimentStep.OUTPUT_SIGNAL)
            if (output.isCustom) add(CreateExperimentStep.OUTPUT_CUSTOM_FORM)
            add(CreateExperimentStep.TARGET_RANGE)
            add(CreateExperimentStep.CONFIRM)
        }

    val totalSteps: Int get() = steps.size
    val currentStepKind: CreateExperimentStep get() = steps.getOrElse(currentStep) { CreateExperimentStep.NAME }

    /** Slugified, uniqueness-checked [ExperimentType.typeKey] preview -- recomputed live off
     *  [ExperimentType.getAllTypes] (issue #33's stated uniqueness check) rather than stored,
     *  so it can't go stale relative to types created by a previous run of this wizard. */
    val typeKey: String
        get() {
            if (name.isBlank()) return ""
            return uniqueTypeKey(slugify(name), ExperimentType.getAllTypes().map { it.typeKey }.toSet())
        }
}

sealed interface CreateExperimentEvent {
    data class NavigateToIntro(val type: ExperimentType) : CreateExperimentEvent
}

@HiltViewModel
class CreateExperimentViewModel @Inject constructor(
    private val repository: ExperimentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateExperimentUiState())
    val uiState: StateFlow<CreateExperimentUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<CreateExperimentEvent>(Channel.BUFFERED)
    val events: Flow<CreateExperimentEvent> = eventChannel.receiveAsFlow()

    fun onNameChanged(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun onNameContinue() {
        if (_uiState.value.name.isBlank()) return
        advance(CreateExperimentStep.NAME, forceSlide = true)
    }

    fun onInputSignalSelected(sourceKey: String) {
        selectSignal(isInput = true, sourceKey)
        advance(CreateExperimentStep.INPUT_SIGNAL)
    }

    fun onOutputSignalSelected(sourceKey: String) {
        selectSignal(isInput = false, sourceKey)
        advance(CreateExperimentStep.OUTPUT_SIGNAL)
    }

    /** Picking a signal source can change the wizard's page shape (a custom-form page
     *  appears/disappears), so any *later* progress is no longer meaningful -- truncate the
     *  reveal frontier back to just past this step, same as answering it for the first time. */
    private fun selectSignal(isInput: Boolean, sourceKey: String) {
        _uiState.update { state ->
            val stepKind = if (isInput) CreateExperimentStep.INPUT_SIGNAL else CreateExperimentStep.OUTPUT_SIGNAL
            val idx = state.steps.indexOf(stepKind)
            val selection = (if (isInput) state.input else state.output).copy(sourceKey = sourceKey)
            val truncated = state.copy(revealedSteps = minOf(state.revealedSteps, idx + 1))
            if (isInput) truncated.copy(input = selection) else truncated.copy(output = selection)
        }
        if (isInput && sourceKey != CUSTOM_SIGNAL_SENTINEL) applyDefaultRangePresetIfNeeded()
    }

    fun onInputCustomLabelChanged(value: String) = updateInput { it.copy(customLabel = value) }
    fun onInputCustomQuestionChanged(value: String) = updateInput { it.copy(customQuestion = value) }
    fun onInputCustomKindSelected(kind: CustomValueKind) = updateInput { it.copy(customKind = kind) }
    fun onInputCustomLowLabelChanged(value: String) = updateInput { it.copy(customLowLabel = value) }
    fun onInputCustomHighLabelChanged(value: String) = updateInput { it.copy(customHighLabel = value) }
    fun onInputCustomUnitLabelChanged(value: String) = updateInput { it.copy(customUnitLabel = value) }

    fun onInputCustomFormContinue() {
        if (_uiState.value.input.toSignalRef() == null) return
        applyDefaultRangePresetIfNeeded()
        advance(CreateExperimentStep.INPUT_CUSTOM_FORM, forceSlide = true)
    }

    fun onOutputCustomLabelChanged(value: String) = updateOutput { it.copy(customLabel = value) }
    fun onOutputCustomQuestionChanged(value: String) = updateOutput { it.copy(customQuestion = value) }
    fun onOutputCustomKindSelected(kind: CustomValueKind) = updateOutput { it.copy(customKind = kind) }
    fun onOutputCustomLowLabelChanged(value: String) = updateOutput { it.copy(customLowLabel = value) }
    fun onOutputCustomHighLabelChanged(value: String) = updateOutput { it.copy(customHighLabel = value) }
    fun onOutputCustomUnitLabelChanged(value: String) = updateOutput { it.copy(customUnitLabel = value) }

    fun onOutputCustomFormContinue() {
        if (_uiState.value.output.toSignalRef() == null) return
        advance(CreateExperimentStep.OUTPUT_CUSTOM_FORM, forceSlide = true)
    }

    private fun updateInput(transform: (SignalSelection) -> SignalSelection) {
        _uiState.update { it.copy(input = transform(it.input)) }
    }

    private fun updateOutput(transform: (SignalSelection) -> SignalSelection) {
        _uiState.update { it.copy(output = transform(it.output)) }
    }

    /** Derives a starting [RangeTable]/rangeSize/stableRange from the just-finalized input
     *  signal's shape (issue #33's "auto-derived from CustomValueKind" step) -- skipped once
     *  the user has touched the Advanced fields themselves, so their edits are never clobbered
     *  by re-selecting a signal that happens to resolve to the same or a different preset. */
    private fun applyDefaultRangePresetIfNeeded() {
        if (_uiState.value.rangesTouchedByUser) return
        val inputRef = _uiState.value.input.toSignalRef() ?: return
        val preset = CustomRangePresets.presetFor(inputRef.rangePresetKind())
        _uiState.update {
            it.copy(ranges = preset.ranges, rangeSize = preset.rangeSize, stableRange = preset.stableRange)
        }
    }

    fun onToggleAdvanced() {
        _uiState.update { it.copy(showAdvanced = !it.showAdvanced) }
    }

    fun onRangeUnderChanged(value: Float) = updateRanges { it?.copy(under = value) }
    fun onRangeN1Changed(value: Float) = updateRanges { it?.copy(n1 = value) }
    fun onRangeN2Changed(value: Float) = updateRanges { it?.copy(n2 = value) }
    fun onRangeN3Changed(value: Float) = updateRanges { it?.copy(n3 = value) }
    fun onRangeOverChanged(value: Float) = updateRanges { it?.copy(over = value) }

    private fun updateRanges(transform: (RangeTable?) -> RangeTable?) {
        _uiState.update { it.copy(ranges = transform(it.ranges), rangesTouchedByUser = true) }
    }

    fun onRangeSizeChanged(value: Float) {
        _uiState.update { it.copy(rangeSize = value, rangesTouchedByUser = true) }
    }

    fun onStableRangeChanged(value: Float) {
        _uiState.update { it.copy(stableRange = value, rangesTouchedByUser = true) }
    }

    fun onTargetRangeContinue() {
        if (_uiState.value.ranges == null) return
        advance(CreateExperimentStep.TARGET_RANGE, forceSlide = true)
    }

    /** Lets the dots indicator jump back to any already-revealed page. */
    fun goToStep(step: Int) {
        val state = _uiState.value
        if (step in 0 until state.revealedSteps) {
            _uiState.update { it.copy(currentStep = step) }
        }
    }

    fun onCreateClicked() {
        submit()
    }

    /** Direct analogue of [ExperimentConfigViewModel.advance]/[CheckinViewModel.advance], but
     *  looking up [stepKind]'s position via [CreateExperimentUiState.steps] instead of its
     *  ordinal, since a step's index can depend on earlier custom-signal selections. */
    private fun advance(stepKind: CreateExperimentStep, forceSlide: Boolean = false) {
        val state = _uiState.value
        val steps = state.steps
        val step = steps.indexOf(stepKind)
        if (step < 0 || step == steps.size - 1) return

        val isFrontier = state.revealedSteps == step + 1
        if (isFrontier) {
            _uiState.update { it.copy(revealedSteps = it.revealedSteps + 1) }
        }

        if (isFrontier || forceSlide) {
            viewModelScope.launch {
                delay(150)
                _uiState.update { it.copy(currentStep = step + 1) }
            }
        }
    }

    private fun submit() {
        val state = _uiState.value
        val type = buildExperimentType(state) ?: return

        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            repository.createCustomType(type)
            eventChannel.send(CreateExperimentEvent.NavigateToIntro(type))
        }
    }
}

private fun buildExperimentType(state: CreateExperimentUiState): ExperimentType? {
    if (state.name.isBlank()) return null
    val typeKey = state.typeKey.takeIf { it.isNotBlank() } ?: return null
    val inputRef = state.input.toSignalRef() ?: return null
    val outputRef = state.output.toSignalRef() ?: return null
    val ranges = state.ranges ?: return null
    val rangeSize = state.rangeSize ?: return null
    val stableRange = state.stableRange ?: return null

    val targetFormatKind = inputRef.defaultFormatKind()
    val resultFormatKind = if (targetFormatKind == FormatKind.TIME_OF_DAY) FormatKind.RAW_NUMBER else targetFormatKind
    val (instructionTemplate, resultTemplate, targetTemplate) = defaultTemplates(inputRef)

    return ExperimentType(
        typeKey = typeKey,
        name = state.name.trim(),
        ranges = ranges,
        rangeSize = rangeSize,
        stableRange = stableRange,
        useVariability = false,
        shouldMinimizeResult = false,
        usesSleepData = isSleepSignal(inputRef) || isSleepSignal(outputRef),
        inputSignal = inputRef,
        outputSignal = outputRef,
        targetFormatKind = targetFormatKind,
        resultFormatKind = resultFormatKind,
        instructionTemplate = instructionTemplate,
        resultTemplate = resultTemplate,
        targetTemplate = targetTemplate
    )
}

/** Slugifies a free-text experiment name into a bare `typeKey` (letters/digits only, matching
 *  every bundled `typeKey`'s convention, e.g. "leisurehappiness") -- falls back to a fixed
 *  string on a name with no alphanumerics at all (e.g. all punctuation/emoji). */
private fun slugify(name: String): String =
    name.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "customexperiment" }

/** Appends the smallest integer suffix that makes [base] unique against [existingKeys] --
 *  issue #33's "uniqueness-checked against ExperimentType.getAllTypes()", resolved
 *  automatically rather than blocking the user with a naming-conflict error page. */
private fun uniqueTypeKey(base: String, existingKeys: Set<String>): String {
    if (base !in existingKeys) return base
    var suffix = 2
    while ("$base$suffix" in existingKeys) suffix++
    return "$base$suffix"
}

/** Which [CustomRangePresets.presetFor] shape best approximates a signal's natural unit, for
 *  deriving the new type's default [RangeTable]. Builtin sleep-start-minute has no great fit
 *  (it's a minute-of-day, not a duration or a plain count) -- it's grouped with COUNT as a
 *  neutral starting point; the wizard's Advanced section is exactly the escape hatch for a
 *  mismatched default like this. */
private fun SignalRef.rangePresetKind(): CustomValueKind = when (this) {
    is SignalRef.Custom -> definition.kind
    is SignalRef.Builtin -> when (source) {
        SignalSource.CHECKIN_LEISURE_TIME,
        SignalSource.HEALTH_CONNECT_SLEEP_DURATION_MINUTES,
        SignalSource.HEALTH_CONNECT_EXERCISE_MINUTES -> CustomValueKind.DURATION_MINUTES

        SignalSource.HEALTH_CONNECT_STEPS,
        SignalSource.HEALTH_CONNECT_SLEEP_EFFICIENCY,
        SignalSource.HEALTH_CONNECT_SLEEP_START_MINUTE -> CustomValueKind.COUNT

        SignalSource.CHECKIN_HAPPINESS,
        SignalSource.CHECKIN_STRESS,
        SignalSource.CHECKIN_PRODUCTIVITY -> CustomValueKind.SCALE_1_7
    }
}

/** Unlike [rangePresetKind] (a rough bucketing-shape guess), this mirrors the bundled config's
 *  per-signal [FormatKind] exactly -- e.g. sleep-start-minute really is [FormatKind.TIME_OF_DAY],
 *  not just "duration-shaped". */
private fun SignalRef.defaultFormatKind(): FormatKind = when (this) {
    is SignalRef.Custom -> when (definition.kind) {
        CustomValueKind.DURATION_MINUTES -> FormatKind.HOURS_FROM_MINUTES
        CustomValueKind.COUNT, CustomValueKind.SCALE_1_7 -> FormatKind.RAW_NUMBER
    }

    is SignalRef.Builtin -> when (source) {
        SignalSource.CHECKIN_LEISURE_TIME,
        SignalSource.HEALTH_CONNECT_SLEEP_DURATION_MINUTES,
        SignalSource.HEALTH_CONNECT_EXERCISE_MINUTES -> FormatKind.HOURS_FROM_MINUTES

        SignalSource.HEALTH_CONNECT_SLEEP_START_MINUTE -> FormatKind.TIME_OF_DAY

        SignalSource.HEALTH_CONNECT_STEPS,
        SignalSource.HEALTH_CONNECT_SLEEP_EFFICIENCY,
        SignalSource.CHECKIN_HAPPINESS,
        SignalSource.CHECKIN_STRESS,
        SignalSource.CHECKIN_PRODUCTIVITY -> FormatKind.RAW_NUMBER
    }
}

private fun isSleepSignal(ref: SignalRef): Boolean =
    ref is SignalRef.Builtin && ref.source in setOf(
        SignalSource.HEALTH_CONNECT_SLEEP_START_MINUTE,
        SignalSource.HEALTH_CONNECT_SLEEP_DURATION_MINUTES,
        SignalSource.HEALTH_CONNECT_SLEEP_EFFICIENCY
    )

private fun unitSuffixFor(ref: SignalRef): String = when (ref) {
    is SignalRef.Custom -> ref.definition.unitLabel?.takeIf { it.isNotBlank() }
        ?: if (ref.definition.kind == CustomValueKind.DURATION_MINUTES) "Hours" else ""

    is SignalRef.Builtin -> when (ref.source) {
        SignalSource.CHECKIN_LEISURE_TIME,
        SignalSource.HEALTH_CONNECT_SLEEP_DURATION_MINUTES,
        SignalSource.HEALTH_CONNECT_EXERCISE_MINUTES -> "Hours"

        SignalSource.HEALTH_CONNECT_STEPS -> "Steps"
        SignalSource.HEALTH_CONNECT_SLEEP_EFFICIENCY -> "%"
        else -> ""
    }
}

private fun shortLabelFor(ref: SignalRef): String = when (ref) {
    is SignalRef.Custom -> ref.definition.label
    is SignalRef.Builtin -> when (ref.source) {
        SignalSource.CHECKIN_LEISURE_TIME -> "leisure time"
        SignalSource.CHECKIN_HAPPINESS -> "happiness"
        SignalSource.CHECKIN_STRESS -> "stress level"
        SignalSource.CHECKIN_PRODUCTIVITY -> "productivity"
        SignalSource.HEALTH_CONNECT_SLEEP_START_MINUTE -> "sleep start time"
        SignalSource.HEALTH_CONNECT_SLEEP_DURATION_MINUTES -> "sleep duration"
        SignalSource.HEALTH_CONNECT_STEPS -> "daily steps"
        SignalSource.HEALTH_CONNECT_SLEEP_EFFICIENCY -> "sleep efficiency"
        SignalSource.HEALTH_CONNECT_EXERCISE_MINUTES -> "exercise minutes"
    }
}

/** Generic, kind-agnostic instruction/result/target copy for a wizard-authored type -- unlike
 *  every bundled type's hand-written, verb-specific templates ("walk", "sleep"), a
 *  user-defined signal has no known verb, so this settles for a neutral "reach"/"of {label}"
 *  phrasing that reads sensibly for any [SignalRef]. */
private fun defaultTemplates(inputRef: SignalRef): Triple<String, String, String> {
    val unit = unitSuffixFor(inputRef)
    val label = shortLabelFor(inputRef)
    val valueWithUnit = if (unit.isBlank()) "{value}" else "{value} $unit"
    return Triple(
        "Try to reach $valueWithUnit of $label today",
        "Try to reach around $valueWithUnit of $label each day",
        valueWithUnit
    )
}
