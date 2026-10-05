CREATE TABLE IF NOT EXISTS matching_configuration (
    config_id INTEGER PRIMARY KEY CHECK (config_id = 1),
    strategy_name VARCHAR(40)
);

INSERT INTO matching_configuration(config_id, strategy_name)
VALUES (1, 'BALANCED')
ON CONFLICT (config_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS matching_criteria (
    criterion_type VARCHAR(40) PRIMARY KEY,
    weight DOUBLE PRECISION NOT NULL CHECK (weight >= 0 AND weight <= 100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO matching_criteria(criterion_type, weight, enabled) VALUES
    ('COURSE', 20, TRUE),
    ('AVAILABILITY', 20, TRUE),
    ('STUDY_MODE', 20, TRUE),
    ('STUDY_GOAL', 20, TRUE),
    ('GROUP_SIZE', 20, TRUE)
ON CONFLICT (criterion_type) DO NOTHING;
