package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import java.io.FileInputStream
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.apache.commons.codec.digest.DigestUtils
import org.kryspetrie.fileimport.domain.model.FolderIndex
import org.kryspetrie.fileimport.domain.model.HashCacheEntry
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.model.IndexProgress
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.HashCachePort
import org.kryspetrie.fileimport.domain.port.TimeProvider

private const val BATCH_SIZE = 500

class HashCacheAdapter(
    private val dispatcherProvider: DispatcherProvider,
    private val timeProvider: TimeProvider,
) : HashCachePort {

    private val indexDir: File by lazy {
        File(System.getProperty("user.home"), ".petriefi/index").also { it.mkdirs() }
    }

    private val hashConcurrency = (Runtime.getRuntime().availableProcessors()).coerceIn(2, 8)

    private fun dbFileFor(folderPath: String): File {
        val hash = DigestUtils.md5Hex(folderPath.toByteArray())
        return File(indexDir, "$hash.db")
    }

    private fun openDb(folderPath: String): Connection {
        val dbFile = dbFileFor(folderPath)
        val conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        conn.createStatement().use { stmt ->
            stmt.execute("PRAGMA journal_mode=WAL")
            stmt.execute("PRAGMA synchronous=NORMAL")
            stmt.execute("PRAGMA cache_size=-32000") // 32MB cache
            stmt.execute("PRAGMA temp_store=MEMORY")
            stmt.execute(
                """
        CREATE TABLE IF NOT EXISTS file_index (
          path TEXT PRIMARY KEY,
          hash TEXT NOT NULL DEFAULT '',
          file_size INTEGER NOT NULL,
          last_modified INTEGER NOT NULL
        )"""
            )
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_hash ON file_index(hash)")
        }
        return conn
    }

    override suspend fun getIndex(folderPath: String): FolderIndex? =
        withContext(dispatcherProvider.io) {
            val dbFile = dbFileFor(folderPath)
            if (!dbFile.exists()) return@withContext null
            val entries = mutableMapOf<String, HashCacheEntry>()
            openDb(folderPath).use { conn ->
                conn
                    .prepareStatement("SELECT path, hash, file_size, last_modified FROM file_index")
                    .use { stmt ->
                        val rs = stmt.executeQuery()
                        while (rs.next()) {
                            entries[rs.getString(1)] =
                                HashCacheEntry(
                                    hash = rs.getString(2),
                                    fileSize = rs.getLong(3),
                                    lastModified = rs.getLong(4),
                                )
                        }
                    }
            }
            FolderIndex(
                folderPath = folderPath,
                entries = entries,
                lastScanTime = dbFile.lastModified(),
            )
        }

    override suspend fun saveIndex(index: FolderIndex) =
        withContext(dispatcherProvider.io) {
            openDb(index.folderPath).use { conn ->
                conn.autoCommit = false
                conn
                    .prepareStatement(
                        "INSERT OR REPLACE INTO file_index (path, hash, file_size, last_modified) VALUES (?, ?, ?, ?)"
                    )
                    .use { stmt ->
                        var batch = 0
                        for ((path, entry) in index.entries) {
                            stmt.setString(1, path)
                            stmt.setString(2, entry.hash)
                            stmt.setLong(3, entry.fileSize)
                            stmt.setLong(4, entry.lastModified)
                            stmt.addBatch()
                            if (++batch % BATCH_SIZE == 0) stmt.executeBatch()
                        }
                        stmt.executeBatch()
                    }
                conn.commit()
            }
        }

    override suspend fun clearIndex(folderPath: String) =
        withContext(dispatcherProvider.io) {
            dbFileFor(folderPath).delete()
            // WAL and SHM companion files
            File(dbFileFor(folderPath).absolutePath + "-wal").delete()
            File(dbFileFor(folderPath).absolutePath + "-shm").delete()
            Unit
        }

    override suspend fun clearAllIndexes() =
        withContext(dispatcherProvider.io) {
            val allowedExtensions = setOf(".db", ".db-wal", ".db-shm")
            indexDir.listFiles()?.forEach { file ->
                if (
                    allowedExtensions.any { file.name.endsWith(it) } &&
                        file.isFile &&
                        !java.nio.file.Files.isSymbolicLink(file.toPath())
                ) {
                    file.delete()
                }
            }
            Unit
        }

    override suspend fun indexFolder(
        folderPath: String,
        recursive: Boolean,
        onProgress: (IndexProgress) -> Unit,
    ): FolderIndex =
        withContext(dispatcherProvider.io) {
            val folder = File(folderPath)
            if (!folder.exists() || !folder.isDirectory) {
                return@withContext FolderIndex(folderPath = folderPath)
            }

            // Stream file discovery — don't materialize entire tree
            val imageFiles =
                if (recursive) {
                    folder
                        .walkTopDown()
                        .filter { it.isFile && ImageFileType.isSupported(it.extension) }
                        .toList()
                } else {
                    folder.listFiles()?.filter {
                        it.isFile && ImageFileType.isSupported(it.extension)
                    } ?: emptyList()
                }

            val total = imageFiles.size
            if (total == 0) {
                onProgress(IndexProgress(indexed = 0, total = 0, isComplete = true))
                return@withContext FolderIndex(folderPath = folderPath)
            }

            openDb(folderPath).use { conn ->
                // Load existing entries into a lookup map for fast comparison
                val existing = mutableMapOf<String, HashCacheEntry>()
                conn
                    .prepareStatement("SELECT path, hash, file_size, last_modified FROM file_index")
                    .use { stmt ->
                        val rs = stmt.executeQuery()
                        while (rs.next()) {
                            existing[rs.getString(1)] =
                                HashCacheEntry(
                                    hash = rs.getString(2),
                                    fileSize = rs.getLong(3),
                                    lastModified = rs.getLong(4),
                                )
                        }
                    }

                // Partition into cached-ok vs needs-rehash
                val needsHash = mutableListOf<File>()
                val upToDate = mutableListOf<Pair<String, HashCacheEntry>>()

                for (file in imageFiles) {
                    val absPath = file.absolutePath
                    val cached = existing[absPath]
                    if (
                        cached != null &&
                            cached.fileSize == file.length() &&
                            cached.lastModified == file.lastModified()
                    ) {
                        upToDate.add(absPath to cached)
                    } else {
                        needsHash.add(file)
                    }
                }

                onProgress(
                    IndexProgress(
                        indexed = upToDate.size,
                        total = total,
                        currentFile = "Hashing ${needsHash.size} new/changed files...",
                        isComplete = false,
                    )
                )

                // Hash new/changed files with parallelism
                val semaphore = Semaphore(hashConcurrency)
                val hashResults = ConcurrentHashMap<String, HashCacheEntry>()
                val counter = AtomicInteger(0)

                coroutineScope {
                    needsHash
                        .map { file ->
                            async(dispatcherProvider.io) {
                                semaphore.withPermit {
                                    val hash =
                                        try {
                                            FileInputStream(file).buffered(65536).use {
                                                DigestUtils.md5Hex(it)
                                            }
                                        } catch (_: Exception) {
                                            null
                                        }
                                    if (hash != null) {
                                        hashResults[file.absolutePath] =
                                            HashCacheEntry(
                                                hash = hash,
                                                fileSize = file.length(),
                                                lastModified = file.lastModified(),
                                            )
                                    }
                                    val done = counter.incrementAndGet()
                                    if (done % 100 == 0 || done == needsHash.size) {
                                        onProgress(
                                            IndexProgress(
                                                indexed = upToDate.size + done,
                                                total = total,
                                                currentFile = file.name,
                                                isComplete = false,
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        .awaitAll()
                }

                // Batch write new entries to SQLite
                conn.autoCommit = false
                conn
                    .prepareStatement(
                        "INSERT OR REPLACE INTO file_index (path, hash, file_size, last_modified) VALUES (?, ?, ?, ?)"
                    )
                    .use { stmt ->
                        var batch = 0
                        for ((path, entry) in hashResults) {
                            stmt.setString(1, path)
                            stmt.setString(2, entry.hash)
                            stmt.setLong(3, entry.fileSize)
                            stmt.setLong(4, entry.lastModified)
                            stmt.addBatch()
                            if (++batch % BATCH_SIZE == 0) stmt.executeBatch()
                        }
                        stmt.executeBatch()
                    }
                conn.commit()

                // Prune entries for files that no longer exist
                val allPaths = imageFiles.map { it.absolutePath }.toSet()
                val toRemove = existing.keys.filter { it !in allPaths }
                if (toRemove.isNotEmpty()) {
                    conn.prepareStatement("DELETE FROM file_index WHERE path = ?").use { stmt ->
                        for (path in toRemove) {
                            stmt.setString(1, path)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                    conn.commit()
                }

                val allEntries = mutableMapOf<String, HashCacheEntry>()
                upToDate.forEach { (path, entry) -> allEntries[path] = entry }
                allEntries.putAll(hashResults)

                onProgress(IndexProgress(indexed = total, total = total, isComplete = true))
                FolderIndex(
                    folderPath = folderPath,
                    entries = allEntries,
                    lastScanTime = timeProvider.currentTimeMillis(),
                )
            }
        }

    @Suppress("NestedBlockDepth")
    override fun getDestinationHashes(folderPath: String): Set<String> {
        val dbFile = dbFileFor(folderPath)
        if (!dbFile.exists()) return emptySet()
        val hashes = mutableSetOf<String>()
        try {
            openDb(folderPath).use { conn ->
                conn
                    .prepareStatement("SELECT DISTINCT hash FROM file_index WHERE hash != ''")
                    .use { stmt ->
                        val rs = stmt.executeQuery()
                        while (rs.next()) hashes.add(rs.getString(1))
                    }
            }
        } catch (_: Exception) {}
        return hashes
    }
}
