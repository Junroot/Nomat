import type { PropsWithChildren } from "react";
import Me from "~/components/ui/Me";
import VolumeControl from "~/components/ui/VolumeControl";

/**
 * 데스크톱 좌측 네비게이션 레일(72px, `hidden md:flex`). 상단은 `children`(셸이 채우는 탭 또는
 * 뒤로가기·액션), 하단 슬롯(볼륨 컨트롤 + 사용자 정보)은 **이 컴포넌트가 직접 소유한다** —
 * 셸마다 같은 하단을 반복해 그리지 않도록. 하단 슬롯은 어느 화면에서든 같은 자리에 있다.
 */
export default function NavigationBar({ children }: PropsWithChildren) {
    return (
        <div className="h-full w-[72px] py-[16px] hidden md:flex flex-col items-center gap-[8px]">
            <div className="grow shrink flex flex-col gap-4">{children}</div>
            <div className="grow-0 shrink-0 flex flex-col items-center gap-2">
                <VolumeControl />
                <Me />
            </div>
        </div>
    );
}
