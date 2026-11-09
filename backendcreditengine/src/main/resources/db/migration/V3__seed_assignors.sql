INSERT INTO assignors (id, document, name, created_at)
VALUES
    ('8f14e45f-ceea-467a-9a38-1b4c9f8b0001', '12345678000190', 'Industria Alfa Ltda', '2026-11-04T00:00:00Z'),
    ('9a25f560-dffb-478b-8b49-2c5da09c0002', '98765432000110', 'Comercio Beta S.A.', '2026-11-04T00:00:00Z')
ON CONFLICT (id) DO NOTHING;
