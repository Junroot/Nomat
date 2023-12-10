<script lang="ts">
  import { Avatar } from "@skeletonlabs/skeleton";
  import CrownIcon from "../../../icons/crown-icon.svelte";

  export let photoUrl: string;
  export let nickname: string;
  export let isMaster = false;

  let isHover = false;
  $: crownColor = isHover ? "--font-dark" : "--font-light";
</script>

<button
  class="flex flex-row items-center cursor-pointer player px-2"
  on:mouseenter={() => {
    isHover = true;
  }}
  on:mouseleave={() => {
    isHover = false;
  }}
>
  <Avatar src={photoUrl} width="w-12" class="flex-none shrink-0"></Avatar>
  <div class="p-1 text-ellipsis overflow-hidden align-middle flex gap-1">
    <span>{nickname}</span>
    {#if isMaster}
      <span class="w-4 flex-none inline-block crown">
        <CrownIcon color={crownColor}></CrownIcon>
      </span>
    {/if}
  </div>
</button>

<style>
  .player:hover {
    background-color: var(--primary-300);
    color: var(--font-dark);
  }
</style>
