import {type AnimationEvent, useEffect, useState} from "react";

interface ModalProps {
    isOpen: boolean;
    onClose: () => void;
    children: React.ReactNode;
}

export default function Modal({ isOpen, onClose, children }: ModalProps) {
    const [visible, setVisible] = useState(false);
    const [closing, setClosing] = useState(false);

    useEffect(() => {
        if (isOpen) {
            setVisible(true);
            setClosing(false);
        } else if (visible) {
            setClosing(true);
        }
    }, [isOpen]);

    function handleClose() {
        setClosing(true);
    }

    function handleAnimationEnd(e: AnimationEvent) {
        if (closing && e.currentTarget === e.target) {
            setVisible(false);
            setClosing(false);
            onClose();
        }
    }

    if (!visible) return null;

    return (
        <div
            className={`fixed inset-0 flex items-center justify-center bg-black/60 backdrop-blur-sm ${closing ? "animate-fade-out" : "animate-fade-in"}`}
            onClick={handleClose}
            onAnimationEnd={handleAnimationEnd}
        >
            <div
                className={`bg-surface border border-border p-6 rounded-2xl shadow-glow-cyan ${closing ? "animate-scale-out" : "animate-scale-in"}`}
                onClick={(e) => e.stopPropagation()}
            >
                {children}
            </div>
        </div>
    )
}
