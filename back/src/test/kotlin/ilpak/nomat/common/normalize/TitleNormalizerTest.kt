package ilpak.nomat.common.normalize

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TitleNormalizerTest {

    @Test
    fun `normalize_전각과 반각을 접는다`() {
        assertThat(TitleNormalizer.normalize("ﾏｲｳｪｲ"))
            .isEqualTo(TitleNormalizer.normalize("マイウェイ"))
        assertThat(TitleNormalizer.normalize("ＭＯＮＳＴＥＲ"))
            .isEqualTo(TitleNormalizer.normalize("MONSTER"))
    }

    @Test
    fun `normalize_히라가나와 가타카나를 접는다`() {
        assertThat(TitleNormalizer.normalize("ふぁいてぃんぐまいうぇい"))
            .isEqualTo(TitleNormalizer.normalize("ファイティングマイウェイ"))
    }

    @Test
    fun `normalize_큰 가나와 작은 가나를 접는다`() {
        assertThat(TitleNormalizer.normalize("マイウエイ"))
            .isEqualTo(TitleNormalizer.normalize("マイウェイ"))
        assertThat(TitleNormalizer.normalize("がっこう"))
            .isEqualTo(TitleNormalizer.normalize("がつこう"))
    }

    @Test
    fun `normalize_히라가나 작은 글자도 큰 가타카나로 수렴한다`() {
        // 단계 순서(히라가나→가타카나 먼저)가 지켜지지 않으면 `ぇ`가 `ェ`에 멈춘다.
        assertThat(TitleNormalizer.normalize("うぇい"))
            .isEqualTo(TitleNormalizer.normalize("ウエイ"))
    }

    @Test
    fun `normalize_대소문자를 접는다`() {
        assertThat(TitleNormalizer.normalize("Monster")).isEqualTo(TitleNormalizer.normalize("monster"))
    }

    @Test
    fun `normalize_공백과 구두점과 기호를 접는다`() {
        assertThat(TitleNormalizer.normalize("밤을 달리다!")).isEqualTo(TitleNormalizer.normalize("밤을달리다"))
        assertThat(TitleNormalizer.normalize("マイ・ウェイ")).isEqualTo(TitleNormalizer.normalize("マイウェイ"))
        assertThat(TitleNormalizer.normalize("夏〜Summer〜")).isEqualTo(TitleNormalizer.normalize("夏summer"))
        assertThat(TitleNormalizer.normalize("Don't Stop")).isEqualTo(TitleNormalizer.normalize("dont stop"))
        assertThat(TitleNormalizer.normalize("밤을\t달리다")).isEqualTo(TitleNormalizer.normalize("밤을달리다"))
    }

    @Test
    fun `normalize_괄호 문자는 지우고 안의 내용은 남긴다`() {
        assertThat(TitleNormalizer.normalize("Monster (feat. X)")).isEqualTo("monsterfeatx")
        assertThat(TitleNormalizer.normalize("【MV】夏")).isEqualTo(TitleNormalizer.normalize("mv夏"))
    }

    @Test
    fun `normalize_탁점과 반탁점은 접지 않는다`() {
        assertThat(TitleNormalizer.normalize("ハハ")).isNotEqualTo(TitleNormalizer.normalize("ババ"))
        assertThat(TitleNormalizer.normalize("かっこう")).isNotEqualTo(TitleNormalizer.normalize("がっこう"))
        assertThat(TitleNormalizer.normalize("ハハ")).isNotEqualTo(TitleNormalizer.normalize("パパ"))
    }

    @Test
    fun `normalize_반각 탁점도 합성되어 보존된다`() {
        // NFKC 가 `ｶﾞ` 를 `ガ` 로 합성한다. 탁점이 사라져 `カ` 가 되면 안 된다.
        assertThat(TitleNormalizer.normalize("ｶﾞ")).isEqualTo(TitleNormalizer.normalize("ガ"))
        assertThat(TitleNormalizer.normalize("ｶﾞ")).isNotEqualTo(TitleNormalizer.normalize("カ"))
    }

    @Test
    fun `normalize_장음 부호는 접지 않는다`() {
        assertThat(TitleNormalizer.normalize("メール")).isNotEqualTo(TitleNormalizer.normalize("メル"))
        assertThat(TitleNormalizer.normalize("メール")).isEqualTo("メール")
    }

    @Test
    fun `normalize_반복 기호는 문자이므로 보존된다`() {
        assertThat(TitleNormalizer.normalize("人々")).isEqualTo("人々")
    }

    @Test
    fun `normalize_괄호 안의 내용은 접지 않는다`() {
        assertThat(TitleNormalizer.normalize("Monster"))
            .isNotEqualTo(TitleNormalizer.normalize("Monster (feat. X)"))
    }

    @Test
    fun `normalize_문자와 숫자가 없는 값은 빈 키가 되지 않는다`() {
        assertThat(TitleNormalizer.normalize("★")).isEqualTo("★")
        assertThat(TitleNormalizer.normalize("!!!")).isEqualTo("!!!")
    }

    @Test
    fun `normalize_문자와 숫자가 없는 값도 공백 흔들림은 접는다`() {
        assertThat(TitleNormalizer.normalize("! ! !")).isEqualTo(TitleNormalizer.normalize("!!!"))
        assertThat(TitleNormalizer.normalize("★ ★")).isEqualTo(TitleNormalizer.normalize("★★"))
    }

    @Test
    fun `normalize_빈 값과 공백뿐인 값은 빈 키가 된다`() {
        assertThat(TitleNormalizer.normalize("")).isEmpty()
        assertThat(TitleNormalizer.normalize("   ")).isEmpty()
    }

    @Test
    fun `normalize_숫자는 보존된다`() {
        assertThat(TitleNormalizer.normalize("Track #1")).isEqualTo("track1")
    }
}
