import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.zip.CRC32
import java.util.zip.ZipFile

/** Static display metadata only. Never loads a plugin or rewrites its assets. */
class PetInfo {
    static final int MAX_INFO_BYTES = 32 * 1024
    static final int MAX_PREVIEW_BYTES = 1024 * 1024
    static final Map<String, Integer> TEXT_LIMITS =
        [versionName: 64, author: 128, description: 4096, license: 256]
    static final Set<String> INFO_FIELDS = (TEXT_LIMITS.keySet() + ['preview']) as Set

    static Map fromAuthor(Object value) {
        if (!(value instanceof Map) || !INFO_FIELDS.containsAll(value.keySet())) {
            throw new IllegalArgumentException('info must contain only versionName, author, description, license and preview')
        }
        Map result = [schemaVersion: 1] + value
        validate(result)
        return result
    }

    static Map validate(Object value) {
        if (!(value instanceof Map) ||
            !(value.schemaVersion instanceof Integer) || value.schemaVersion != 1 ||
            !(INFO_FIELDS + ['schemaVersion']).containsAll(value.keySet())) {
            throw new IllegalArgumentException('Invalid pet-info.json schema or fields')
        }
        TEXT_LIMITS.each { key, limit ->
            if (value.containsKey(key)) {
                def text = value[key]
                if (!(text instanceof String) || !text.trim() || text.length() > limit ||
                    text.chars().anyMatch { int c ->
                        Character.isISOControl(c) && !(key == 'description' && c in [9, 10, 13])
                    }) {
                    throw new IllegalArgumentException("Invalid pet info ${key} (maximum ${limit} characters)")
                }
            }
        }
        if (value.containsKey('preview')) validatePreviewPath(value.preview)
        if (encode(value).length > MAX_INFO_BYTES) {
            throw new IllegalArgumentException('pet-info.json exceeds 32 KiB')
        }
        return value
    }

    static byte[] encode(Map info) {
        (JsonOutput.prettyPrint(JsonOutput.toJson(info)) + '\n').getBytes(StandardCharsets.UTF_8)
    }

    static String validatePreviewPath(Object value) {
        if (!(value instanceof String) || !value || value.length() > 240 ||
            value.startsWith('/') || value.contains('\\') || value.contains(':') ||
            value.chars().anyMatch { int c -> Character.isISOControl(c) } ||
            value.split('/', -1).any { it in ['', '.', '..'] } ||
            !value.toLowerCase(Locale.ROOT).endsWith('.png')) {
            throw new IllegalArgumentException('preview must be a relative PNG path inside assets')
        }
        return value
    }

    static byte[] readBounded(InputStream input, int maximum, String label) {
        byte[] bytes = input.readNBytes(maximum + 1)
        if (bytes.length > maximum) throw new IllegalArgumentException("${label} is too large")
        return bytes
    }

    static Map parse(byte[] bytes) {
        if (bytes.length > MAX_INFO_BYTES) throw new IllegalArgumentException('pet-info.json exceeds 32 KiB')
        def decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return validate(new JsonSlurper().parseText(decoder.decode(ByteBuffer.wrap(bytes)).toString()))
    }

    static void validateAssets(File sourceRoot, Map info) {
        def root = sourceRoot.toPath().toRealPath()
        def assets = root.resolve('src/main/assets')
        // Check each ancestor too: Files.walk does not reveal an assets-root symlink.
        def current = root
        ['src', 'main', 'assets'].each {
            current = current.resolve(it)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) checkPlainPath(root, current)
        }
        if (Files.exists(assets, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(assets)) throw new IllegalArgumentException('src/main/assets must be a directory')
            Files.walk(assets).withCloseable { stream ->
                stream.forEach { path ->
                    checkPlainPath(root, path)
                    String name = assets.relativize(path).toString().replace('\\', '/')
                    if (name.equalsIgnoreCase('plugin.json') || name.equalsIgnoreCase('pet-info.json')) {
                        throw new IllegalArgumentException("${name} is generated; configure pet.json instead")
                    }
                    if (!Files.isDirectory(path) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IllegalArgumentException('Assets must be regular files')
                    }
                }
            }
        }
        if (info?.preview) {
            def preview = assets.resolve(validatePreviewPath(info.preview))
            if (!Files.isRegularFile(preview, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException('Preview PNG is missing from src/main/assets')
            }
            checkPlainPath(root, preview)
            Files.newInputStream(preview).withCloseable {
                validatePng(readBounded(it, MAX_PREVIEW_BYTES, 'Preview PNG'))
            }
        }
    }

