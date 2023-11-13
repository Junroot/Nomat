<script>
    import Players from "../components/players.svelte";
</script>

<div class="container mx-auto p-4">
  <Players></Players>
</div>
<h1>Welcome to SvelteKit</h1>
<p>Visit <a href="https://kit.svelte.dev">kit.svelte.dev</a> to read the documentation</p>
<div class="previewer-viewport p-4 md:p-10 space-y-4 bg-gradient-to-br variant-gradient-primary-secondary ">
  <div class="previewer-preview flex justify-center items-center mx-auto transition-[width] duration-200 w-full ">
    <div class="w-full text-token grid grid-cols-1 md:grid-cols-2 gap-4">
      <div class="card bg-initial p-4 flex justify-center items-center">
        <span>Minimal</span>
      </div>
      <a class="card bg-initial card-hover overflow-hidden" href="/.">
        <header>
          <img src="https://images.unsplash.com/photo-1620562303747-ba21ca6f1530?ixid=M3w0Njc5ODF8MHwxfGFsbHx8fHx8fHx8fDE2ODc5NzY2MjR8&amp;ixlib=rb-4.0.3&amp;w=400&amp;h=175&amp;auto=format&amp;fit=crop" class="bg-black/50 w-full aspect-[21/9]" alt="Post">
        </header>
        <div class="p-4 space-y-4">
          <h6 class="h6" data-toc-ignore="">Announcements</h6>
          <h3 class="h3" data-toc-ignore="">Skeleton is Awesome!</h3>
          <article>
            <p>Lorem ipsum dolor sit amet consectetur adipisicing elit. Numquam aspernatur provident eveniet eligendi cumque consequatur tempore sint nisi sapiente. Iste beatae laboriosam iure molestias cum expedita architecto itaque quae rem.</p>
          </article>
        </div>
        <hr class="opacity-50">
        <footer class="p-4 flex justify-start items-center space-x-4">
          <figure class="avatar flex aspect-square text-surface-50 font-semibold justify-center items-center overflow-hidden isolate bg-surface-400-500-token w-8  rounded-full   " data-testid="avatar">
            <img class="avatar-image w-full h-full object-cover" src="https://images.unsplash.com/photo-1617296538902-887900d9b592?ixid=M3w0Njc5ODF8MHwxfGFsbHx8fHx8fHx8fDE2ODc5NzExMDB8&amp;ixlib=rb-4.0.3&amp;w=32&amp;h=32&amp;auto=format&amp;fit=crop" alt="" style="">
          </figure>
          <div class="flex-auto flex justify-between items-center">
            <h6 class="font-bold" data-toc-ignore="">By Alex</h6>
            <small>On 2023. 11. 9.</small>
          </div>
        </footer>
      </a>
    </div>
  </div>
</div>