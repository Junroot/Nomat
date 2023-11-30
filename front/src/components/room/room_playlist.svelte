<script lang="ts">
    var title = "오늘의 TOP 100: 일본";
    var maker = "ROOT#3465";
    var comment = "오늘의 일본 인기곡 Top 100으로 구성된 맵입니다. 재미있게 즐겨 주세요!";
</script>
<div class="card block p-4 room_playlist">
    <div class="space-y-0.5">
        <div class="flex">
            <div class="music_note px-4"></div>
            <h3 class="h2">{title}</h3>
        </div>
        <div class="flex space-x-0.4">
            <div class="playlist_maker px-4"></div>
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
    .music_note {
        background-color: #e0e3e2;
        display: inline-block;
        -webkit-mask: url("/icons/music_note.svg") no-repeat center;
        mask: url("/icons/music_note.svg") no-repeat center;
    }
    .playlist_maker {
        background-color: #e0e3e2;
        display: inline-block;
        -webkit-mask: url("/icons/playlist_maker.svg") no-repeat center;
        mask: url("/icons/playlist_maker.svg") no-repeat center;
    }
</style>