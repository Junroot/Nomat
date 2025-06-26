export function getUrlByEmbedId(embedId: string) {
    return `https://www.youtube.com/watch?v=${embedId}`
}

export function getEmbedIdByUrl(url: string) {
    try {
        const parsedUrl = new URL(url)
        return parsedUrl.searchParams.get("v")
    } catch (e) {
        return null
    }   
}