    private static void checkPlainPath(def root, def path) {
        if (Files.isSymbolicLink(path) || !path.toRealPath().startsWith(root) ||
            path.toRealPath() != path.toAbsolutePath().normalize()) {
            throw new IllegalArgumentException('Symbolic links and redirected asset paths are unsupported')
        }
    }

    static Map validatePng(byte[] bytes) {
        if (bytes.length < 57 || bytes.length > MAX_PREVIEW_BYTES ||
            !Arrays.equals(Arrays.copyOf(bytes, 8), [137, 80, 78, 71, 13, 10, 26, 10].collect { (byte) it } as byte[])) {
            throw new IllegalArgumentException('Preview must be a PNG of at most 1 MiB')
        }
        def data = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        int offset = 8
        long width = 0
        long height = 0
        boolean seenHeader = false
        boolean seenData = false
        boolean dataEnded = false
        boolean seenPalette = false
        int colorType = -1
        while (offset <= bytes.length - 12) {
            long size = data.getInt(offset) & 0xffffffffL
            if (size > bytes.length - offset - 12) throw new IllegalArgumentException('Truncated PNG chunk')
            int count = (int) size
            String type = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII)
            if (!(type ==~ /[A-Za-z]{4}/)) throw new IllegalArgumentException('Invalid PNG chunk type')
            def crc = new CRC32()
            crc.update(bytes, offset + 4, count + 4)
            if (crc.value != (data.getInt(offset + 8 + count) & 0xffffffffL)) {
                throw new IllegalArgumentException('Invalid PNG chunk CRC')
            }
            if (!seenHeader && type != 'IHDR') throw new IllegalArgumentException('PNG must start with IHDR')
            if (type == 'IHDR') {
                if (seenHeader || count != 13) throw new IllegalArgumentException('Invalid PNG IHDR')
                seenHeader = true
                width = data.getInt(offset + 8) & 0xffffffffL
                height = data.getInt(offset + 12) & 0xffffffffL
                int depth = bytes[offset + 16] & 255
                colorType = bytes[offset + 17] & 255
                def depths = [0: [1, 2, 4, 8, 16], 2: [8, 16], 3: [1, 2, 4, 8], 4: [8, 16], 6: [8, 16]]
                if (width < 1 || height < 1 || width > 1024 || height > 1024 || width * height > 1024L * 1024L ||
                    !(depth in depths[colorType]) || bytes[offset + 18] != 0 || bytes[offset + 19] != 0 ||
                    !((bytes[offset + 20] & 255) in [0, 1])) {
                    throw new IllegalArgumentException('Invalid PNG dimensions or encoding')
                }
            } else if (type == 'PLTE') {
                if (seenPalette || seenData || count == 0 || count > 768 || count % 3 != 0 || colorType in [0, 4]) {
                    throw new IllegalArgumentException('Invalid PNG palette')
                }
                seenPalette = true
            } else if (type == 'IDAT') {
                if (dataEnded || (colorType == 3 && !seenPalette)) throw new IllegalArgumentException('Invalid PNG IDAT order')
                seenData = true
            } else if (type == 'IEND') {
                if (count != 0 || !seenData || offset + 12 != bytes.length) {
                    throw new IllegalArgumentException('Invalid PNG end')
                }
                return [width: width, height: height]
            } else if (Character.isUpperCase(type.charAt(0))) {
                throw new IllegalArgumentException('Unsupported critical PNG chunk')
            }
            if (seenData && type != 'IDAT') dataEnded = true
            offset += count + 12
        }
        throw new IllegalArgumentException('PNG is missing IEND')
    }

    static void verifyZipInfo(ZipFile zip, Map expectedInfo) {
        def entries = zip.entries().collect { it }
        def names = entries.collect { it.name }
        if (names.size() != names.toSet().size()) throw new IllegalArgumentException('Duplicate ZIP entries')
        def entry = zip.getEntry('assets/pet-info.json')
        if (expectedInfo == null) {
            if (entry != null) throw new IllegalArgumentException('Unexpected pet-info.json')
            return
        }
        if (entry == null || entry.directory) throw new IllegalArgumentException('Missing pet-info.json')
        Map actual = zip.getInputStream(entry).withCloseable {
            parse(readBounded(it, MAX_INFO_BYTES, 'pet-info.json'))
        }
        if (actual != expectedInfo) throw new IllegalArgumentException('Generated pet info has changed')
        if (actual.preview) {
            def preview = zip.getEntry('assets/' + actual.preview)
            if (preview == null || preview.directory) throw new IllegalArgumentException('Missing preview PNG')
            zip.getInputStream(preview).withCloseable {
                validatePng(readBounded(it, MAX_PREVIEW_BYTES, 'Preview PNG'))
            }
        }
    }
}
