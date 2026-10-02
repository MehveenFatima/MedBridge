-- The three systems that send MedBridge patient records.
-- INSERT IGNORE makes this safe to run on every startup: existing rows are left alone.
INSERT IGNORE INTO source_system (id, code, name, format, active) VALUES
  (1, 'LAB',      'Central Diagnostics Lab',   'CSV',  true),
  (2, 'CLINIC',   'Dermatology Clinic Portal', 'JSON', true),
  (3, 'HOSPITAL', 'City Hospital ADT Feed',    'HL7',  true);
