DROP TABLE room_member;

CREATE TABLE IF NOT EXISTS room_entry
(
    room_id   BIGINT      NOT NULL,
    player_id BIGINT      NOT NULL,
    PRIMARY KEY (room_id, player_id)
) DEFAULT CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci';

ALTER TABLE room DROP COLUMN playlist_master;
ALTER TABLE room ADD playlist_master_id BIGINT NOT NULL;
