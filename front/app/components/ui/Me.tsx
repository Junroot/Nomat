import { useEffect, useRef, useState } from "react";
import UserIcon from "~/assets/user.svg?react";
import "./Me.css"

interface MeProperties {
    nickname: string
}

export default function Me(properties: MeProperties) {
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
            className={`h-[80px] m-[8px] flex flex-row cursor-pointer profile transition-all transform rounded-full ${isHover ? "drop-shadow-lg" : ""}`}
            onMouseEnter={ () => setHover(true) }
            onMouseLeave={ () => setHover(false) }
            style={{ width: `${width}px` }}
        >
            <div className={`grow-0 shrink-0 w-[80px] h-[80px] p-[12px] flex`}>
                <UserIcon className="size-[56px] m-auto"></UserIcon>
            </div>
            <div ref={nicknameRef} className={`grow-0 shrink-0 pr-[36px] my-auto text-4xl nickname`}><p>{properties.nickname}</p></div>
        </div>
    </div>
    
}