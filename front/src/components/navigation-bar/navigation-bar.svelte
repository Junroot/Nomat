<script lang="ts">
  import type { NavigationItemData } from "./navigation-item";
  import type { ComponentType } from "svelte";

  import NavigationBarItem from "./navigation-bar-item.svelte";

  export let navigationItems: NavigationItemData[];
  export let footer: ComponentType | null = null;
</script>

<div class="w-28 py-4 h-full flex flex-col items-center">
  <div class="grow">
    {#each navigationItems as navigationItem}
      <div class="w-20">
        <NavigationBarItem
          iconComponent={navigationItem.iconComponent}
          label={navigationItem.label}
          onClick={navigationItem.onClick}
        ></NavigationBarItem>
      </div>
    {/each}
  </div>
  <div class="flex-none w-20">
    {#if footer != null}
      <svelte:component this={footer} />
    {/if}
  </div>
</div>

<style>
</style>
