package ilpak.nomat.health.controller

import ilpak.nomat.health.dto.HealthResponse
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
