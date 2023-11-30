<script>
  import ArrowBack from "../../icons/arrow-back.svelte";
  import Play from "../../icons/play.svelte";
  import NavigationBarItem from "./navigation-bar-item.svelte";
</script>

<div class="flex-none w-24 py-4 h-full flex flex-col">
  <NavigationBarItem iconComponent={ArrowBack} label="대기실"
  ></NavigationBarItem>
  <NavigationBarItem iconComponent={Play} label="시작"></NavigationBarItem>
</div>

<style>
</style>
