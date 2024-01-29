package ilpak.nomat.room.controller

import com.fasterxml.jackson.databind.ObjectMapper
import ilpak.nomat.playlist.dto.PlaylistResponse
import ilpak.nomat.room.dto.RoomResponse
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoomControllerTest(
	@Autowired
	private val mockMvc: MockMvc
) {

	@Test
	fun getRooms() {

		mockMvc.get("/rooms")
			.andExpect {
				status { isOk() }
				content {
					jsonPath("$.length()", `is`(40))
					jsonPath("$[0].title", `is`("들어오셈"))
					jsonPath("$[0].playlist.id", `is`(1))
					jsonPath("$[0].playlist.name", `is`("오늘의 TOP 100: 일본"))
					jsonPath("$[0].playlist.count", `is`(100))
					jsonPath("$[0].masterNickname", `is`("ROOT#3465"))
				}
			}
	}
}
