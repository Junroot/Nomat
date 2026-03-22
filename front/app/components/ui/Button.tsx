import { type ButtonHTMLAttributes } from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "sm" | "md" | "lg";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
    variant?: Variant;
    size?: Size;
    loading?: boolean;
    fullWidth?: boolean;
}

const variantStyles: Record<Variant, string> = {
    primary:
        "bg-cyan-500/20 text-cyan-400 border border-cyan-500/30 shadow-glow-cyan hover:bg-cyan-500/30 hover:shadow-glow-cyan-lg",
    secondary:
        "bg-transparent text-zinc-300 border border-zinc-600 hover:border-zinc-400 hover:text-zinc-100",
    ghost:
        "bg-transparent text-zinc-400 border border-transparent hover:bg-zinc-800 hover:text-zinc-200",
    danger:
        "bg-red-500/20 text-red-400 border border-red-500/30 hover:bg-red-500/30",
};

const sizeStyles: Record<Size, string> = {
    sm: "px-3 py-1.5 text-xs rounded-lg",
    md: "px-4 py-2 text-sm rounded-xl",
    lg: "px-6 py-3 text-base rounded-xl",
};

export default function Button({
    variant = "primary",
    size = "md",
    loading = false,
    fullWidth = false,
    disabled,
    className = "",
    children,
    ...rest
}: ButtonProps) {
    const isDisabled = disabled || loading;

    return (
        <button
            disabled={isDisabled}
            className={`
                inline-flex items-center justify-center font-medium
                transition-all duration-200 cursor-pointer
                ${variantStyles[variant]}
                ${sizeStyles[size]}
                ${fullWidth ? "w-full" : ""}
                ${isDisabled ? "opacity-50 cursor-not-allowed pointer-events-none" : ""}
                ${className}
            `}
            {...rest}
        >
            {loading && (
                <span className="mr-2 inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
            )}
            {children}
        </button>
    );
}
