export function getRegistrationCode(registrationType: string | undefined) {
    if (registrationType === "DISCORD") {
        return "DSCD"
    }
    return ""
}
