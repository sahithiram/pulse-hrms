
package com.mentis.hrms.scheduler;

/**
 * PM due-date scheduling is intentionally disabled in this deployment.
 *
 * The unfinished PM module is excluded from Git and must not be required
 * by the core HRMS application.
 *
 * Task, milestone, and sprint notifications can be restored when the PM
 * module is ready to be included in a deployment.
 *
 * This class is intentionally not a Spring component and does not define
 * scheduled jobs.
 */
public final class DueDateNotificationScheduler {

    private DueDateNotificationScheduler() {
        // Prevent instantiation.
    }
}
