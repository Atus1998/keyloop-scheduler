-- Demo data. Repeatable migration: Flyway runs it again when this file changes,
-- so every statement must be safe to run twice. Names are made up.

INSERT INTO dealership (id, name, zone_id, opens_at, closes_at) VALUES
    (1, 'Saigon Central', 'Asia/Ho_Chi_Minh', '08:00', '17:00'),
    (2, 'London West',    'Europe/London',    '09:00', '18:00')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, zone_id = EXCLUDED.zone_id,
    opens_at = EXCLUDED.opens_at, closes_at = EXCLUDED.closes_at;

INSERT INTO service_bay (id, dealership_id, name) VALUES
    (1, 1, 'Bay 1'), (2, 1, 'Bay 2'), (3, 1, 'Bay 3'),
    (4, 2, 'Bay 1'), (5, 2, 'Bay 2'), (6, 2, 'Bay 3')
ON CONFLICT (id) DO UPDATE SET dealership_id = EXCLUDED.dealership_id, name = EXCLUDED.name;

INSERT INTO technician (id, dealership_id, name) VALUES
    (1, 1, 'An Tran'),     (2, 1, 'Binh Le'),     (3, 1, 'Chi Pham'),
    (4, 2, 'Dan Carter'),  (5, 2, 'Ella Brooks'), (6, 2, 'Finn Walsh')
ON CONFLICT (id) DO UPDATE SET dealership_id = EXCLUDED.dealership_id, name = EXCLUDED.name;

INSERT INTO service_type (id, name, duration_minutes) VALUES
    (1, 'Oil change',     30),
    (2, 'Tyre rotation',  60),
    (3, 'Brake service',  90),
    (4, 'Full service',  180)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, duration_minutes = EXCLUDED.duration_minutes;

-- Each dealership offers every service type.
INSERT INTO technician_skill (technician_id, service_type_id) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4),
    (2, 1), (2, 2),
    (3, 3),
    (4, 1), (4, 3), (4, 4),
    (5, 1), (5, 2),
    (6, 2), (6, 3)
ON CONFLICT DO NOTHING;

INSERT INTO customer (id, name) VALUES
    (1, 'Customer One'),
    (2, 'Customer Two')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO vehicle (id, customer_id, registration) VALUES
    (1, 1, '51A-123.45'),
    (2, 1, '51G-678.90'),
    (3, 2, 'AB12 CDE'),
    (4, 2, 'XY34 ZFG')
ON CONFLICT (id) DO UPDATE SET customer_id = EXCLUDED.customer_id, registration = EXCLUDED.registration;

-- Rows above use fixed ids, so move each identity past them.
SELECT setval(pg_get_serial_sequence('dealership',   'id'), (SELECT max(id) FROM dealership));
SELECT setval(pg_get_serial_sequence('service_bay',  'id'), (SELECT max(id) FROM service_bay));
SELECT setval(pg_get_serial_sequence('technician',   'id'), (SELECT max(id) FROM technician));
SELECT setval(pg_get_serial_sequence('service_type', 'id'), (SELECT max(id) FROM service_type));
SELECT setval(pg_get_serial_sequence('customer',     'id'), (SELECT max(id) FROM customer));
SELECT setval(pg_get_serial_sequence('vehicle',      'id'), (SELECT max(id) FROM vehicle));
