package edu.mit.media.mysnapshot.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Faithful, pure-function port of the adaptive stage/target/confidence algorithm
 * currently living server-side in the Django backend
 * ({@code AffectiveComputingQuantifyMeDjango/src/app/models.py} class {@code Experiment}
 * and {@code analysis.py}). No Android, Room, or network dependency.
 * <p>
 * This class exists ONLY as the oracle for {@link ExperimentEngineCharacterizationTest}
 * (Phase 0 of AGENT_PLANS/MODERNIZE.md). It intentionally mirrors the Python method
 * names/signatures rather than being idiomatic Java, so a reviewer can diff it against
 * models.py line-by-line. Phase 2 replaces this with the real Kotlin engine wired to
 * Room; that engine must keep passing the same characterization tests.
 */
public class ExperimentEngineReference {

    public static final int NUM_STAGES = 3;

    private ExperimentEngineReference() {
    }

    public static double mean(List<Double> values) {
        double sum = 0;
        int count = 0;
        for (Double v : values) {
            if (v != null) {
                sum += v;
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    /**
     * Port of {@code Experiment.set_stage_targets}. Returns the 4-entry stage target
     * list (index 0 is the just-finished stage 0/baseline, unused by callers) plus the
     * plain mean of the inputs (stored as {@code initial_stage_average} regardless of
     * whether this is a variability study).
     */
    public static StageTargets setStageTargets(List<Double> initialStageInputsRaw,
                                                boolean useVariability,
                                                Map<String, Double> ranges,
                                                double rangeSize) {
        List<Double> initialStageInputs = new ArrayList<>();
        for (Double v : initialStageInputsRaw) {
            if (v != null) {
                initialStageInputs.add(v);
            }
        }

        double average = mean(initialStageInputs);
        double min = initialStageInputs.get(0);
        double max = initialStageInputs.get(0);
        for (double v : initialStageInputs) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double variability = max - min;

        double targetValue = useVariability ? variability : average;

        String[] targetKeys;
        if (targetValue <= ranges.get("under")) {
            targetKeys = new String[]{"under", "N1", "N3", "N2"};
        } else if (targetValue <= ranges.get("N1") + rangeSize) {
            targetKeys = new String[]{"N1", "N3", "N1", "N2"};
        } else if (targetValue <= ranges.get("N2") + rangeSize) {
            targetKeys = new String[]{"N2", "N3", "N1", "N2"};
        } else if (targetValue <= ranges.get("N3") + rangeSize) {
            targetKeys = new String[]{"N3", "N1", "N3", "N2"};
        } else {
            targetKeys = new String[]{"over", "N3", "N1", "N2"};
        }

        double[] values = new double[targetKeys.length];
        for (int i = 0; i < targetKeys.length; i++) {
            values[i] = ranges.get(targetKeys[i]);
        }

        return new StageTargets(values, average);
    }

    public static class StageTargets {
        public final double[] stageTargetValues;
        public final double initialStageAverage;

        public StageTargets(double[] stageTargetValues, double initialStageAverage) {
            this.stageTargetValues = stageTargetValues;
            this.initialStageAverage = initialStageAverage;
        }
    }

    /**
     * Port of {@code Experiment.get_daily_target}. {@code stageTarget} may be {@code null}
     * (no target set for this stage yet).
     */
    public static Double getDailyTarget(boolean useVariability,
                                         Double stageTarget,
                                         double initialStageAverage,
                                         int dayInStage) {
        if (!useVariability || stageTarget == null) {
            return stageTarget;
        }
        if (dayInStage % 2 != 0) {
            return initialStageAverage + stageTarget;
        } else {
            return initialStageAverage - stageTarget;
        }
    }

    /**
     * Port of {@code Experiment.is_output_stable}. {@code outputs} may contain nulls
     * (missed check-ins); only the last 5 non-null values are considered.
     */
    public static boolean isOutputStable(int currentStage, List<Double> outputs, double stableRange) {
        if (currentStage == 0) {
            return false;
        }
        List<Double> relevant = new ArrayList<>();
        for (Double v : outputs) {
            if (v != null) {
                relevant.add(v);
            }
        }
        if (relevant.size() > 5) {
            relevant = relevant.subList(relevant.size() - 5, relevant.size());
        }
        if (relevant.isEmpty()) {
            return false;
        }
        double min = relevant.get(0), max = relevant.get(0);
        for (double v : relevant) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return (max - min) <= stableRange;
    }

    public static class Pair {
        public final double input, output;

        public Pair(double input, double output) {
            this.input = input;
            this.output = output;
        }
    }

    /** Port of {@code Experiment.get_valid_days}. */
    public static List<Pair> getValidDays(List<Double> inputs, List<Double> outputs, Double target, double rangeSize) {
        List<Pair> result = new ArrayList<>();
        int n = Math.min(inputs.size(), outputs.size());
        for (int i = 0; i < n; i++) {
            Double in = inputs.get(i);
            Double out = outputs.get(i);
            if (in == null || out == null) {
                continue;
            }
            if (target == null || (target - rangeSize <= in && in <= target + rangeSize)) {
                result.add(new Pair(in, out));
            }
        }
        return result;
    }

    /** Port of {@code Experiment.get_num_missed_days}. */
    public static int getNumMissedDays(List<Double> inputs, List<Double> outputs) {
        int n = Math.min(inputs.size(), outputs.size());
        int missed = 0;
        for (int i = 0; i < n; i++) {
            if (inputs.get(i) == null || outputs.get(i) == null) {
                missed++;
            }
        }
        return missed;
    }

    public static class StageEndDecision {
        public final boolean shouldEnd, endedEarly, restartedStage;

        public StageEndDecision(boolean shouldEnd, boolean endedEarly, boolean restartedStage) {
            this.shouldEnd = shouldEnd;
            this.endedEarly = endedEarly;
            this.restartedStage = restartedStage;
        }
    }

    /**
     * Port of {@code Experiment.should_end_stage}. {@code stageDay} is the pre-computed
     * {@code (today - stage_start).days} value from the Python version.
     */
    public static StageEndDecision shouldEndStage(int currentStage,
                                                   int stageDay,
                                                   int missedDays,
                                                   int validDaysCount,
                                                   boolean isOutputStable) {
        if ((currentStage > 0 && missedDays >= 2) || (currentStage == 0 && missedDays > 2)) {
            return new StageEndDecision(false, true, true);
        }

        if (currentStage > 0) {
            if (validDaysCount >= 5 && isOutputStable) {
                return new StageEndDecision(true, true, false);
            }

            if (stageDay >= 4) {
                int daysLeft = 7 - stageDay;
                int possibleValidDays = validDaysCount + daysLeft;
                if (possibleValidDays < 4) {
                    return new StageEndDecision(false, true, true);
                }
            }
        }

        if (stageDay == 7) {
            return new StageEndDecision(true, false, false);
        }

        return new StageEndDecision(false, false, false);
    }

    public static class StageResult {
        public final int stage;
        public final double target;
        public final double meanOutput, minOutput, maxOutput;
        public final List<Double> outputs;

        public StageResult(int stage, double target, double meanOutput, double minOutput, double maxOutput, List<Double> outputs) {
            this.stage = stage;
            this.target = target;
            this.meanOutput = meanOutput;
            this.minOutput = minOutput;
            this.maxOutput = maxOutput;
            this.outputs = outputs;
        }
    }

    public static class Results {
        public final double resultValue;
        public final double resultConfidence;
        public final int bestStage;
        public final Map<Integer, StageResult> stageResults;

        public Results(double resultValue, double resultConfidence, int bestStage, Map<Integer, StageResult> stageResults) {
            this.resultValue = resultValue;
            this.resultConfidence = resultConfidence;
            this.bestStage = bestStage;
            this.stageResults = stageResults;
        }
    }

    /**
     * Port of {@code Experiment.calculate_results}. {@code targets} and
     * {@code validDaysByStage} are keyed by stage number (1..NUM_STAGES); stage 0
     * (baseline) is excluded, matching the Python version.
     */
    public static Results calculateResults(boolean wantMinimizedResults,
                                            Map<Integer, Double> targets,
                                            Map<Integer, List<Pair>> validDaysByStage) {
        java.util.HashMap<Integer, StageResult> stageResults = new java.util.HashMap<>();
        int bestStage = 0;
        double bestOutput = wantMinimizedResults ? 500000 : -500000;

        for (int stage = 1; stage <= NUM_STAGES; stage++) {
            List<Pair> validDays = validDaysByStage.get(stage);
            List<Double> outputs = new ArrayList<>();
            for (Pair p : validDays) {
                outputs.add(p.output);
            }
            double target = targets.get(stage);
            double sum = 0;
            double min = outputs.get(0), max = outputs.get(0);
            for (double o : outputs) {
                sum += o;
                min = Math.min(min, o);
                max = Math.max(max, o);
            }
            double meanOutput = sum / outputs.size();

            if ((meanOutput > bestOutput && !wantMinimizedResults) || (wantMinimizedResults && meanOutput < bestOutput)) {
                bestOutput = meanOutput;
                bestStage = stage;
            }

            stageResults.put(stage, new StageResult(stage, target, meanOutput, min, max, outputs));
        }

        double maxOverlap = wantMinimizedResults ? 50000 : -50000;
        for (int stage = 1; stage <= NUM_STAGES; stage++) {
            if (stage == bestStage) {
                continue;
            }
            StageResult sr = stageResults.get(stage);
            StageResult best = stageResults.get(bestStage);
            if (wantMinimizedResults) {
                int count = 0;
                for (double v : sr.outputs) {
                    if (v <= best.maxOutput) {
                        count++;
                    }
                }
                double overlap = (double) count / sr.outputs.size();
                maxOverlap = Math.min(maxOverlap, overlap);
            } else {
                int count = 0;
                for (double v : sr.outputs) {
                    if (v >= best.minOutput) {
                        count++;
                    }
                }
                double overlap = (double) count / sr.outputs.size();
                maxOverlap = Math.max(maxOverlap, overlap);
            }
        }

        double confidence = 1.0 - maxOverlap;
        confidence = Math.round(confidence * 100.0) / 100.0;

        double resultValue = stageResults.get(bestStage).target;
        double resultConfidence = Math.min(confidence, 0.9);

        return new Results(resultValue, resultConfidence, bestStage, stageResults);
    }
}
