package ilpak.nomat.common.normalize

import java.text.Normalizer
import java.util.Locale

/**
 * 정답 매칭과 추가 정답 중복 판정이 공유하는 표기 정규화 규칙.
 *
 * 기준은 하나다 — **같은 내용을 다르게 "표기"한 것만 접고, 내용이 다른 것은 접지 않는다.**
 *
 * | 축 | 예 | 판정 |
 * |---|---|---|
 * | 전각 ↔ 반각 | `ﾏｲｳｪｲ` / `マイウェイ` | 접음 |
 * | 히라가나 ↔ 가타카나 | `まいうぇい` / `マイウェイ` | 접음 |
 * | 큰 가나 ↔ 작은 가나 | `ウエイ` / `ウェイ` | 접음 |
 * | 대소문자 | `Monster` / `monster` | 접음 |
 * | 공백·구두점·기호 | `밤을 달리다!` / `밤을달리다` | 접음 |
 * | 탁점·반탁점 | `ハハ` / `ババ` | **접지 않음** — `かっこう`(뻐꾸기) ≠ `がっこう`(학교) |
 * | 장음 부호 | `メール` / `メル` | **접지 않음** — 발음이 다르다 |
 * | 괄호 안의 내용 | `Monster` / `Monster (feat. X)` | **접지 않음** — 괄호 안은 정보다 |
 *
 * 마지막 두 축은 예외 목록이 아니라 [NON_LETTER_OR_DIGIT] 규칙의 자연스러운 귀결이다.
 * `・`(Po)·`〜`(Pd)는 제거되지만 `ー`(장음, Lm)·`々`(Lm)는 문자이므로 보존된다.
 * 괄호 문자 자체는 구두점이라 사라지고 그 안의 내용은 남는다.
 *
 * **단계 순서가 규칙의 일부다.** 히라가나→가타카나를 먼저 해야 작은 가나 매핑 테이블을
 * 가타카나 12자로만 유지할 수 있다 (`ぁ` → `ァ` → `ア`).
 */
object TitleNormalizer {

    private const val HIRAGANA_FIRST = 'ぁ'
    private const val HIRAGANA_LAST = 'ゖ'
    private const val HIRAGANA_TO_KATAKANA_OFFSET = 0x60

    private val SMALL_TO_LARGE_KANA = mapOf(
        'ァ' to 'ア', 'ィ' to 'イ', 'ゥ' to 'ウ', 'ェ' to 'エ', 'ォ' to 'オ',
        'ッ' to 'ツ', 'ャ' to 'ヤ', 'ュ' to 'ユ', 'ョ' to 'ヨ', 'ヮ' to 'ワ',
        'ヵ' to 'カ', 'ヶ' to 'ケ',
    )

    private val NON_LETTER_OR_DIGIT = Regex("[^\\p{L}\\p{N}]")

    /**
     * 비교에만 쓰는 정규화 키를 만든다. 저장값은 입력 원문 그대로 두어야 한다.
     */
    fun normalize(value: String): String {
        val folded = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT) // 터키어 로케일에서 `I` → `ı`가 되는 것을 막는다.
            .map(::foldKana)
            .joinToString("")

        // `★`·`!!!` 처럼 문자·숫자가 하나도 없는 제목은 키가 비어 서로 구분 불가해진다.
        // 이때만 종전 규칙(공백 제거)으로 폴백해 배포 전후 판정을 연속시킨다.
        // 제거를 통째로 건너뛰지 않는 이유는 `!!!`과 `! ! !`이 같은 키가 되어야 하기 때문이다.
        return NON_LETTER_OR_DIGIT.replace(folded, "")
            .ifEmpty { folded.filterNot { it.isWhitespace() } }
    }

    private fun foldKana(char: Char): Char {
        val katakana = if (char in HIRAGANA_FIRST..HIRAGANA_LAST) {
            char + HIRAGANA_TO_KATAKANA_OFFSET
        } else {
            char
        }
        return SMALL_TO_LARGE_KANA[katakana] ?: katakana
    }
}
