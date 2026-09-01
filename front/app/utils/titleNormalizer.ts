/**
 * 정답 매칭과 추가 정답 중복 판정이 공유하는 표기 정규화 규칙.
 *
 * **소스 오브 트루스는 백엔드의 `common/normalize/TitleNormalizer.kt`다.**
 * 여기는 사용자가 이미 인정되는 값을 추가하기 전에 미리 막기 위한 재구현이고,
 * 최종 판정은 언제나 서버가 한다. 규칙을 고칠 일이 생기면 Kotlin 쪽을 먼저 고치고
 * 이 파일을 맞춘다. 두 구현이 갈라져도 데이터 무결성은 서버가 지킨다.
 *
 * 기준은 하나다 — 같은 내용을 다르게 "표기"한 것만 접고, 내용이 다른 것은 접지 않는다.
 *
 * | 축 | 예 | 판정 |
 * |---|---|---|
 * | 전각 ↔ 반각 | `ﾏｲｳｪｲ` / `マイウェイ` | 접음 |
 * | 히라가나 ↔ 가타카나 | `まいうぇい` / `マイウェイ` | 접음 |
 * | 큰 가나 ↔ 작은 가나 | `ウエイ` / `ウェイ` | 접음 |
 * | 대소문자 | `Monster` / `monster` | 접음 |
 * | 공백·구두점·기호 | `밤을 달리다!` / `밤을달리다` | 접음 |
 * | 탁점·반탁점 | `ハハ` / `ババ` | **접지 않음** |
 * | 장음 부호 | `メール` / `メル` | **접지 않음** |
 * | 괄호 안의 내용 | `Monster` / `Monster (feat. X)` | **접지 않음** |
 *
 * 단계 순서가 규칙의 일부다 — 히라가나→가타카나를 먼저 해야 작은 가나 매핑 테이블을
 * 가타카나 12자로만 유지할 수 있다 (`ぁ` → `ァ` → `ア`).
 */

const HIRAGANA_FIRST = 0x3041
const HIRAGANA_LAST = 0x3096
const HIRAGANA_TO_KATAKANA_OFFSET = 0x60

const SMALL_TO_LARGE_KANA: Record<string, string> = {
    "ァ": "ア", "ィ": "イ", "ゥ": "ウ", "ェ": "エ", "ォ": "オ",
    "ッ": "ツ", "ャ": "ヤ", "ュ": "ユ", "ョ": "ヨ", "ヮ": "ワ",
    "ヵ": "カ", "ヶ": "ケ",
}

// `u` 플래그가 있어야 `\p{L}`·`\p{N}` 유니코드 속성이 동작한다.
const NON_LETTER_OR_DIGIT = /[^\p{L}\p{N}]/gu
const WHITESPACE = /\s/gu

function foldKana(char: string): string {
    const code = char.codePointAt(0) ?? 0
    const katakana = code >= HIRAGANA_FIRST && code <= HIRAGANA_LAST
        ? String.fromCodePoint(code + HIRAGANA_TO_KATAKANA_OFFSET)
        : char
    return SMALL_TO_LARGE_KANA[katakana] ?? katakana
}

/**
 * 비교에만 쓰는 정규화 키를 만든다. 서버로 보내는 값은 입력 원문 그대로여야 한다.
 */
export function normalizeTitle(value: string): string {
    const folded = Array.from(value.normalize("NFKC").toLowerCase())
        .map(foldKana)
        .join("")

    // `★`·`!!!` 처럼 문자·숫자가 하나도 없는 제목은 키가 비어 서로 구분 불가해진다.
    // 이때만 공백 제거로 폴백한다 — 제거를 통째로 건너뛰면 `!!!`과 `! ! !`이 갈린다.
    const stripped = folded.replace(NON_LETTER_OR_DIGIT, "")
    return stripped === "" ? folded.replace(WHITESPACE, "") : stripped
}

/**
 * 이미 등록된 값 중 `candidate`와 정규화 기준으로 같은 것이 있는지 본다.
 * 있으면 그 값은 서버 판정상 이미 정답으로 인정되므로 추가할 이유가 없다.
 */
export function isAlreadyCovered(candidate: string, registered: readonly string[]): boolean {
    const key = normalizeTitle(candidate)
    return registered.some((title) => normalizeTitle(title) === key)
}
