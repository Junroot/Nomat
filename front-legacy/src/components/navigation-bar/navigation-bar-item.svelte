<script lang="ts">
  import type { ComponentType } from "svelte";

  export let iconComponent: ComponentType;
  export let label: string;
  export let onClick: (() => void) | null | undefined;
</script>

<button
  class="w-full my-1 text-center justify-center cursor-pointer"
  on:click={() => {
    if (onClick) {
      onClick();
    }
  }}
>
  <div class="w-full h-24 flex flex-col">
    <div class="w-full h-16 flex-none">
      <svelte:component this={iconComponent} />
    </div>
    <div class="w-full flex-auto">{label}</div>
  </div>
</button>
