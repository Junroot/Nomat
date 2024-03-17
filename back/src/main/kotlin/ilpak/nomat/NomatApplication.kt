package ilpak.nomat

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NomatApplication

fun main(args: Array<String>) {
    runApplication<NomatApplication>(*args)
}
