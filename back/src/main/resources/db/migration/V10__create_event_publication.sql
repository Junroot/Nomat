CREATE TABLE IF NOT EXISTS event_publication
(
    id               BINARY(16)    NOT NULL,
    listener_id      VARCHAR(512)  NOT NULL,
    event_type       VARCHAR(512)  NOT NULL,
    serialized_event VARCHAR(4000) NOT NULL,
    publication_date TIMESTAMP(6)  NOT NULL,
    completion_date  TIMESTAMP(6)  NULL DEFAULT NULL,
    PRIMARY KEY (id)
) DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_unicode_ci';

CREATE INDEX i_event_publication_completion_date
    ON event_publication (completion_date);

CREATE INDEX i_event_publication_listener_serialized
    ON event_publication (listener_id, serialized_event(255));
