package ilpak.nomat.playlist.service

import ilpak.nomat.playlist.dto.PlaylistMetaDataResponse
import org.springframework.stereotype.Service

@Service
class PlaylistService {

    fun getMetadata(id: Long): PlaylistMetaDataResponse {
        return PlaylistMetaDataResponse(
            1L,
            "오늘의 TOP 100: 일본",
            100
        )
    }
}
