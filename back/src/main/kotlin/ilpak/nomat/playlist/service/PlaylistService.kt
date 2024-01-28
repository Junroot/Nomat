package ilpak.nomat.playlist.service

import ilpak.nomat.playlist.dto.PlaylistResponse
import org.springframework.stereotype.Service

@Service
class PlaylistService {

	fun getPlaylistMetadata(ids: Set<Long>): List<PlaylistResponse> {
		return ids.map { PlaylistResponse(it, "오늘의 TOP 100: 일본", 100) }
	}
}
