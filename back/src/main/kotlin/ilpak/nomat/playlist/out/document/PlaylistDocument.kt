package ilpak.nomat.playlist.out.document

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.TypeAlias
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.Setting

@Document(indexName = "playlist-v2")
@TypeAlias("playlist")
@Setting(settingPath = "/elasticsearch/playlist-settings.json")
data class PlaylistDocument(
    @Field(type = FieldType.Text, analyzer = "korean_analyzer")
    val title: String,
    @Field(type = FieldType.Text, analyzer = "korean_analyzer")
    val description: String,
    @Id
    val id: Long,
)
