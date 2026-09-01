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
        assertThat(AnswerMatcher.matches("밤을\t달리다", answers)).isTrue()
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
    fun `matches_구두점과 기호만 다른 답도 정답으로 인정된다`() {
        assertThat(AnswerMatcher.matches("밤을달리다", setOf("밤을 달리다!"))).isTrue()
        assertThat(AnswerMatcher.matches("夏Summer", setOf("夏〜Summer〜"))).isTrue()
        assertThat(AnswerMatcher.matches("dont stop", setOf("Don't Stop"))).isTrue()
    }

    @Test
    fun `matches_가나 표기만 다른 답도 정답으로 인정된다`() {
        val answers = setOf("ファイティングマイウェイ")

        assertThat(AnswerMatcher.matches("ファイティングマイウエイ", answers)).isTrue()
        assertThat(AnswerMatcher.matches("ふぁいてぃんぐまいうぇい", answers)).isTrue()
        assertThat(AnswerMatcher.matches("ﾌｧｲﾃｨﾝｸﾞﾏｲｳｪｲ", answers)).isTrue()
        assertThat(AnswerMatcher.matches("ファイティング・マイ・ウェイ", answers)).isTrue()
    }

    @Test
    fun `matches_탁점이 다른 답은 오답이다`() {
        assertThat(AnswerMatcher.matches("ババ", setOf("ハハ"))).isFalse()
        assertThat(AnswerMatcher.matches("がっこう", setOf("かっこう"))).isFalse()
    }

    @Test
    fun `matches_장음 부호가 다른 답은 오답이다`() {
        assertThat(AnswerMatcher.matches("メル", setOf("メール"))).isFalse()
    }

    @Test
    fun `matches_괄호 꼬리표를 뺀 답은 별도 등록 없이는 오답이다`() {
        assertThat(AnswerMatcher.matches("Monster", setOf("Monster (feat. X)"))).isFalse()
    }

    @Test
    fun `matches_문자와 숫자가 없는 정답도 종전대로 매칭된다`() {
        assertThat(AnswerMatcher.matches("★", setOf("★"))).isTrue()
        assertThat(AnswerMatcher.matches("! ! !", setOf("!!!"))).isTrue()
    }
}
