CREATE TABLE IF NOT EXISTS playlist
(
    id BIGINT NOT NULL PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    description VARCHAR(600) NOT NULL,
    created_by BIGINT NOT NULL,
    created_date DATETIME NOT NULL,
    modified_by BIGINT NOT NULL,
    modified_date DATETIME NOT NULL
) DEFAULT CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci';

CREATE INDEX i_created_by ON playlist (created_by);

CREATE TABLE IF NOT EXISTS track
(
    id BIGINT NOT NULL PRIMARY KEY,
    embed_id VARCHAR(30) NOT NULL,
    title VARCHAR(150) NOT NULL,
    start_time_sec INTEGER NOT NULL,
    end_time_sec INTEGER NOT NULL,
    repeat_count INTEGER NOT NULL,
    playlist_id BIGINT NOT NULL,
    is_representative BOOLEAN NOT NULL,
    created_by BIGINT NOT NULL,
    created_date DATETIME NOT NULL,
    modified_by BIGINT NOT NULL,
    modified_date DATETIME NOT NULL
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

ALTER TABLE player ADD created_date DATETIME NOT NULL;
ALTER TABLE player ADD modified_date DATETIME NOT NULL;
ALTER TABLE room ADD created_date DATETIME NOT NULL;
ALTER TABLE room ADD created_by BIGINT NOT NULL;
ALTER TABLE room ADD modified_date DATETIME NOT NULL;
ALTER TABLE room ADD modified_by BIGINT NOT NULL;

