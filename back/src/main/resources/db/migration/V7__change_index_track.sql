DROP INDEX i_playlist_id ON track;
CREATE INDEX i_playlist_id_is_representative ON track (playlist_id, is_representative);
