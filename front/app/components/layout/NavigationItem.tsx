import type { ReactNode } from "react";
import React from "react";

interface NavigationItemProperties {
    icon: ReactNode;
    title: String;
    clicked?: Boolean;
    onClick?: () => void;
}

export default function NavigationItem({
    icon,
    title,
    clicked = false,
    onClick = () => {},
}: NavigationItemProperties) {
    return <div onClick={onClick} className={`w-[96px] flex flex-col items-center ${clicked ? "text-zinc-600" : "cursor-pointer"}`}>
        {React.isValidElement<{ className?: string }>(icon) &&
        React.cloneElement(icon, {
          className: `size-[56px] ${clicked ? "fill-zinc-600" : ""}`,
        })}
        <p>{title}</p>
    </div>
}
