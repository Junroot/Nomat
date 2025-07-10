package ilpak.nomat.common

import jakarta.persistence.Embeddable
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import java.time.LocalDateTime

@Embeddable
data class AuditDateMetadata(
	@CreatedDate
	var createdDate: LocalDateTime? = null,
	@LastModifiedDate
	var modifiedDate: LocalDateTime? = null,
)
