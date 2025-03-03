export default interface MeResponse {
    nickname: string,
    registrationType: string,
    registrationId: string,
    id: number,
}

export function getRegistrationCode(registrationType: string | undefined) {
    if (registrationType === "DISCORD") {
        return "DSCD"
    }
    return ""
}
