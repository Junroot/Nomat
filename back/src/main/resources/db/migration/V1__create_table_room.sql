CREATE TABLE IF NOT EXISTS player
(
    id       BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nickname VARCHAR(30) NOT NULL
) DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_unicode_ci';

CREATE TABLE IF NOT EXISTS room
(
    id             BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(30) NOT NULL,
    `password`     VARCHAR(30),
    playlist_id    BIGINT      NOT NULL,
    playlist_name  VARCHAR(30) NOT NULL,
    playlist_count INTEGER     NOT NULL
) DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_unicode_ci';

CREATE TABLE IF NOT EXISTS room_member
(
    room_id   BIGINT      NOT NULL,
    player_id BIGINT      NOT NULL,
    nickname  VARCHAR(30) NOT NULL,
    PRIMARY KEY (room_id, player_id, nickname)
) DEFAULT CHARACTER SET 'utf8mb4'
  COLLATE 'utf8mb4_unicode_ci';
