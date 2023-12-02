<script lang="ts">
  import MusicNote from "../../icons/music-note.svelte";
  import PlaylistMaker from "../../icons/playlist-maker.svelte";
  var title = "오늘의 TOP 100: 일본";
  var maker = "ROOT#3465";
  var comment =
    "오늘의 일본 인기곡 Top 100으로 구성된 맵입니다. 재미있게 즐겨 주세요!";
</script>

<div class="card block p-4 room_playlist">
  <div class="space-y-0.5">
    <div class="flex">
      <div class="w-8">
        <MusicNote></MusicNote>
      </div>
      <h3 class="h2">{title}</h3>
    </div>
    <div class="flex space-x-1">
      <div class="w-7">
        <PlaylistMaker></PlaylistMaker>
      </div>
      <h6 class="h3">{maker}</h6>
    </div>
  </div>
  <div class="pt-4">
    <h6 class="h5">{comment}</h6>
  </div>
</div>

<style>
  .room_playlist {
    background-color: #004e61;
    color: #e0e3e2;
  }
</style>
