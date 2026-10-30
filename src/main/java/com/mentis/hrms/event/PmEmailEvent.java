package com.mentis.hrms.event;

public record PmEmailEvent(String to, String subject, String body) {}