package ilpak.nomat.room.application.domain

import ilpak.nomat.common.normalize.TitleNormalizer

/**
 * 정답 판정 규칙: 입력과 정답 양쪽을 [TitleNormalizer]로 표기 정규화한 뒤 비교한다.
 *
 * 표기 흔들림(전각/반각·히라가나/가타카나·큰 가나/작은 가나·대소문자·공백/구두점/기호)은 접고,
 * 내용이 다른 것(탁점·장음 부호·괄호 안의 내용)은 접지 않는다. 축별 판정과 근거는 [TitleNormalizer] 참고.
 *
 * 예: 정답 `밤을 달리다` ↔ `밤을 달 리다`·`밤 을 달리다!`, 정답 `マイウェイ` ↔ `まいうぇい`·`ﾏｲｳｪｲ`·`マイ・ウェイ`.
 */
object AnswerMatcher {

    fun matches(input: String, answers: Collection<String>): Boolean {
        val normalizedInput = TitleNormalizer.normalize(input)
        if (normalizedInput.isEmpty()) {
            return false
        }
        return answers.any { TitleNormalizer.normalize(it) == normalizedInput }
    }
}
