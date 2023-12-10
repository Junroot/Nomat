<script lang="ts">
  import UserIcon from "../../icons/user-icon.svelte";

  export let nickname: string = "ROOT#3465";
  let isHover = false;
</script>

<button
  class="w-20 h-20 overflow-visible"
  on:mouseenter={() => {
    isHover = true;
  }}
  on:mouseleave={() => {
    isHover = false;
  }}
>
  <div
    class="h-20 flex gap-2 items-center rounded-full profile"
    class:shadow-2xl={isHover}
  >
    <div class="flex-none w-20">
      <div class="w-16 m-auto">
        <UserIcon></UserIcon>
      </div>
    </div>
    {#if isHover}
      <p class="flex-none pr-8 h2 nickname">{nickname}</p>
    {/if}
  </div>
</button>

<style>
  button {
    position: relative;
  }

  .profile {
    position: absolute;
    top: 0;
    transition: all 0.3s;
  }

  .profile:hover {
    background-color: var(--tertiary-100);
  }

  .nickname {
    visibility: hidden;
    opacity: 0;
    transition:
      visibility 0.3s,
      opacity 0.3s linear;
  }

  .profile:hover .nickname {
    visibility: visible;
    opacity: 1;
  }
</style>
