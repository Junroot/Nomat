<script lang="ts">
  import MusicNoteIcon from "../../icons/music-note-icon.svelte";
  import PlaylistMakerIcon from "../../icons/playlist-maker-icon.svelte";
  var title = "오늘의 TOP 100: 일본";
  var maker = "ROOT#3465";
  var comment =
    "오늘의 일본 인기곡 Top 100으로 구성된 맵입니다. 재미있게 즐겨 주세요!";
</script>

<div class="card block p-1 room_playlist">
  <div class="space-y-0.5 p-2">
    <div class="flex mb-2">
      <div class="flex-none w-8">
        <MusicNoteIcon></MusicNoteIcon>
      </div>
      <h3 class="h2">{title}</h3>
    </div>
    <div class="flex space-x-1 mb-2">
      <div class="flex-none w-7">
        <PlaylistMakerIcon></PlaylistMakerIcon>
      </div>
      <h6 class="h3">{maker}</h6>
    </div>
  </div>
  <div class="px-2">
    <h6 class="h5">{comment}</h6>
  </div>
</div>

<style>
  .room_playlist {
    background-color: #004e61;
    color: #e0e3e2;
  }
</style>
