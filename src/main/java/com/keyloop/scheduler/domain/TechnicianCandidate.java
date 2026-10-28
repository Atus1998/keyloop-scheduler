package com.keyloop.scheduler.domain;

/** A free, qualified technician and the number of CONFIRMED appointments they have that day. */
public record TechnicianCandidate(long technicianId, long confirmedAppointmentsThatDay) {}
