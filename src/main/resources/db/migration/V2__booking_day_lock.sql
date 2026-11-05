-- Bookings for one dealership and local day wait for each other (docs/system-design.md, section 6).
-- The SET clause gives only the wait for this lock a 2 s limit: PostgreSQL restores the
-- connection's lock_timeout (5 s, application.properties) when the function returns.
-- The transaction-level lock itself is held until the calling transaction ends.
-- VOLATILE (the default) on purpose: the call must run every time.
CREATE FUNCTION lock_booking_day(lock_key bigint) RETURNS void
    LANGUAGE sql
    SET lock_timeout = '2s'
AS $$ SELECT pg_catalog.pg_advisory_xact_lock(lock_key) $$;
