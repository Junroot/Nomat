<script lang="ts">
  export let contents: string[];
</script>

{#each contents as content}
  <p style="font-weight: bold">{content}</p>
{/each}
