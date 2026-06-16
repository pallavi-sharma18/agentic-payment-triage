INSERT INTO payment (payment_id, amount, payment_status, failure_reason, payment_time) VALUES
-- original scenarios
('pay_1001', 150.00, 'FAILED',    'insufficient_funds', NOW() - INTERVAL '2 hours'),
('pay_1003', 250.00, 'PENDING',   NULL,                 NOW() - INTERVAL '3 hours'),
('pay_1004',  45.50, 'CONFIRMED', NULL,                 NOW() - INTERVAL '1 day'),
('pay_1005', 500.00, 'FAILED',    'expired_card',       NOW() - INTERVAL '30 minutes'),

-- the payment we triage: a GENERIC decline → first diagnosis is ambiguous → loop fires.
-- RECENT, so it sits inside the failure cluster.
('pay_1002',  89.99, 'FAILED',    'do_not_honor',       NOW() - INTERVAL '8 minutes'),

-- a BURST of recent failures (all within the last hour) → evidence of a systemic issue,
-- which should make the re-diagnosis reclassify pay_1002 as TECHNICAL.
('pay_2001', 120.00, 'FAILED',    'do_not_honor',       NOW() - INTERVAL '5 minutes'),
('pay_2002',  75.25, 'FAILED',    'do_not_honor',       NOW() - INTERVAL '6 minutes'),
('pay_2003', 310.00, 'FAILED',    'do_not_honor',       NOW() - INTERVAL '9 minutes'),
('pay_2004',  60.00, 'FAILED',    'do_not_honor',       NOW() - INTERVAL '12 minutes');







-- INSERT INTO payment (payment_id, amount, payment_status, failure_reason, payment_time) VALUES
-- -- hard failure: insufficient funds (retryable, with limits)
-- ('pay_1001', 150.00, 'FAILED', 'insufficient_funds', NOW() - INTERVAL '2 hours'),
-- -- hard failure: do not honor / generic issuer decline (retry discouraged)
-- ('pay_1002',  89.99, 'FAILED', 'do_not_honor',       NOW() - INTERVAL '1 hour'),
-- -- stuck: still PENDING and old → should be flagged stuck=true
-- ('pay_1003', 250.00, 'PENDING', NULL,                NOW() - INTERVAL '3 hours'),
-- -- healthy: CONFIRMED → pipeline should short-circuit
-- ('pay_1004',  45.50, 'CONFIRMED', NULL,              NOW() - INTERVAL '1 day'),
-- -- hard failure: expired card (not retryable until card updated)
-- ('pay_1005', 500.00, 'FAILED', 'expired_card',       NOW() - INTERVAL '30 minutes');