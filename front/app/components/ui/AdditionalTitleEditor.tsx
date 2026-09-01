import { useState } from "react"
import { isAlreadyCovered } from "~/utils/titleNormalizer"

interface AdditionalTitleEditorProps {
    maxAdditionalTitlesCount: number,
    additionalTitles: Array<string>,
    setAdditionalTitles: (value: Array<string>) => void
}

// 백엔드 `Track.MAX_TITLE_LENGTH`와 같은 값이어야 한다.
const MAX_TITLE_LENGTH = 100

export default function AdditionalTitleEditor({maxAdditionalTitlesCount, additionalTitles, setAdditionalTitles}: AdditionalTitleEditorProps) {
    const [additionalTitle, setAdditionalTitle] = useState("")
    const [notice, setNotice] = useState("")

    function handleInputKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
        if (e.key !== "Enter" || e.nativeEvent.isComposing) return
        if (!isValidAdditionalTitle()) return

        const trimedAdditionalTitle = additionalTitle.trim()

        // 표기만 다른 값은 서버 매칭에서 이미 같은 정답으로 인정되므로 추가할 이유가 없다.
        // 사용자 눈에는 다른 글자이므로 조용히 무시하지 않고 왜 안 담기는지 알린다.
        if (isAlreadyCovered(trimedAdditionalTitle, additionalTitles)) {
            setNotice("이미 등록된 정답으로 인정되는 표기입니다.")
            return
        }

        setAdditionalTitles([...additionalTitles, trimedAdditionalTitle])
        setAdditionalTitle("")
        setNotice("")
    }

    function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
        setAdditionalTitle(e.target.value)
        setNotice("")
    }

    function isValidAdditionalTitle() {
        const length = additionalTitle.trim().length
        return length > 0 && length <= MAX_TITLE_LENGTH
    }

    function canInput() {
        return maxAdditionalTitlesCount > additionalTitles.length;
    }

    return (
        <div className="flex flex-col gap-1">
            <div className={"flex-1 h-10 p-2 shrink border text-zinc-200 rounded-full transition-all duration-200 " + (canInput() ? "bg-surface border-border focus-within:border-neon-cyan focus-within:shadow-glow-cyan" : "bg-zinc-800 border-border")}>
                <input
                    disabled={!canInput()}
                    type="text"
                    placeholder="추가 정답 (엔터를 눌러 추가)"
                    value={additionalTitle}
                    maxLength={MAX_TITLE_LENGTH}
                    onChange={handleInputChange}
                    onKeyDown={handleInputKeyDown}
                    className="w-full pl-[8px] placeholder-zinc-500 focus:outline-none"
                />
            </div>
            <p className="text-xs text-warning h-4">{notice}</p>
        </div>
    )
}
