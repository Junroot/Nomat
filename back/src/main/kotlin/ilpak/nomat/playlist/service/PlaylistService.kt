package ilpak.nomat.playlist.service

import ilpak.nomat.playlist.dto.PlaylistMetaDataResponse
import org.springframework.stereotype.Service

@Service
class PlaylistService {

    fun getMetadata(id: Long): PlaylistMetaDataResponse {
        return PlaylistMetaDataResponse(
            id,
            "오늘의 TOP 100: 일본",
            100,
            "ROOT#3465",
            "오늘의 일본 인기곡 Top 100으로 구성된 맵입니다. 재미있게 즐겨 주세요!"
        )
    }
}
