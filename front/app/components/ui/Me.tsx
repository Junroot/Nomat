import { useEffect, useRef, useState } from "react";
import UserIcon from "~/assets/user.svg?react";
import "./Me.css"
import MeStore from "~/stores/MeStore";
import { fetchMe } from "~/utils/api";
import { getRegistrationCode } from "~/utils/MeResponse";


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
    var [width, setWidth] = useState(defaultWidth);
    var [isHover, setHover] = useState(false);

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

    return <div className="me size-[96px] p-[8px]">
        <div 
            className={`h-[80px] flex flex-row cursor-pointer items-center align-middle profile transition-all transform rounded-full ${isHover ? "drop-shadow-lg" : ""}`}
            onMouseEnter={ () => setHover(true) }
            onMouseLeave={ () => setHover(false) }
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
    
}