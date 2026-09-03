import { useEffect, useRef, useState } from "react";

interface ChatInputProps {
    // 전송할 내용(trim 완료·비어 있지 않음). 부모는 참조가 고정된 콜백을 넘겨야 한다 —
    // 매 렌더 새 함수를 만들면 이 컴포넌트가 부모 렌더마다 함께 렌더된다.
    onSend: (content: string) => void;
}

/**
 * 채팅 입력창. **입력값 상태를 이 컴포넌트가 소유한다.**
 *
 * 방 화면에서 가장 자주 바뀌는 상태가 키 입력값인데, 그것이 화면 루트(`RoomView`)에 있으면
 * 한 글자마다 메시지 목록·라운드 패널·오디오 플레이어가 전부 다시 렌더된다. 상태를 그것을
 * 쓰는 곳으로 내리면 키 입력은 여기서 끝나고, 부모는 전송 시점에만 `onSend`로 관여한다.
 *
 * 채팅 영역 밖에서 Enter를 누르면 입력창으로 포커스를 옮기는 전역 리스너도 여기 있다 —
 * `inputRef`에만 의존하므로 입력창을 소유하는 컴포넌트가 들고 있는 것이 맞다.
 */
export default function ChatInput({ onSend }: ChatInputProps) {
    const [input, setInput] = useState("");
    const inputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        function handleKeyDown(e: KeyboardEvent) {
            if (e.key === "Enter" && document.activeElement !== inputRef.current) {
                e.preventDefault();
                inputRef.current?.focus();
            }
        }
        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, []);

    function handleSend() {
        const trimmed = input.trim();
        if (!trimmed) return;
        onSend(trimmed);
        setInput("");
    }

    return (
        <div className="p-2 m-2 flex items-center gap-2 rounded-full bg-surface border border-border focus-within:border-neon-cyan focus-within:shadow-glow-cyan transition-all duration-200">
            <input
                ref={inputRef}
                type="text"
                placeholder="보낼 메시지 입력"
                className="flex-1 p-[2px] pl-[8px] placeholder-zinc-500 focus:outline-none"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                maxLength={200}
                onKeyDown={(e) => {
                    // IME(한글·일본어) 조합 중 Enter는 조합 확정이지 전송이 아니다.
                    if (e.key === "Enter" && !e.nativeEvent.isComposing) {
                        e.preventDefault();
                        handleSend();
                    }
                }}
            />
            <button
                type="button"
                className="size-7 flex items-center justify-center rounded-full bg-neon-cyan/20 text-neon-cyan hover:bg-neon-cyan/30 disabled:opacity-30 disabled:cursor-not-allowed transition-colors cursor-pointer shrink-0"
                disabled={!input.trim()}
                onClick={handleSend}
            >
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" className="size-4">
                    <path d="M3.105 2.288a.75.75 0 0 0-.826.95l1.414 4.926A1.5 1.5 0 0 0 5.135 9.25h6.115a.75.75 0 0 1 0 1.5H5.135a1.5 1.5 0 0 0-1.442 1.086l-1.414 4.926a.75.75 0 0 0 .826.95l14.095-5.637a.75.75 0 0 0 0-1.394L3.105 2.289Z" />
                </svg>
            </button>
        </div>
    );
}
