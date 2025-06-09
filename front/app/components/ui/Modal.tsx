interface ModalProps {
    isOpen: boolean;
    onClose: () => void;
    children: React.ReactNode;
}

export default function Modal({ isOpen, onClose, children }: ModalProps) {
    if (!isOpen) return null;

    return (
        <div 
            className="fixed inset-0 flex items-center justify-center bg-black/50"
            onClick={onClose}
        >
            <div
                className="bg-zinc-900 opacity-100 p-6 rounded-2xl shadow-lg"
                onClick={(e) => e.stopPropagation()} // 내부 클릭 시 닫히지 않도록 설정
            >
                {children}
            </div>
        </div>
    )
}
