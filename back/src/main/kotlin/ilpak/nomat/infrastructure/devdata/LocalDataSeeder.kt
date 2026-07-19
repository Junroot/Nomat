package ilpak.nomat.infrastructure.devdata

import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.player.application.domain.RegistrationType
import ilpak.nomat.player.application.dto.PlayerRequest
import ilpak.nomat.playlist.application.PlaylistService
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequest
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequestTrack
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * local 프로파일에서만 동작하는 테스트 데이터 시더.
 *
 * local은 부팅할 때마다 Testcontainers로 새 MySQL을 띄우므로 DB가 항상 비어 있다.
 * 방·라운드 엔진을 손으로 확인할 때 쓸 수 있도록 시드 플레이어와 YOASOBI 플레이리스트를 미리 만들어 둔다.
 *
 * 플레이리스트 저장은 반드시 [PlaylistService]를 거친다 — 직접 INSERT하면
 * ES 색인의 유일한 경로인 `PlaylistUpserted` 이벤트가 발행되지 않아 검색에 잡히지 않는다.
 */
@Component
@Profile("local")
private class LocalDataSeeder(
    private val playerService: PlayerService,
    private val playlistService: PlaylistService,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val existingPlayer = playerService.findByRegistrationTypeAndRegistrationId(
            RegistrationType.DISCORD,
            SEED_REGISTRATION_ID,
        )
        if (existingPlayer != null) {
            log.info("[시드] 이미 시드 데이터가 존재하여 건너뜁니다. master={}", existingPlayer.displayName)
            return
        }

        val seedPlayer = playerService.save(
            PlayerRequest(
                nickname = SEED_NICKNAME,
                registrationType = RegistrationType.DISCORD,
                registrationId = SEED_REGISTRATION_ID,
            )
        )

        // playlist.created_by는 AuditorAwareImpl이 SecurityContext에서 읽어 채운다.
        // 인증 컨텍스트 없이 저장하면 created_by=0인 유령 플레이리스트가 되어 목록 API에서 걸러진다.
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(seedPlayer.id, null, emptyList())

        val playlist = try {
            playlistService.save(seedPlayer.id, yoasobiPlaylistRequest())
        } finally {
            SecurityContextHolder.clearContext()
        }

        log.info(
            "[시드] 테스트 플레이리스트를 생성했습니다. playlistId={}, title={}, trackCount={}, master={}",
            playlist.id,
            playlist.title,
            playlist.tracks.size,
            seedPlayer.displayName,
        )
    }

    private fun yoasobiPlaylistRequest(): PlaylistCreationRequest {
        return PlaylistCreationRequest(
            title = "YOASOBI 모음",
            description = "로컬 테스트용 YOASOBI 플레이리스트",
            tracks = listOf(
                track(
                    embedId = "by4SYYWlhEs",
                    title = "夜に駆ける",
                    additionalTitles = setOf("밤을 달리다", "요루니 카케루", "Yoru ni Kakeru", "Racing Into the Night"),
                    isRepresentative = true,
                ),
                track(
                    embedId = "ZRtdQ81jPUQ",
                    title = "アイドル",
                    additionalTitles = setOf("아이돌", "아이도루", "Idol"),
                ),
                track(
                    embedId = "Y4nEEZwckuU",
                    title = "群青",
                    additionalTitles = setOf("군청", "군조", "Gunjou"),
                ),
                track(
                    embedId = "dy90tA3TT1c",
                    title = "怪物",
                    additionalTitles = setOf("괴물", "카이부츠", "Kaibutsu", "Monster"),
                ),
                track(
                    embedId = "kzdJkT4kp-A",
                    title = "ハルジオン",
                    additionalTitles = setOf("하루지온", "Haruzion", "Halzion"),
                ),
                track(
                    embedId = "8iuLXODzL04",
                    title = "たぶん",
                    additionalTitles = setOf("타분", "Tabun", "Probably"),
                ),
                track(
                    embedId = "nhOhFOoURnE",
                    title = "三原色",
                    additionalTitles = setOf("삼원색", "산겐쇼쿠", "Sangenshoku"),
                ),
                track(
                    embedId = "3eytpBOkOFA",
                    title = "祝福",
                    additionalTitles = setOf("축복", "슈쿠후쿠", "Shukufuku", "The Blessing"),
                ),
                track(
                    embedId = "VyvhvlYvRnc",
                    title = "優しい彗星",
                    additionalTitles = setOf("상냥한 혜성", "야사시이 스이세이", "Yasashii Suisei", "Comet"),
                ),
            ),
        )
    }

    private fun track(
        embedId: String,
        title: String,
        additionalTitles: Set<String>,
        isRepresentative: Boolean = false,
    ): PlaylistCreationRequestTrack {
        return PlaylistCreationRequestTrack(
            embedId = embedId,
            title = title,
            startTimeSec = CLIP_START_TIME_SEC,
            endTimeSec = CLIP_END_TIME_SEC,
            repeatCount = 1,
            additionalTitles = additionalTitles,
            isRepresentative = isRepresentative,
        )
    }

    companion object {
        private const val SEED_NICKNAME = "노맞봇"
        private const val SEED_REGISTRATION_ID = "local-seed-master"

        // 라운드 제한시간 = (endTimeSec - startTimeSec) * repeatCount + 2초 → 27초
        private const val CLIP_START_TIME_SEC = 30
        private const val CLIP_END_TIME_SEC = 55
    }
}
