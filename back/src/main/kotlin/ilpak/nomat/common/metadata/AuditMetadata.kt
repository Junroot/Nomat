package ilpak.nomat.common.metadata

import jakarta.persistence.Embeddable
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import java.time.LocalDateTime

@Embeddable
data class AuditMetadata(
    @CreatedBy
    var createdBy: Long = 0L,
    @CreatedDate
    var createdDate: LocalDateTime = LocalDateTime.MIN,
    @LastModifiedBy
    var modifiedBy: Long = 0L,
    @LastModifiedDate
    var modifiedDate: LocalDateTime = LocalDateTime.MIN,
)
