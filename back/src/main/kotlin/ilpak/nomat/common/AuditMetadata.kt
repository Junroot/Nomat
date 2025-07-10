package ilpak.nomat.common

import jakarta.persistence.Embeddable
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import java.time.LocalDateTime

@Embeddable
data class AuditMetadata(
	@CreatedBy
	var createdBy: Long? = null,
	@CreatedDate
	var createdDate: LocalDateTime? = null,
	@LastModifiedBy
	var modifiedBy: Long? = null,
	@LastModifiedDate
	var modifiedDate: LocalDateTime? = null,
)
