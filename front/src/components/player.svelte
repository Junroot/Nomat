<script>
    import { Avatar } from '@skeletonlabs/skeleton';

    export let photoUrl = "";
    export let nickname = "";
    export let isMaster = false;
</script>

<div class="flex flex-row items-center p-1 cursor-pointer player">
    <Avatar 
        src={photoUrl}
        width="w-12"
        class="p-1 flex-none shrink-0">
    </Avatar>
    <div class="p-1 text-ellipsis overflow-hidden nickname">
        <span>{nickname}</span>
        {#if isMaster}
            <div class="crown p-1.5"></div>
        {/if}
    </div>
</div>

<style>
    .crown {
        background-color: white;
        display: inline-block;
        -webkit-mask: url("/icons/crown.svg") no-repeat center;
        mask: url("/icons/crown.svg") no-repeat center;
    }
    .player:hover {
        background-color: #4ddad6;
    }
</style>