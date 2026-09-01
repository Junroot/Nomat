import { useRef, useState } from "react";
import Modal, { ModalTitle } from "~/components/ui/Modal";

interface PasswordModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSubmit: (password: string) => void;
    isLoading: boolean;
    error: string | null;
}

export default function PasswordModal({ isOpen, onClose, onSubmit, isLoading, error }: PasswordModalProps) {
    const [password, setPassword] = useState("");
    const passwordInputRef = useRef<HTMLInputElement>(null);

    function handleSubmit(e: React.FormEvent) {
        e.preventDefault();
        if (password.trim() && !isLoading) {
            onSubmit(password);
        }
    }

    // 닫기 신호는 무조건 이행한다. 연결 중 닫기 억제는 Modal의 dismissible이 담당한다
    // — 단 ESC 억제는 최선 노력이라, 플랫폼이 강제로 닫는 경로는 막을 수 없다.
    function handleClose() {
        setPassword("");
        onClose();
    }

    return (
        <Modal
            isOpen={isOpen}
            onClose={handleClose}
            dismissible={!isLoading}
            initialFocusRef={passwordInputRef}
        >
            <form onSubmit={handleSubmit} className="w-72">
                <ModalTitle>비밀번호 입력</ModalTitle>
                <input
                    ref={passwordInputRef}
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="비밀번호를 입력하세요"
                    className="w-full px-3 py-2 bg-zinc-800 border border-border rounded-lg text-sm text-zinc-200 placeholder-zinc-500 focus:outline-none focus:border-neon-cyan/50 mb-2"
                    disabled={isLoading}
                />
                {error && (
                    <p className="text-xs text-red-400 mb-2">{error}</p>
                )}
                <div className="flex gap-2 mt-4">
                    <button
                        type="button"
                        onClick={handleClose}
                        disabled={isLoading}
                        className="flex-1 px-4 py-2 text-sm text-zinc-400 bg-zinc-800 border border-border rounded-lg hover:bg-zinc-700 transition-colors disabled:opacity-50"
                    >
                        취소
                    </button>
                    <button
                        type="submit"
                        disabled={!password.trim() || isLoading}
                        className="flex-1 px-4 py-2 text-sm font-semibold text-zinc-900 bg-neon-cyan rounded-lg hover:bg-neon-cyan/80 transition-colors disabled:opacity-50"
                    >
                        {isLoading ? "연결 중..." : "입장"}
                    </button>
                </div>
            </form>
        </Modal>
    );
}
