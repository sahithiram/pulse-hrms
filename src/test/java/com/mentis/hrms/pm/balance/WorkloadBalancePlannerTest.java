package com.mentis.hrms.pm.balance;

import com.mentis.hrms.pm.balance.WorkloadBalancePlanner.Item;
import com.mentis.hrms.pm.balance.WorkloadBalancePlanner.Person;
import com.mentis.hrms.pm.balance.WorkloadBalancePlanner.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadBalancePlannerTest {

    private static final WorkloadBalancePlanner.Eligibility ANYONE = (item, to) -> true;

    // capacity 40h each
    private static Person p(long pk, String status, double allocated, boolean leave) {
        return new Person(pk, "P" + pk, 40, allocated, leave, status);
    }

    @Test
    void movesFromOverToLeastUtilisedAndNeverExceeds85() {
        // A: 56h (140%), B: 8h (20% UNDER), C: 20h (50% OK)
        List<Person> people = List.of(p(1, "OVER", 56, false), p(2, "UNDER", 8, false), p(3, "OK", 20, false));
        List<Item> items = List.of(new Item(10L, 1L, 8), new Item(11L, 1L, 8), new Item(12L, 1L, 8),
                new Item(13L, 1L, 8));

        Result r = WorkloadBalancePlanner.plan(people, items, ANYONE, 0.85, 1.0, 20);

        assertFalse(r.moves().isEmpty());
        // first move goes to B, the least utilised
        assertEquals(2L, r.moves().get(0).toPk());
        // nobody who received work ends above 85% of 40h = 34h
        assertTrue(r.finalAllocated().get(2L) <= 34.0 + 1e-9);
        assertTrue(r.finalAllocated().get(3L) <= 34.0 + 1e-9);
        // source stops once it is back to 100% (40h)
        assertTrue(r.finalAllocated().get(1L) <= 40.0 + 1e-9);
    }

    @Test
    void beforeAfterAreCumulativeForTheSameReceiver() {
        List<Person> people = List.of(p(1, "OVER", 56, false), p(2, "UNDER", 0, false));
        List<Item> items = List.of(new Item(10L, 1L, 8), new Item(11L, 1L, 8));
        Result r = WorkloadBalancePlanner.plan(people, items, ANYONE, 0.85, 1.0, 20);
        assertEquals(2, r.moves().size());
        assertEquals(r.moves().get(0).toAfterPct(), r.moves().get(1).toBeforePct(), 1e-9);
    }

    @Test
    void personOnLeaveNeverReceives() {
        List<Person> people = List.of(p(1, "OVER", 56, false), p(2, "UNDER", 4, true));
        Result r = WorkloadBalancePlanner.plan(people, List.of(new Item(10L, 1L, 8)), ANYONE, 0.85, 1.0, 20);
        assertTrue(r.moves().isEmpty());
    }

    @Test
    void warnAndOverPeopleNeverReceive() {
        List<Person> people = List.of(p(1, "OVER", 56, false), p(2, "WARN", 36, false), p(3, "OVER", 50, false));
        Result r = WorkloadBalancePlanner.plan(people, List.of(new Item(10L, 1L, 8)), ANYONE, 0.85, 1.0, 20);
        assertTrue(r.moves().isEmpty());
    }

    @Test
    void moveCapIsRespectedAndFlagged() {
        List<Person> people = List.of(p(1, "OVER", 80, false), p(2, "UNDER", 0, false), p(3, "UNDER", 0, false));
        List<Item> items = List.of(new Item(1L, 1L, 4), new Item(2L, 1L, 4), new Item(3L, 1L, 4), new Item(4L, 1L, 4));
        Result r = WorkloadBalancePlanner.plan(people, items, ANYONE, 0.85, 1.0, 2);
        assertEquals(2, r.moves().size());
        assertTrue(r.capped());
    }

    @Test
    void eligibilityRuleIsHonoured() {
        List<Person> people = List.of(p(1, "OVER", 56, false), p(2, "UNDER", 0, false), p(3, "UNDER", 4, false));
        // person 2 is not a valid participant for any task
        Result r = WorkloadBalancePlanner.plan(people, List.of(new Item(10L, 1L, 8)),
                (item, to) -> to != 2L, 0.85, 1.0, 20);
        assertEquals(1, r.moves().size());
        assertEquals(3L, r.moves().get(0).toPk());
    }

    @Test
    void taskTooBigForAnyoneIsSkippedButSmallerOnesStillMove() {
        // 36h task cannot fit under 34h cap for anyone; the 4h task can
        List<Person> people = List.of(p(1, "OVER", 60, false), p(2, "UNDER", 0, false));
        List<Item> items = List.of(new Item(1L, 1L, 36), new Item(2L, 1L, 4));
        Result r = WorkloadBalancePlanner.plan(people, items, ANYONE, 0.85, 1.0, 20);
        assertEquals(1, r.moves().size());
        assertEquals(2L, r.moves().get(0).item().taskId());
    }
}