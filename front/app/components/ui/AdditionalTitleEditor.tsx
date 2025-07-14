import { useState } from "react"

interface AdditionalTitleEditorProps {
    maxAdditionalTitlesCount: number,
    additionalTitles: Array<string>,
    setAdditionalTitles: (value: Array<string>) => void
}

export default function AdditionalTitleEditor({maxAdditionalTitlesCount, additionalTitles, setAdditionalTitles}: AdditionalTitleEditorProps) {
    const [additionalTitle, setAdditionalTitle] = useState("")
    const maxTitleLength = 100

    function handleInputKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
        if (e.key !== "Enter" || e.nativeEvent.isComposing) return
        if (!isValidAdditionalTitle()) return

        const trimedAdditionalTitle = additionalTitle.trim()
        console.log(trimedAdditionalTitle)

        if (additionalTitles.includes(trimedAdditionalTitle)) return

        setAdditionalTitles([...additionalTitles, trimedAdditionalTitle])
        setAdditionalTitle("")
    }

    function isValidAdditionalTitle() {
        return additionalTitle.trim().length > 0 && additionalTitle.trim.length < 50
    }

    function canInput() {
        return maxAdditionalTitlesCount > additionalTitles.length;
    }

    return (
        <div className={"flex-1 h-10 p-2 shrink text-zinc-200 rounded-full " + (canInput() ? "bg-zinc-600" : "bg-zinc-800")}>
            <input
                disabled={!canInput()}
                type="text"
                placeholder="추가 정답 (엔터를 눌러 추가)"
                value={additionalTitle}
                maxLength={maxTitleLength}
                onChange={(e) => {setAdditionalTitle(e.target.value)}}
                onKeyDown={handleInputKeyDown}
                className="w-full pl-[8px] focus:outline-none"
            />
        </div>
    )
}
