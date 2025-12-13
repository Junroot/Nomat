DROP TABLE IF EXISTS room;
DROP TABLE IF EXISTS room_entry;

CREATE TABLE IF NOT EXISTS room
(
    id BIGINT   NOT NULL    AUTO_INCREMENT  PRIMARY KEY,
    title   VARCHAR(50) NOT NULL,
    `password`  VARCHAR(50),
    max_entries_count   INTEGER NOT NULL,
    playlist_id BIGINT  NOT NULL,
    playlist_title  VARCHAR(150) NOT NULL,
    playlist_master_id  BIGINT  NOT NULL,
    playlist_description    VARCHAR(600)   NOT NULL,
    status  CHAR(20)    NOT NULL,
    created_date    DATETIME(3) NOT NULL,
    modified_date   DATETIME(3) NOT NULL,
    created_by  BIGINT  NOT NULL,
    modified_by BIGINT  NOT NULL
) DEFAULT CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci';

CREATE TABLE IF NOT EXISTS room_entry
(
    room_id BIGINT  NOT NULL,
    player_id   BIGINT  NOT NULL,
    join_date DATETIME(3) NOT NULL,
    PRIMARY KEY (room_id, player_id)
) DEFAULT CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci';

CREATE TABLE IF NOT EXISTS room_playlist_track
(
    room_id BIGINT  NOT NULL,
    track_id    BIGINT  NOT NULL,
    embed_id VARCHAR(30) NOT NULL,
    title VARCHAR(150) NOT NULL,
    start_time_sec INTEGER NOT NULL,
    end_time_sec INTEGER NOT NULL,
    repeat_count INTEGER NOT NULL,
    is_representative BOOLEAN NOT NULL,
    PRIMARY KEY (room_id, track_id)
) DEFAULT CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci';

CREATE TABLE IF NOT EXISTS room_track_additional_title
(
    room_id BIGINT NOT NULL,
    track_id BIGINT NOT NULL,
    additional_title VARCHAR(150) NOT NULL,
    PRIMARY KEY (room_id, track_id, additional_title)
) DEFAULT CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci';
