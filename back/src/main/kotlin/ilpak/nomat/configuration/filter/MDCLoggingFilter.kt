package ilpak.nomat.configuration.filter

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.*

@Component
class MDCLoggingFilter : Filter {

    override fun doFilter(p0: ServletRequest?, p1: ServletResponse?, p2: FilterChain?) {
        MDC.put(REQUEST_ID, UUID.randomUUID().toString())
        p2?.doFilter(p0, p1)
        MDC.clear()
    }

    companion object {
        private const val REQUEST_ID = "requestId"
    }
}
