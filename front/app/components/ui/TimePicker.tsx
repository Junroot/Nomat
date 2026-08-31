import { useRef } from "react";

interface TimePickerProps {
    timeSec: number
    setTimeSec: (value: number) => void
}

export default function TimePicker({timeSec, setTimeSec}: TimePickerProps) {
    const formatedTime = formatTime(timeSec)
    const inputRef = useRef<HTMLInputElement>(null)

    function formatTime(timeSec: number) {
        const second = timeSec % 60
        const minute = Math.floor(timeSec / 60) % 60
        const hour = Math.floor(timeSec / 3600)

        return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}:${String(second).padStart(2, "0")}`
    }

    function isValidFormatedTime(formatedTime: string) {
        if (formatedTime.length !== 8) return false

        const hh = parseInt(formatedTime.slice(0, 2))
        const mm = parseInt(formatedTime.slice(3, 5))
        const ss = parseInt(formatedTime.slice(6, 8))

        return hh >= 0 && hh <= 99
            && mm >= 0 && mm <= 59
            && ss >= 0 && ss <= 59
    }

    function convertToTimeSec(timeString: string) {
        const hh = parseInt(timeString.slice(0, 2))
        const mm = parseInt(timeString.slice(3, 5))
        const ss = parseInt(timeString.slice(6, 8))

        return hh * 3600 + mm * 60 + ss
    }

    // IME(일본어 등)가 켜져 있으면 숫자 키의 e.key가 "0"으로 오지 않는다.
    // 전각 숫자("０")로 오거나 IME가 키를 소비해 "Process"로 온다.
    function extractDigit(e: React.KeyboardEvent<HTMLInputElement>) {
        // 사용자의 키보드 레이아웃을 존중하도록 e.key를 먼저 본다.
        const normalizedKey = e.key.normalize("NFKC")
        if (/^[0-9]$/.test(normalizedKey)) return normalizedKey

        // e.key가 IME에 뭉개진 경우에만 물리 키 위치로 폴백한다.
        // 단축키(Cmd+1 등)와 Shift 조합이 숫자로 새지 않도록 제외한다.
        if (e.shiftKey || e.metaKey || e.ctrlKey || e.altKey) return null
        return /^(?:Digit|Numpad)([0-9])$/.exec(e.code)?.[1] ?? null
    }

    function handleInputKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
        const digit = extractDigit(e)
        if (e.key !== "ArrowUp" && e.key !== "ArrowDown" && digit === null) return

        const input = inputRef.current
        if (!input) return

        let pos = input.selectionStart ?? 0

        if (digit !== null) {
            if (pos === 2 || pos === 5) {
                pos += 1
            }

            const newFormatedTime = formatedTime.slice(0, pos) + digit + formatedTime.slice(pos + 1)
            const isAccepted = isValidFormatedTime(newFormatedTime)
            if (isAccepted) {
                setTimeSec(convertToTimeSec(newFormatedTime))
            }

            const nextPos = isAccepted ? pos + 1 : pos
            requestAnimationFrame(() => {
                // 입력이 거부되면 리렌더가 없어, IME가 preventDefault를 무시하고
                // 삽입한 문자가 화면에 그대로 남는다. DOM 값을 직접 되돌린다.
                if (!isAccepted && input.value !== formatedTime) {
                    input.value = formatedTime
                }
                input.setSelectionRange(nextPos, nextPos)
            })
        } else {
            // 커서 위치에 따라 조절 대상 결정
            if (pos >= 0 && pos <= 2) {
                adjustTime(e.key === "ArrowUp" ? 3600 : -3600)
            } else if (pos >= 3 && pos <= 5) {
                adjustTime(e.key === "ArrowUp" ? 60 : -60);
            } else if (pos >= 6) {
                adjustTime(e.key === "ArrowUp" ? 1 : -1);
            }

            requestAnimationFrame(() => {
                input.setSelectionRange(pos, pos)
            })
        }

        // prevent page scroll
        e.preventDefault();
    }

    const adjustTime = (deltaSec: number): void => {
        const resultTime = timeSec + deltaSec
        if (resultTime < 0) return
        if (resultTime > 99 * 3600 + 59 * 60 + 59) return
        setTimeSec(resultTime)
    }

    return (
        <div className="flex-1 h-10 p-2 shrink bg-surface border border-border text-zinc-200 rounded-full focus-within:border-neon-cyan focus-within:shadow-glow-cyan transition-all duration-200">
            <input
                ref={inputRef}
                type="text"
                value={formatedTime}
                onChange={() => {}}
                onKeyDown={handleInputKeyDown}
                className="w-full pl-[8px] caret-neon-cyan focus:outline-none"
            />
        </div>
    )
}
