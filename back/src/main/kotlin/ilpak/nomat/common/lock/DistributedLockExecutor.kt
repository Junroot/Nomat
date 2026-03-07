package ilpak.nomat.common.lock

interface DistributedLockExecutor {

    fun <T> withLock(key: String, action: () -> T): T
}
