import type { ReactElement, ReactNode } from "react";
import React from "react";

interface NavigaitonItemProperties {
    icon: ReactNode;
    title: String;
    clicked?: Boolean;
}

export default function NavigationItem({
    icon,
    title,
    clicked = false,
}: NavigaitonItemProperties) {
    return <div className={`w-[96px] flex flex-col items-center ${clicked ? "text-zinc-600" : "cursor-pointer"}`}>
        {React.isValidElement<{ className?: string }>(icon) &&
        React.cloneElement(icon, {
          className: `size-[56px] ${clicked ? "fill-zinc-600" : ""}`,
        })}
        <p>{title}</p>
    </div>
}