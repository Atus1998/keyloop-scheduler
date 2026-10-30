-- Flyway callback: runs after every migrate, as the migration user. Default privileges give the
-- app user data rights on every new table (docker/postgres/initdb/01-roles.sh); take them back
-- on the migration history. No role names here: where only one user exists (tests), it does
-- nothing.
DO $$
DECLARE
  history_owner text;
  grantee_name text;
BEGIN
  SELECT tableowner INTO history_owner
  FROM pg_tables
  WHERE schemaname = current_schema() AND tablename = 'flyway_schema_history';

  FOR grantee_name IN
    SELECT DISTINCT grantee
    FROM information_schema.table_privileges
    WHERE table_schema = current_schema()
      AND table_name = 'flyway_schema_history'
      AND grantee <> history_owner
  LOOP
    IF grantee_name = 'PUBLIC' THEN
      REVOKE ALL ON TABLE flyway_schema_history FROM PUBLIC;
    ELSE
      EXECUTE format('REVOKE ALL ON TABLE flyway_schema_history FROM %I', grantee_name);
    END IF;
  END LOOP;
END
$$;
