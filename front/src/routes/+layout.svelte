<script>
  import "../app.pcss";
  import NavigationBar from "../components/navigation-bar/navigation-bar.svelte";
</script>

<div class="w-screen h-screen flex flex-row layout-container">
  <NavigationBar></NavigationBar>
  <slot />
</div>

<style>
  .layout-container {
    background-color: var(--background-color);
    color: var(--font-light);
  }
</style>
