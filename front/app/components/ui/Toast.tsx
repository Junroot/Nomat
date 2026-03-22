import { Toaster as SonnerToaster } from "sonner";

export default function Toaster() {
    return (
        <SonnerToaster
            position="bottom-center"
            duration={3000}
            toastOptions={{
                style: {
                    background: "var(--color-surface)",
                    border: "1px solid var(--color-border)",
                    color: "#fafafa",
                },
                classNames: {
                    success: "!border-emerald-500/30",
                    error: "!border-red-500/30",
                    info: "!border-cyan-500/30",
                    warning: "!border-amber-500/30",
                },
            }}
        />
    );
}
