import { useEffect, useState } from "react";

interface Breakpoint {
    isMobile: boolean;
    isTablet: boolean;
    isDesktop: boolean;
}

const MOBILE_QUERY = "(max-width: 767px)";
const TABLET_QUERY = "(min-width: 768px) and (max-width: 1023px)";
const DESKTOP_QUERY = "(min-width: 1024px)";

export default function useBreakpoint(): Breakpoint {
    const [breakpoint, setBreakpoint] = useState<Breakpoint>(() => ({
        isMobile: window.matchMedia(MOBILE_QUERY).matches,
        isTablet: window.matchMedia(TABLET_QUERY).matches,
        isDesktop: window.matchMedia(DESKTOP_QUERY).matches,
    }));

    useEffect(() => {
        const mobileQuery = window.matchMedia(MOBILE_QUERY);
        const tabletQuery = window.matchMedia(TABLET_QUERY);
        const desktopQuery = window.matchMedia(DESKTOP_QUERY);

        function update() {
            setBreakpoint({
                isMobile: mobileQuery.matches,
                isTablet: tabletQuery.matches,
                isDesktop: desktopQuery.matches,
            });
        }

        mobileQuery.addEventListener("change", update);
        tabletQuery.addEventListener("change", update);
        desktopQuery.addEventListener("change", update);

        return () => {
            mobileQuery.removeEventListener("change", update);
            tabletQuery.removeEventListener("change", update);
            desktopQuery.removeEventListener("change", update);
        };
    }, []);

    return breakpoint;
}
