package com.mentis.hrms.pm.balance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GAP 19c - the greedy part of workload auto-balancing, with NO Spring / JPA in it.
 *
 * The service decides WHICH tasks may move (open, not started, project not locked, caller
 * allowed) and WHO may receive them (valid participant, not already on the task). This class
 * only decides the order and the numbers:
 *
 *  - sources  = people whose status is OVER, most loaded first
 *  - targets  = people whose status is UNDER or OK, not on leave, capacity > 0
 *  - for each source, biggest task first, give it to the least-utilised eligible target
 *  - a target may never go above capFraction (0.85) of its capacity after the move
 *  - stop moving from a source once it is at or below stopFraction (1.00) of capacity
 *  - never more than maxMoves moves in total
 *
 * Utilisation is tracked as hours so each Move's before/after figures are cumulative and
 * consistent: if Priya receives two tasks, the second row starts where the first one ended.
 */
public final class WorkloadBalancePlanner {

    private WorkloadBalancePlanner() { }

    /** status is the value WorkloadServiceImpl already computes: OVER, WARN, OK or UNDER. */
    public record Person(Long pk, String name, double capacityHours, double allocatedHours,
                         boolean onLeave, String status) { }

    /** One movable task, already filtered by the service. hours = estimate, or storyPoints * 4. */
    public record Item(Long taskId, Long ownerPk, double hours) { }

    public record Move(Item item, Long fromPk, Long toPk,
                       double fromBeforePct, double fromAfterPct,
                       double toBeforePct, double toAfterPct) { }

    public record Result(List<Move> moves, Map<Long, Double> finalAllocated, boolean capped) { }

    /** Per-task, per-candidate rule supplied by the service (participant, not already assigned...). */
    @FunctionalInterface
    public interface Eligibility {
        boolean canTake(Item item, Long toPk);
    }

    public static Result plan(List<Person> people, List<Item> items, Eligibility eligible,
                              double capFraction, double stopFraction, int maxMoves) {

        Map<Long, Person> byPk = new LinkedHashMap<>();
        Map<Long, Double> alloc = new HashMap<>();
        for (Person p : people) {
            byPk.put(p.pk(), p);
            alloc.put(p.pk(), p.allocatedHours());
        }

        List<Person> sources = new ArrayList<>();
        List<Person> targets = new ArrayList<>();
        for (Person p : people) {
            if ("OVER".equals(p.status())) {
                sources.add(p);
            } else if (!p.onLeave() && p.capacityHours() > 0
                    && ("UNDER".equals(p.status()) || "OK".equals(p.status()))) {
                targets.add(p);
            }
        }
        sources.sort(Comparator.comparingDouble((Person p) -> util(p.allocatedHours(), p.capacityHours())).reversed());

        List<Move> moves = new ArrayList<>();
        boolean capped = false;

        outer:
        for (Person src : sources) {
            List<Item> mine = new ArrayList<>();
            for (Item it : items) if (src.pk().equals(it.ownerPk())) mine.add(it);
            // biggest first moves the most per reassignment; taskId keeps the order deterministic
            mine.sort(Comparator.comparingDouble(Item::hours).reversed().thenComparing(Item::taskId));

            for (Item it : mine) {
                if (alloc.get(src.pk()) <= src.capacityHours() * stopFraction + 1e-9) break; // no longer OVER
                if (it.hours() <= 0) continue;
                if (moves.size() >= maxMoves) { capped = true; break outer; }

                Person best = null;
                double bestUtil = Double.MAX_VALUE;
                for (Person t : targets) {
                    if (t.pk().equals(src.pk())) continue;
                    double after = alloc.get(t.pk()) + it.hours();
                    if (after > t.capacityHours() * capFraction + 1e-9) continue;   // 85% cap
                    if (!eligible.canTake(it, t.pk())) continue;
                    double u = util(alloc.get(t.pk()), t.capacityHours());
                    if (u < bestUtil) { bestUtil = u; best = t; }
                }
                if (best == null) continue;

                double fromBefore = util(alloc.get(src.pk()), src.capacityHours());
                double toBefore = util(alloc.get(best.pk()), best.capacityHours());
                alloc.put(src.pk(), alloc.get(src.pk()) - it.hours());
                alloc.put(best.pk(), alloc.get(best.pk()) + it.hours());
                moves.add(new Move(it, src.pk(), best.pk(),
                        fromBefore, util(alloc.get(src.pk()), src.capacityHours()),
                        toBefore, util(alloc.get(best.pk()), best.capacityHours())));
            }
        }
        return new Result(moves, alloc, capped);
    }

    /** utilisation in percent (not rounded); 0 when the person has no capacity. */
    public static double util(double allocated, double capacity) {
        return capacity <= 0 ? 0 : allocated * 100.0 / capacity;
    }
}