CREATE TABLE IF NOT EXISTS playlist
(
    id BIGINT NOT NULL PRIMARY KEY,
    master_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    description VARCHAR(600) NOT NULL
) DEFAULT CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci';

CREATE INDEX i_master_id ON playlist (master_id);

CREATE TABLE IF NOT EXISTS track
(
    id BIGINT NOT NULL PRIMARY KEY,
    embed_id VARCHAR(30) NOT NULL,
    title VARCHAR(150) NOT NULL,
    start_time_sec INTEGER NOT NULL,
    end_time_sec INTEGER NOT NULL,
    repeat_count INTEGER NOT NULL,
    playlist_id BIGINT NOT NULL
) DEFAULT CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci';

CREATE INDEX i_playlist_id ON track (playlist_id);

CREATE TABLE IF NOT EXISTS track_sequence
(
    next_val BIGINT NOT NULL
);
INSERT INTO track_sequence (next_val) VALUES (0);

CREATE TABLE IF NOT EXISTS track_additional_title
(
    track_id BIGINT NOT NULL,
    additional_title VARCHAR(150) NOT NULL,
    PRIMARY KEY (track_id, additional_title)
) DEFAULT CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci';

ALTER TABLE room CHANGE COLUMN playlist_count track_count BIGINT NOT NULL;
ALTER TABLE player MODIFY COLUMN id BIGINT NOT NULL;
ALTER TABLE room MODIFY COLUMN id BIGINT NOT NULL;

CREATE TABLE IF NOT EXISTS hibernate_sequences(
    sequence_name VARCHAR(255) NOT NULL PRIMARY KEY,
    next_val BIGINT
);

