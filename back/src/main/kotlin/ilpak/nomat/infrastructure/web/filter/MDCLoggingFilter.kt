package ilpak.nomat.infrastructure.web.filter

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
        val requestId = UUID.randomUUID().toString()
        MDC.put(REQUEST_ID, requestId)
        p0?.setAttribute(REQUEST_ID, requestId)
        p2?.doFilter(p0, p1)
        MDC.clear()
    }

    companion object {
        private const val REQUEST_ID = "requestId"
    }
}
