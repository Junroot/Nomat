ALTER TABLE player ADD registration_type VARCHAR(20) NOT NULL;
ALTER TABLE player ADD registration_id VARCHAR(255) NOT NULL;

CREATE INDEX registration_type_registration_id ON player (registration_type, registration_id);
