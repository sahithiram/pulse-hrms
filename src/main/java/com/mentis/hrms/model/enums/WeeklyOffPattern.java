package com.mentis.hrms.model.enums;

/**
 * Which occurrence(s) of a weekday are weekly offs.
 * ODD  = 1st, 3rd, 5th occurrence of that weekday in the month.
 * EVEN = 2nd, 4th occurrence.
 * FIRST..FIFTH = exact occurrence. LAST = last occurrence (e.g. last Saturday).
 */
public enum WeeklyOffPattern {
    ALL, ODD, EVEN, FIRST, SECOND, THIRD, FOURTH, FIFTH, LAST
}
