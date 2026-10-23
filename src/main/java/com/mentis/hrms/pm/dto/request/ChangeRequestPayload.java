package com.mentis.hrms.pm.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Parsed + validated payload of a CHANGE_REQUEST approval. */
@Getter
public class ChangeRequestPayload {

    private final LocalDate newEndDate;   // optional
    private final BigDecimal newBudget;   // optional
    private final String reason;          // required

    private ChangeRequestPayload(LocalDate newEndDate, BigDecimal newBudget, String reason) {
        this.newEndDate = newEndDate;
        this.newBudget = newBudget;
        this.reason = reason;
    }

    /** Shape rules only (no project lookup). */
    public static ChangeRequestPayload from(JsonNode n) {
        if (n == null || !n.isObject())
            throw new BusinessRuleException("payload is required for a CHANGE_REQUEST");

        String reason = n.hasNonNull("reason") ? n.get("reason").asText().trim() : "";
        if (reason.isEmpty())
            throw new BusinessRuleException("payload.reason is required for a CHANGE_REQUEST");
        if (reason.length() > 1000)
            throw new BusinessRuleException("payload.reason is too long (max 1000 characters)");

        LocalDate end = null;
        if (n.hasNonNull("newEndDate")) {
            try {
                end = LocalDate.parse(n.get("newEndDate").asText());
            } catch (DateTimeParseException e) {
                throw new BusinessRuleException("payload.newEndDate must be a date in the form yyyy-MM-dd");
            }
        }

        BigDecimal budget = null;
        if (n.hasNonNull("newBudget")) {
            try {
                budget = new BigDecimal(n.get("newBudget").asText());
            } catch (NumberFormatException e) {
                throw new BusinessRuleException("payload.newBudget must be a number");
            }
            if (budget.signum() < 0)
                throw new BusinessRuleException("Budget cannot be negative");
        }

        if (end == null && budget == null)
            throw new BusinessRuleException("A change request must set newEndDate, newBudget, or both");

        return new ChangeRequestPayload(end, budget, reason);
    }

    /** One-line summary used in notifications and the activity log. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (newEndDate != null) sb.append("new end date ").append(newEndDate);
        if (newBudget != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("new budget ").append(newBudget.toPlainString());
        }
        return sb.append(" — ").append(reason).toString();
    }
}