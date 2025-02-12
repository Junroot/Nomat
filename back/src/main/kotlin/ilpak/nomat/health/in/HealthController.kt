package ilpak.nomat.health.`in`

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/health")
class HealthController {

    @GetMapping
    fun get(): HealthResponse {
        return HealthResponse("ok")
    }
}

data class HealthResponse(
    val status: String
)
