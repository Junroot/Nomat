<script lang="ts">
  import { Avatar } from "@skeletonlabs/skeleton";

  export let photoUrl: string;
  export let nickname: string;
  export let isMaster = false;
</script>

<div class="flex flex-row items-center cursor-pointer player px-1">
  <Avatar src={photoUrl} width="w-12" class="flex-none shrink-0"></Avatar>
  <div class="p-1 text-ellipsis overflow-hidden nickname">
    <span>{nickname}</span>
    {#if isMaster}
      <div class="crown p-1.5"></div>
    {/if}
  </div>
</div>

<style>
  .crown {
    background-color: #e0e3e2;
    display: inline-block;
    -webkit-mask: url("/icons/crown.svg") no-repeat center;
    mask: url("/icons/crown.svg") no-repeat center;
  }
  .player:hover {
    background-color: #4ddad6;
    color: #1c2626;
  }
  .player:hover .crown {
    background-color: #1c2626;
  }
</style>
