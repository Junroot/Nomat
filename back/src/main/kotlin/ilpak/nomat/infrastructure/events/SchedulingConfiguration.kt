package ilpak.nomat.infrastructure.events

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/**
 * `@Scheduled` 전용 스케줄러를 **이름 `taskScheduler`로 명시 선언**한다.
 *
 * 이 앱은 `@EnableWebSocketMessageBroker`가 `messageBrokerTaskScheduler`(`TaskScheduler` 빈)를 등록하므로
 * Boot의 `taskScheduler` 자동설정이 `@ConditionalOnMissingBean(TaskScheduler)`로 꺼져 있고, 그동안 `@Scheduled`는
 * 유일한 `TaskScheduler`인 브로커 스케줄러에서 우연히 돌고 있었다. `TaskScheduler` 빈이 하나 더 생기면(WebSocket
 * 하트비트 전용 빈) 타입 해석이 모호해지고, Spring은 이름 `taskScheduler`를 찾다가 없으면 **단일 스레드 로컬 executor로
 * 폴백**한다 — 그러면 유예 sweeper의 락 대기가 라운드 마감 sweeper 틱을 직접 밀어낸다.
 *
 * Boot의 [ThreadPoolTaskSchedulerBuilder]로 만들어 `spring.task.scheduling.*`(풀 크기·스레드명 접두사)가
 * 프로퍼티로 바인딩되게 한다. 다른 `TaskScheduler` 빈은 이 이름을 쓰면 안 된다.
 */
@Configuration
class SchedulingConfiguration {

    @Bean(name = ["taskScheduler"])
    fun taskScheduler(builder: ThreadPoolTaskSchedulerBuilder): ThreadPoolTaskScheduler = builder.build()
}
