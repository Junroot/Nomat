<script>
  import SearchIcon from "../../icons/search-icon.svelte";
</script>

<div class="input-group input-group-divider grid-cols-[auto_1fr_auto]">
  <div class="w-10 pr-2">
    <SearchIcon></SearchIcon>
  </div>
  <input
    class="input"
    type="text"
    placeholder="방 이름, 플레이리스트, 플레이어 이름으로 검색"
  />
</div>

<style>
  .input-group {
    background-color: var(--tertiary-100);
    border: none;
  }
  .input-group div {
    padding-right: 0;
  }
  .input {
    border: none;
  }
</style>
