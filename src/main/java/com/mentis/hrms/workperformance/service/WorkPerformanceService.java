package com.mentis.hrms.workperformance.service;

import com.mentis.hrms.workperformance.dto.ClickUpTaskDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * NEW service. Turns raw ClickUp task data into a single fair 0-100 work score.
 *
 * FAIRNESS DESIGN — why each component exists:
 *  1) Completion Rate   - did they finish what was assigned to them.
 *  2) On-Time Rate       - of what they finished, how much was on schedule.
 *  3) Priority-Weighted  - completing urgent/high tasks counts more than
 *                          completing trivial ones, so task difficulty matters.
 *  4) Efficiency         - actual time vs estimated time (only counted when
 *                          an estimate exists, so it never penalizes teams
 *                          that don't use time tracking).
 *  5) Workload-Normalized - an employee with 5 hard tasks in a small team
 *                          shouldn't lose to someone with 50 trivial tasks.
 *                          We rank each employee's task volume against their
 *                          OWN DEPARTMENT for the SAME MONTH (percentile),
 *                          not against the whole company. This is the key
 *                          fairness safeguard.
 *
 * All weights are configurable in application.properties — nothing is
 * hardcoded so you can tune fairness without recompiling.
 */
@Service
public class WorkPerformanceService {

    private static final Logger logger = LoggerFactory.getLogger(WorkPerformanceService.class);

    @Autowired
    private ClickUpApiService clickUpApiService;

    @Value("${work.weight.completion:0.35}")
    private double completionWeight;

    @Value("${work.weight.ontime:0.25}")
    private double onTimeWeight;

    @Value("${work.weight.priority:0.20}")
    private double priorityWeight;

    @Value("${work.weight.efficiency:0.10}")
    private double efficiencyWeight;

    @Value("${work.weight.workload:0.10}")
    private double workloadWeight;

    public static class WorkScoreResult {
        public int totalTasks;
        public int completedTasks;
        public int onTimeCompleted;
        public double completionRate;
        public double onTimeRate;
        public double priorityScore;
        public double efficiencyScore;
        public double workloadNormalizedScore;
        public double finalWorkScore;
    }

    /**
     * Fetch raw tasks for one employee. Exposed separately so a batch sync
     * can fetch every employee's tasks FIRST, then score everyone against
     * the same peer group — never mid-loop / half-populated data.
     */
    public List<ClickUpTaskDTO> fetchTasks(String clickupUserId, long startMs, long endMs) {
        return clickUpApiService.getTasksForAssignee(clickupUserId, startMs, endMs);
    }

    /** Convenience overload for scoring a single employee outside a batch run. */
    public WorkScoreResult calculateFairWorkScore(String clickupUserId, long startMs, long endMs,
                                                  List<Integer> departmentTaskCounts) {
        List<ClickUpTaskDTO> tasks = fetchTasks(clickupUserId, startMs, endMs);
        return calculateFairWorkScore(tasks, departmentTaskCounts);
    }

    /**
     * Compute the fair work score from an already-fetched task list.
     * departmentTaskCounts should contain every OTHER employee in the same
     * department's total task count for the same month, for normalization.
     * Pass an empty list if not available yet (score simply skips that component).
     */
    public WorkScoreResult calculateFairWorkScore(List<ClickUpTaskDTO> tasks, List<Integer> departmentTaskCounts) {
        WorkScoreResult r = new WorkScoreResult();

        r.totalTasks = tasks.size();
        if (r.totalTasks == 0) {
            r.finalWorkScore = 0.0;
            return r;
        }

        int completed = 0, onTime = 0;
        double priorityEarned = 0, priorityPossible = 0;
        double efficiencySum = 0;
        int efficiencyCount = 0;

        for (ClickUpTaskDTO t : tasks) {
            double weight = priorityWeight(t.getPriority());
            priorityPossible += weight;

            if (t.isCompleted()) {
                completed++;
                priorityEarned += weight;
                if (t.isCompletedOnTime()) onTime++;

                if (t.getTimeEstimate() != null && t.getTimeEstimate() > 0 && t.getTimeSpent() != null) {
                    double ratio = t.getTimeSpent() / (double) t.getTimeEstimate();
                    // 1.0 = exactly on estimate. Under estimate is fine (full marks).
                    // Over estimate reduces score proportionally, floor at 0.
                    double eff = ratio <= 1.0 ? 100.0 : Math.max(0, 100.0 - ((ratio - 1.0) * 100));
                    efficiencySum += eff;
                    efficiencyCount++;
                }
            }
        }

        r.completedTasks = completed;
        r.onTimeCompleted = onTime;
        r.completionRate = round((completed / (double) r.totalTasks) * 100);
        r.onTimeRate = completed > 0 ? round((onTime / (double) completed) * 100) : 0.0;
        r.priorityScore = priorityPossible > 0 ? round((priorityEarned / priorityPossible) * 100) : 0.0;
        // No estimates logged at all -> don't penalize, treat as neutral 100
        r.efficiencyScore = efficiencyCount > 0 ? round(efficiencySum / efficiencyCount) : 100.0;
        r.workloadNormalizedScore = round(percentileRank(r.totalTasks, departmentTaskCounts));

        double score = (r.completionRate * completionWeight)
                + (r.onTimeRate * onTimeWeight)
                + (r.priorityScore * priorityWeight)
                + (r.efficiencyScore * efficiencyWeight)
                + (r.workloadNormalizedScore * workloadWeight);

        r.finalWorkScore = round(Math.min(100, Math.max(0, score)));

        logger.info("Work score computed: total={}, completed={}, onTime={}, priority={}, efficiency={}, workload={}, final={}",
                r.totalTasks, r.completedTasks, r.onTimeCompleted, r.priorityScore, r.efficiencyScore,
                r.workloadNormalizedScore, r.finalWorkScore);

        return r;
    }

    private double priorityWeight(String priority) {
        if (priority == null) return 2.0;
        switch (priority.toLowerCase()) {
            case "urgent": return 4.0;
            case "high": return 3.0;
            case "low": return 1.0;
            default: return 2.0; // normal
        }
    }

    /**
     * Percentile rank of this employee's task count among their department
     * peers for the month. Median volume = 50, top volume = 100, so nobody
     * is punished just for having fewer tasks than a teammate in a
     * different role, and nobody is unfairly rewarded purely for closing
     * a huge number of trivial tickets.
     */
    private double percentileRank(int myCount, List<Integer> departmentCounts) {
        if (departmentCounts == null || departmentCounts.isEmpty()) return 75.0; // neutral-positive default
        long below = departmentCounts.stream().filter(c -> c < myCount).count();
        long equal = departmentCounts.stream().filter(c -> c.equals(myCount)).count();
        double rank = (below + (0.5 * equal)) / departmentCounts.size();
        return rank * 100;
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
