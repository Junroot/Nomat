package ilpak.nomat.room.controller

import com.fasterxml.jackson.databind.ObjectMapper
import ilpak.nomat.room.dto.PlaylistResponse
import ilpak.nomat.room.dto.RoomResponse
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
	private val objectMapper: ObjectMapper,
	@Autowired
	private val mockMvc: MockMvc
) {

	@Test
	fun getRooms() {
		val expected = (1..40).map {
			RoomResponse(
				id = it.toLong(),
				title = "들어오셈",
				playlist = PlaylistResponse(
					1L,
					"오늘의 TOP 100: 일본",
					100
				),
				master = "ROOT#3465"
			)
		}

		mockMvc.get("/rooms")
			.andExpect {
				status { isOk() }
				content {
					json(objectMapper.writeValueAsString(expected))
				}
			}
	}
}
