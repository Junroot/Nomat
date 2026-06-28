package ilpak.nomat.room.application.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AnswerMatcherTest {

    @Test
    fun `matches_공백만 다른 답도 정답으로 인정된다`() {
        val answers = setOf("밤을 달리다")

        assertThat(AnswerMatcher.matches("밤을 달 리다", answers)).isTrue()
        assertThat(AnswerMatcher.matches("밤을     달리다", answers)).isTrue()
        assertThat(AnswerMatcher.matches("밤 을 달리다", answers)).isTrue()
        assertThat(AnswerMatcher.matches("밤을달리다", answers)).isTrue()
    }

    @Test
    fun `matches_대소문자를 무시한다`() {
        assertThat(AnswerMatcher.matches("Hello World", setOf("helloworld"))).isTrue()
    }

    @Test
    fun `matches_additionalTitles 중 하나와 일치하면 정답이다`() {
        assertThat(AnswerMatcher.matches("alt title", setOf("정답", "ALT TITLE"))).isTrue()
    }

    @Test
    fun `matches_정답과 다르면 오답이다`() {
        assertThat(AnswerMatcher.matches("틀린 답", setOf("밤을 달리다"))).isFalse()
    }

    @Test
    fun `matches_공백뿐인 입력은 오답이다`() {
        assertThat(AnswerMatcher.matches("   ", setOf("밤을 달리다"))).isFalse()
    }

    @Test
    fun `normalize_모든 공백을 제거하고 소문자로 바꾼다`() {
        assertThat(AnswerMatcher.normalize("  A b\tC ")).isEqualTo("abc")
    }
}
