package com.shinjikai.dictionary.data

import android.content.Context
import android.util.Log
import com.shinjikai.dictionary.R
import java.io.File
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.Collections
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

data class BundledDictionaryInstallResult(
    val available: Boolean,
    val dictionaryImported: Boolean,
    val imagesInstalled: Boolean,
    val importedCount: Int = 0
)

class BundledDictionaryInstaller(
    private val context: Context,
    private val database: AppDatabase,
    private val importer: RawShinjikaiImporter = RawShinjikaiImporter(database)
) {
    fun hasBundledDictionary(): Boolean {
        return findJsonlAssetPaths().isNotEmpty()
    }

    suspend fun installIfNeeded(
        force: Boolean = false,
        onProgress: suspend (phase: String, progress: Float) -> Unit = { _, _ -> }
    ): Result<BundledDictionaryInstallResult> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val jsonlPaths = findJsonlAssetPaths()
                Log.i(TAG, "Bundled dictionary asset chunks found: ${jsonlPaths.size}")
                if (jsonlPaths.isEmpty()) {
                    return@withContext BundledDictionaryInstallResult(
                        available = false,
                        dictionaryImported = false,
                        imagesInstalled = false
                    )
                }

                val dao = database.yomitanDao()
                val existingBundledCount = dao.countTermsBySource(BUNDLED_SOURCE_LABEL)
                var existingBundledFtsCount = runCatching {
                    dao.countFtsTermsBySource(BUNDLED_SOURCE_LABEL)
                }.getOrDefault(0)

                if (!force && existingBundledCount > 0 && existingBundledFtsCount != existingBundledCount) {
                    // The dictionary rows are the durable installed state. If only the derived
                    // search table is incomplete, rebuild it instead of parsing the bundle again.
                    AppDatabase.repairOfflineDictionarySchema(database.openHelper.writableDatabase)
                    existingBundledFtsCount = runCatching {
                        dao.countFtsTermsBySource(BUNDLED_SOURCE_LABEL)
                    }.getOrDefault(0)
                }

                val signature = buildAssetSignature(jsonlPaths, schemaVersion = BUNDLED_DATA_SCHEMA)
                val storedSignature = dao.getMetaValue(META_BUNDLED_SIGNATURE)
                // Null = installed by an older build that predates the signature; keep it.
                val bundleChanged = storedSignature != null && storedSignature != signature

                if (!force && !bundleChanged && existingBundledCount > 0 && existingBundledFtsCount == existingBundledCount) {
                    Log.i(TAG, "Bundled dictionary already installed; skipping import.")
                    return@withContext BundledDictionaryInstallResult(
                        available = true,
                        dictionaryImported = false,
                        imagesInstalled = false
                    )
                }

                val imageArchivePaths = findImageArchiveAssetPaths()
                val installedImages = installBundledImagesIfNeeded(
                    imageArchivePaths = imageArchivePaths,
                    force = force,
                    onProgress = onProgress
                )
                val imageRoot = installedImages?.root ?: findImageAssetRoot()

                onProgress(context.getString(R.string.offline_import_phase_index), 0.08f)
                Log.i(TAG, "Starting bundled dictionary import. imageRoot=$imageRoot")
                val importedCount = importer.importFromJsonLineStreams(
                    sources = jsonlPaths.map { path ->
                        JsonLineInput(path) { openDictionaryAsset(path) }
                    },
                    sourceLabel = BUNDLED_SOURCE_LABEL,
                    offlineImageRoot = imageRoot
                ).getOrThrow()
                Log.i(TAG, "Bundled dictionary import complete. importedCount=$importedCount")

                dao.upsertMeta(YomitanMetaEntity(key = META_BUNDLED_SIGNATURE, value = signature))
                dao.upsertMeta(YomitanMetaEntity(key = META_BUNDLED_SOURCE, value = BUNDLED_SOURCE_LABEL))
                imageRoot?.let { root ->
                    dao.upsertMeta(YomitanMetaEntity(key = OFFLINE_IMAGE_DIR_META_KEY, value = root))
                }
                onProgress(context.getString(R.string.offline_import_phase_ready), 1f)

                BundledDictionaryInstallResult(
                    available = true,
                    dictionaryImported = true,
                    imagesInstalled = installedImages?.installed == true,
                    importedCount = importedCount
                )
            }
        }
    }

    private fun findJsonlAssetPaths(): List<String> {
        val listedPaths = DATA_ASSET_DIRS
            .flatMap { dir ->
                context.assets.list(dir)
                    .orEmpty()
                    .filter(::isSupportedJsonLineAsset)
                    .map { fileName -> "$dir/$fileName" }
            }
            .sortedWith(compareBy<String>({ jsonlChunkIndex(it) }, { it }))

        if (listedPaths.isNotEmpty()) return listedPaths

        return DATA_ASSET_DIRS
            .flatMap { dir ->
                (0..MAX_FALLBACK_CHUNK_INDEX).mapNotNull { index ->
                    val path = "$dir/data_$index.jsonl.xz"
                    if (assetExists(path)) path else null
                }
            }
            .sortedWith(compareBy<String>({ jsonlChunkIndex(it) }, { it }))
    }

    private fun findImageArchiveAssetPaths(): List<String> {
        val chunkPaths = IMAGE_ARCHIVE_ASSET_DIRS
            .firstNotNullOfOrNull { dir ->
                context.assets.list(dir)
                    .orEmpty()
                    .filter { fileName -> IMAGE_ARCHIVE_CHUNK_REGEX.matches(fileName) }
                    .map { fileName -> "$dir/$fileName" }
                    .sortedWith(compareBy<String>({ imageArchiveChunkIndex(it) }, { it }))
                    .takeIf { it.isNotEmpty() }
            }
        if (!chunkPaths.isNullOrEmpty()) return chunkPaths

        return IMAGE_ARCHIVE_ASSET_PATHS
            .filter(::assetExists)
            .sorted()
    }

    private fun assetExists(path: String): Boolean {
        return runCatching {
            context.assets.open(path).use { true }
        }.getOrDefault(false)
    }

    private fun openDictionaryAsset(path: String): InputStream {
        val rawStream = context.assets.open(path).buffered()
        return when {
            path.endsWith(".xz", ignoreCase = true) -> XZCompressorInputStream(rawStream)
            path.endsWith(".gz", ignoreCase = true) -> GZIPInputStream(rawStream)
            else -> rawStream
        }
    }

    private fun isSupportedJsonLineAsset(fileName: String): Boolean {
        return fileName.endsWith(".jsonl", ignoreCase = true) ||
            fileName.endsWith(".jsonl.xz", ignoreCase = true) ||
            fileName.endsWith(".jsonl.gz", ignoreCase = true)
    }

    private fun findImageAssetRoot(): String? {
        return IMAGE_ASSET_DIRS.firstOrNull { dir ->
            context.assets.list(dir).orEmpty().isNotEmpty()
        }?.let { dir -> "file:///android_asset/$dir" }
    }

    private suspend fun installBundledImagesIfNeeded(
        imageArchivePaths: List<String>,
        force: Boolean,
        onProgress: suspend (phase: String, progress: Float) -> Unit
    ): InstalledImageRoot? {
        if (imageArchivePaths.isEmpty()) return null

        val dao = database.yomitanDao()
        val signature = buildAssetSignature(imageArchivePaths)
        val existingSignature = dao.getMetaValue(META_BUNDLED_IMAGE_SIGNATURE)
        val targetRoot = File(context.filesDir, "offline")
        val targetImageDir = File(targetRoot, "yomitan_images")
        val targetRootPath = targetRoot.canonicalPath
        val targetImagePath = targetImageDir.canonicalPath
        require(targetImagePath.startsWith(targetRootPath)) {
            "Refusing to install bundled images outside app storage."
        }

        if (!force && existingSignature == signature && targetImageDir.exists() && targetImageDir.list().orEmpty().isNotEmpty()) {
            return InstalledImageRoot(
                root = targetImageDir.absolutePath.replace('\\', '/'),
                installed = false
            )
        }

        onProgress(context.getString(R.string.offline_import_phase_images), 0.04f)
        val stagingRoot = File(context.filesDir, "offline-image-staging")
        try {
            if (stagingRoot.exists()) {
                stagingRoot.deleteRecursively()
            }
            stagingRoot.mkdirs()
            openBundledImageArchive(imageArchivePaths).use { input ->
                extractTarXzStream(input, stagingRoot)
            }
            val stagingImageDir = File(stagingRoot, "yomitan_images")
            require(stagingImageDir.isDirectory && stagingImageDir.walkTopDown().any(File::isFile)) {
                "Bundled image archive did not contain yomitan_images."
            }
            val mergedStagingImageDir = stageDirectoryMergedCopy(stagingImageDir, targetImageDir)
            stagingImageDir.deleteRecursively()
            replaceStagedDirectoryAtomically(mergedStagingImageDir, targetImageDir)
        } finally {
            if (stagingRoot.exists()) {
                stagingRoot.deleteRecursively()
            }
        }
        dao.upsertMeta(YomitanMetaEntity(key = META_BUNDLED_IMAGE_SIGNATURE, value = signature))
        return InstalledImageRoot(
            root = targetImageDir.absolutePath.replace('\\', '/'),
            installed = true
        )
    }

    private fun buildAssetSignature(
        assetPaths: List<String>,
        schemaVersion: Int? = null
    ): String {
        val schemaPrefix = schemaVersion?.let { "schema$it-" }.orEmpty()
        return "$schemaPrefix${assetPaths.joinToString("|") { path -> "$path:${assetByteLength(path)}" }}"
    }

    private fun openBundledImageArchive(assetPaths: List<String>): InputStream {
        val streams = assetPaths.map { path ->
            context.assets.open(path).buffered()
        }
        return SequenceInputStream(Collections.enumeration(streams))
    }

    private fun assetByteLength(path: String): Long {
        return runCatching {
            context.assets.openFd(path).use { descriptor -> descriptor.length }
        }.getOrElse {
            context.assets.open(path).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                }
                total
            }
        }
    }

    private fun jsonlChunkIndex(path: String): Int {
        return DATA_CHUNK_REGEX
            .find(path)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: Int.MAX_VALUE
    }

    private fun imageArchiveChunkIndex(path: String): Int {
        return IMAGE_ARCHIVE_CHUNK_REGEX
            .find(path.substringAfterLast('/'))
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: Int.MAX_VALUE
    }

    companion object {
        private const val TAG = "BundledDictionary"
        private const val BUNDLED_DATA_SCHEMA = 2
        const val BUNDLED_SOURCE_LABEL = "bundled-1selxo-shinjikai-jsonl"

        private const val MAX_FALLBACK_CHUNK_INDEX = 99
        private const val META_BUNDLED_SIGNATURE = "bundled_dictionary_signature"
        private const val META_BUNDLED_IMAGE_SIGNATURE = "bundled_dictionary_image_signature"
        private const val META_BUNDLED_SOURCE = "bundled_dictionary_source"
        private val DATA_ASSET_DIRS = listOf(
            "bundled_dictionary/shinjikai_data",
            "bundled_dictionary/raw/shinjikai_data"
        )
        private val IMAGE_ASSET_DIRS = listOf(
            "bundled_dictionary/yomitan_images",
            "bundled_dictionary/raw/yomitan_images"
        )
        private val IMAGE_ARCHIVE_ASSET_DIRS = listOf(
            "bundled_dictionary",
            "bundled_dictionary/raw"
        )
        private val IMAGE_ARCHIVE_ASSET_PATHS = listOf(
            "bundled_dictionary/yomitan_images.tar.xz",
            "bundled_dictionary/raw/yomitan_images.tar.xz"
        )
        private val DATA_CHUNK_REGEX =
            Regex("""data_(\d+)\.jsonl(?:\.(?:xz|gz))?$""", RegexOption.IGNORE_CASE)
        private val IMAGE_ARCHIVE_CHUNK_REGEX =
            Regex("""yomitan_images\.part(\d+)\.tar\.xz$""", RegexOption.IGNORE_CASE)
    }
}

private data class InstalledImageRoot(
    val root: String,
    val installed: Boolean
)
