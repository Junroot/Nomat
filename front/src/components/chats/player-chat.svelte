<script lang="ts">
  import { Avatar } from "@skeletonlabs/skeleton";

  export let photoUrl: string;
  export let nickname: string;
  export let contents: string[];
</script>

<div class="flex flex-row p-1">
  <Avatar src={photoUrl} width="w-12" class="flex-none shrink-0 h-12"></Avatar>
  <div class="flex flex-col px-2 text-ellipsis overflow-hidden nickname">
    <div style="font-weight: bold">{nickname}</div>
    {#each contents as content}
      <div>{content}</div>
    {/each}
  </div>
</div>
