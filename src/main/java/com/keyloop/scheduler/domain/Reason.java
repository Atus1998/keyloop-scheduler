package com.keyloop.scheduler.domain;

/**
 * Every reason code the API returns, in the order of {@code docs/system-design.md}. The web adapter
 * maps each code to an HTTP status. Rule priority lives in {@link BookingRules}, not in {@link
 * #ordinal()}.
 */
public enum Reason {
  INVALID_REQUEST,
  DEALERSHIP_NOT_FOUND,
  VEHICLE_NOT_FOUND,
  SERVICE_TYPE_NOT_FOUND,
  APPOINTMENT_NOT_FOUND,
  START_NOT_WHOLE_MINUTE,
  START_IN_PAST,
  START_TOO_FAR_AHEAD,
  OUTSIDE_OPENING_HOURS,
  SERVICE_NOT_OFFERED,
  NO_TECHNICIAN,
  NO_BAY,
  BUSY,
  VEHICLE_BUSY,
  TECHNICIAN_TAKEN,
  BAY_TAKEN,
  SLOT_TAKEN,
  BOOKING_QUEUE_TIMEOUT
}
