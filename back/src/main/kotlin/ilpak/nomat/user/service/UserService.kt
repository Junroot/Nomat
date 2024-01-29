package ilpak.nomat.user.service

import ilpak.nomat.user.dto.UserIdAndNickname
import org.springframework.stereotype.Service

@Service
class UserService {

	fun getUserNames(ids: Set<Long>): List<UserIdAndNickname> {
		return ids.map { UserIdAndNickname(it, "ROOT#3465") }
	}
}
