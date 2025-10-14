CREATE TABLE IF NOT EXISTS favorite_playlist
(
    player_id     BIGINT      NOT NULL,
    playlist_id   BIGINT      NOT NULL,
    created_date DATETIME NOT NULL,
    PRIMARY KEY (player_id, playlist_id)
) DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_unicode_ci';

CREATE INDEX i_player_id ON favorite_playlist (player_id);
