package ilpak.nomat.room.application.domain

/**
 * 정답 판정 규칙: 입력과 정답 양쪽에서 모든 공백을 제거하고 대소문자를 무시해 비교한다.
 * 예: 정답 `밤을 달리다` ↔ `밤을 달 리다`·`밤 을 달리다`는 모두 정답으로 인정된다.
 */
object AnswerMatcher {

    private fun normalize(value: String): String = value.filterNot { it.isWhitespace() }.lowercase()

    fun matches(input: String, answers: Collection<String>): Boolean {
        val normalizedInput = normalize(input)
        if (normalizedInput.isEmpty()) {
            return false
        }
        return answers.any { normalize(it) == normalizedInput }
    }
}
