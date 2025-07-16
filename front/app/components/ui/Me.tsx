import { useEffect, useRef, useState } from "react";
import UserIcon from "~/assets/user.svg?react";
import "./Me.css"
import MeStore from "~/stores/MeStore";
import { fetchMe, updateNickname } from "~/utils/api";
import { getRegistrationCode } from "~/utils/MeResponse";
import Modal from "../ui/Modal";
import type { AxiosError } from "axios";

export default function Me() {
    const meStore = MeStore()
    if (!meStore.me) {
        fetchMe()
            .then(me => meStore.setMe(me))
            .catch(() => {
                window.location.href = `${window.location.origin}/login?redirectUrl=${window.location.href}`
            })
    }
    const me = meStore.me

    const nicknameRef = useRef<HTMLDivElement>(null);
    const defaultWidth = 80;
    const [width, setWidth] = useState(defaultWidth);
    const [isHover, setHover] = useState(false);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [newNickname, setNewNickname] = useState("");
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!isHover) {
            setWidth(defaultWidth);
            return;
        }

        const nicknameRefCurrent = nicknameRef.current
        if (!nicknameRefCurrent) {
            return
        }

        setWidth(nicknameRefCurrent.clientWidth + defaultWidth);
    }, [isHover]);

    const isNicknameChanged = () => {
        return newNickname.trim() !== "" && newNickname.trim() !== me?.nickname;
    };

    const handleNicknameUpdate = async () => {
        if (!isNicknameChanged()) {
            return;
        }
        
        try {
            setError(null);
            await updateNickname(newNickname);
            const updatedMe = await fetchMe();
            meStore.setMe(updatedMe);
            setIsModalOpen(false);
            setNewNickname("");
        } catch (error) {
            setError((error as AxiosError<{message: string}>).response?.data.message ?? "닉네임 업데이트에 실패했습니다.");
        }
    };

    return <>
        <div className="me size-[96px] p-[8px]">
            <div 
                className={`h-[80px] flex flex-row cursor-pointer items-center align-middle profile transition-all transform rounded-full ${isHover ? "drop-shadow-lg" : ""}`}
                onMouseEnter={ () => setHover(true) }
                onMouseLeave={ () => setHover(false) }
                onClick={() => {
                    setIsModalOpen(true);
                    setNewNickname(me?.nickname || "");
                    setError(null);
                }}
                style={{ width: `${width}px` }}
            >
                <div className={`grow-0 shrink-0 w-[80px] h-[80px] p-[12px] flex`}>
                    <UserIcon className="size-[56px] m-auto"></UserIcon>
                </div>
                <div ref={nicknameRef} className={`grow-0 shrink-0 pr-[36px] my-auto text-4xl align-top nickname`}>
                    <p>{me?.nickname}<span className="text-xl ml-2 text-zinc-400">#{getRegistrationCode(me?.registrationType)}</span></p>
                </div>
            </div>
        </div>
        <Modal
            isOpen={isModalOpen}
            onClose={() => {
                setIsModalOpen(false);
                setError(null);
                setNewNickname("");
            }}
        >
            <div className="flex flex-col w-full min-w-[300px]">
                <h2 className="text-2xl font-bold mb-2">닉네임 수정</h2>
                <div className="flex flex-col gap-1">
                    <p className="px-4">닉네임</p>
                    <div className="flex-1 h-10 p-2 shrink bg-zinc-600 text-zinc-200 rounded-full">
                        <input
                            type="text"
                            placeholder="새로운 닉네임"
                            value={newNickname}
                            maxLength={40}
                            onChange={(e) => {
                                setNewNickname(e.target.value);
                                setError(null);
                            }}
                            className="w-full pl-[8px] focus:outline-none bg-transparent"
                        />
                    </div>
                    {error && (
                        <p className="px-4 text-xs text-red-600">{error}</p>
                    )}
                </div>
                <div className="flex flex-row mt-6 gap-2">
                    <button
                        className="flex-1 rounded-full p-2 bg-zinc-700 text-zinc-100 font-bold cursor-pointer"
                        onClick={() => {
                            setIsModalOpen(false);
                            setError(null);
                            setNewNickname("");
                        }}
                    >
                        취소
                    </button>
                    <button
                        className={`flex-1 rounded-full p-2 font-bold ${
                            isNicknameChanged()
                                ? "bg-cyan-400 text-zinc-900 cursor-pointer"
                                : "bg-zinc-800 text-zinc-500 cursor-not-allowed"
                        }`}
                        onClick={handleNicknameUpdate}
                        disabled={!isNicknameChanged()}
                    >
                        저장
                    </button>
                </div>
            </div>
        </Modal>
    </>;
}