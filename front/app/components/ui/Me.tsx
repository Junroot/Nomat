import { useEffect, useRef, useState } from "react";
import UserIcon from "~/assets/user.svg?react";
import "./Me.css"
import MeStore from "~/stores/MeStore";
import { fetchMe } from "~/utils/api";
import { getRegistrationCode } from "~/utils/registrationCode";

interface MeProperties {
    compact?: boolean;
}

export default function Me({ compact = false }: MeProperties) {
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
    const defaultWidth = 56;
    const [width, setWidth] = useState(defaultWidth);
    const [isHover, setHover] = useState(false);

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

    if (compact) {
        return (
            <div className="size-[32px] flex items-center justify-center rounded-full bg-zinc-800">
                <UserIcon className="size-[20px]" />
            </div>
        );
    }

    return <>
        <div className="me size-[72px] p-[8px]">
            <div
                className={`h-[56px] flex flex-row items-center align-middle profile transition-all transform rounded-full ${isHover ? "drop-shadow-lg" : ""}`}
                onMouseEnter={ () => setHover(true) }
                onMouseLeave={ () => setHover(false) }
                style={{ width: `${width}px` }}
            >
                <div className={`grow-0 shrink-0 w-[56px] h-[56px] p-[8px] flex`}>
                    <UserIcon className="size-[40px] m-auto"></UserIcon>
                </div>
                <div ref={nicknameRef} className={`grow-0 shrink-0 pr-[36px] my-auto text-3xl align-top nickname`}>
                    <p>{me?.nickname}<span className="text-lg ml-2 text-zinc-400">#{getRegistrationCode(me?.registrationType)}</span></p>
                </div>
            </div>
        </div>
    </>;
}
