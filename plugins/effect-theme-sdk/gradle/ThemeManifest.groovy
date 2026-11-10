import java.nio.file.Files
import java.util.zip.ZipFile

class ThemeManifest {
    static void need(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message) }
    static boolean relative(String path) {
        path != null && path.startsWith('assets/') && !path.contains('\\') && !path.contains(':') &&
            path.split('/', -1).every { it && it != '.' && it != '..' }
    }
    static Map fromAuthor(Object author) {
        need(author instanceof Map, 'theme.json must be an object')
        need(author.format in ['reading-ng-theme','reading-ng-listening-effect'] && author.version == 2, 'Expected a theme or listening-effect package version 2')
        need((author.keySet() - ['format','version','theme','lightBackgroundAsset','darkBackgroundAsset','lightDrawerImageAsset','darkDrawerImageAsset','build','distributions']).isEmpty(), 'Unknown author root field')
        def m = new LinkedHashMap(author); m.remove('build'); m.remove('distributions')
        def t = m.theme
        need(t instanceof Map && t.schemaVersion == 1, 'Missing theme schema')
        def id = t.id
        def withoutId = id == null || (id instanceof String && id.trim().isEmpty())
        need(withoutId ? m.format == 'reading-ng-theme' :
            id instanceof String && id ==~ /[A-Za-z0-9][A-Za-z0-9._-]{0,127}/ &&
                !id.startsWith('builtin.') && !id.startsWith('local.anonymous.'), 'Invalid theme identity')
        if (withoutId) {
            t = new LinkedHashMap(t)
            t.remove('id')
            m.theme = t
        }
        need(t.name instanceof String && t.name.trim() && t.name.size() <= 128, 'Invalid theme name')
        need(t.packageRootPath == null && t.lightBackground?.path == null && t.darkBackground?.path == null, 'Device paths must not be published')
        need(t.lightBackground instanceof Map && t.darkBackground instanceof Map && t.colors instanceof Map, 'Complete static appearance is required')
        ['manualLight','manualDark'].each { field ->
            need(t.colors[field] instanceof Map, 'Missing manual colors')
            ['primary','secondary','primaryText','secondaryText','background','labelContainer'].each { color -> need(t.colors[field][color] instanceof Number, 'Invalid static color') }
        }
        need(t.publication instanceof Map && t.publication.versionCode instanceof Integer && t.publication.versionCode > 0, 'Invalid publication versionCode')
        need(t.publication.versionName instanceof String && t.publication.versionName.trim() && t.publication.versionName.size() <= 64, 'Invalid publication versionName')
        need(t.publication.sha256 == null && t.publication.repository == null, 'Source hashes are assigned by the installer')
        def effect = t.effect
        need(effect instanceof Map && effect.apiVersion == 1, 'Unsupported effect API')
        need(effect.entryClass instanceof String && effect.entryClass ==~ /(?:[A-Za-z_$][A-Za-z0-9_$]*\.)+[A-Za-z_$][A-Za-z0-9_$]*/, 'Invalid entry class')
        need(effect.targets == [m.format == 'reading-ng-theme' ? 'app' : 'player'], 'Theme and listening effect must be separate single-purpose packages')
        need(effect.minGlesVersion instanceof Integer && effect.minGlesVersion >= 0 && effect.minGlesVersion <= 0x30002, 'Invalid GLES requirement')
        need(effect.lowRamAllowed instanceof Boolean, 'Missing low RAM support declaration')
        need(effect.playerPalette in ['cover','theme'], 'Invalid player palette policy')
        need(relative(effect.preview), 'Invalid preview path')
        need(relative(m.lightBackgroundAsset) && relative(m.darkBackgroundAsset), 'Static fallback assets are required')
        def schemas = effect.parameterSchema ?: []
        need(schemas instanceof List && schemas.size() <= 32, 'Invalid parameter schema')
        def keys=[] as Set
        schemas.each { p ->
            need(p instanceof Map && p.key instanceof String && p.key ==~ /[a-z][A-Za-z0-9_.-]{0,63}/ && !p.key.startsWith('host.') && keys.add(p.key), 'Invalid/duplicate parameter key')
            need(p.label instanceof String && p.label.trim() && p.label.size() <= 64, 'Invalid parameter label')
            need(p.type in ['range','choice','color','boolean'] && p.defaultValue instanceof String, 'Invalid parameter type/default')
            if (p.type == 'range') {
                need(p.min instanceof Number && p.max instanceof Number && p.step instanceof Number && [p.min,p.max,p.step].every { Double.isFinite(it.doubleValue()) } && p.min <= p.max && p.step > 0, 'Invalid range')
            } else if (p.type == 'choice') {
                need(p.options instanceof List && p.options && p.options.size() <= 32, 'Missing choices')
                need(p.options.every { it.value instanceof String && it.value && it.label instanceof String && it.label } && p.options.collect { it.value }.unique(false).size() == p.options.size(), 'Invalid choices')
            }
            validateValue(p, p.defaultValue)
        }
        need(t.effectValues instanceof Map && keys.containsAll(t.effectValues.keySet()), 'Invalid parameter overrides')
        t.effectValues.each { k,v -> validateValue(schemas.find { it.key == k }, v) }
        return m
    }
    // Author sources may share implementation/assets; each distributable has one owner.
    static Map<String, Map> packagesFromAuthor(Object author) {
        def base = fromAuthor(author)
        def purposes = author.distributions ?: [base.format == 'reading-ng-theme' ? 'theme' : 'listening_effect']
        need(purposes instanceof List && purposes && purposes.every { it in ['theme','listening_effect'] } &&
            purposes.unique(false).size() == purposes.size(), 'Invalid distributions')
        purposes.collectEntries { purpose ->
            def manifest = new groovy.json.JsonSlurper().parseText(groovy.json.JsonOutput.toJson(base))
            manifest.format = purpose == 'theme' ? 'reading-ng-theme' : 'reading-ng-listening-effect'
            manifest.theme.effect.targets = [purpose == 'theme' ? 'app' : 'player']
            if (purpose == 'listening_effect') {
                ['barProfile','drawerProfile','resourceProfile','coverProfile','ownedCoverAlbumIds'].each { manifest.theme.remove(it) }
            }
            [(purpose): fromAuthor(manifest)]
        }
    }

    static void validateValue(Map p, Object value) {
        need(value instanceof String && value.size() <= 256, 'Parameter values must be short strings')
        if (p.type == 'range') {
            Double n = null; try { n = Double.valueOf(value) } catch (Exception ignored) { }
            need(n != null && Double.isFinite(n) && n >= p.min.doubleValue() && n <= p.max.doubleValue(), 'Range value outside limits')
        } else if (p.type == 'choice') need(p.options.any { it.value == value }, 'Unknown choice')
        else if (p.type == 'color') need(value ==~ /#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?/, 'Expected #RRGGBB or #AARRGGBB')
        else if (p.type == 'boolean') need(value in ['true','false'], 'Invalid boolean')
    }
    static List<String> referencedAssets(Map manifest) {
        [manifest.lightBackgroundAsset, manifest.darkBackgroundAsset, manifest.lightDrawerImageAsset, manifest.darkDrawerImageAsset, manifest.theme.effect.preview].findAll { it != null }
    }
    static void validateAssets(File project, Map manifest) {
        def root = new File(project, 'src/main').canonicalFile.toPath()
        def assetsRoot = root.resolve('assets')
        if (Files.exists(assetsRoot)) Files.walk(assetsRoot).withCloseable { paths -> paths.forEach { need(!Files.isSymbolicLink(it), 'Symlink assets are unsupported') } }
        referencedAssets(manifest).each { path ->
            need(relative(path), 'Invalid asset path')
            def target = root.resolve(path).normalize()
            need(target.startsWith(root) && Files.isRegularFile(target) && !Files.isSymbolicLink(target), "Missing asset: ${path}")
            def current=target
            while (current != root) { need(!Files.isSymbolicLink(current), 'Symlink assets are unsupported'); current=current.parent }
        }
        def preview = root.resolve(manifest.theme.effect.preview)
        need(Files.size(preview) <= 2L * 1024 * 1024, 'Theme preview exceeds 2 MiB')
        validatePreview(Files.readAllBytes(preview))
    }
    static void verifyAssets(ZipFile zip, Map manifest) {
        referencedAssets(manifest).each { path -> need(relative(path) && zip.getEntry(path) != null && !zip.getEntry(path).directory, "Missing packaged asset: ${path}") }
        def preview = zip.getEntry(manifest.theme.effect.preview)
        need(preview.size >= 0 && preview.size <= 2L * 1024 * 1024, 'Theme preview exceeds 2 MiB')
        zip.getInputStream(preview).withCloseable { input -> validatePreview(input.readNBytes(2 * 1024 * 1024 + 1)) }
    }
    static void validatePreview(byte[] image) {
        need(image.length <= 2 * 1024 * 1024, 'Theme preview exceeds 2 MiB')
        def size = previewDimensions(image)
        need(size.every { it >= 1 && it <= 2048 }, 'Theme preview dimensions must be 1..2048 pixels')
    }
    // Metadata only: never decode full-size artwork during the author build.
    // Android performs its own actual image decode when importing the final ZIP.
    static List<Long> previewDimensions(byte[] data) {
        need(data.length >= 12, 'Invalid theme preview image')
        if ([137,80,78,71,13,10,26,10].withIndex().every { value, index -> u8(data,index) == value }) {
            need(data.length >= 33 && u32be(data,8) == 13 && ascii(data,12,4) == 'IHDR', 'Invalid PNG preview')
            return [u32be(data,16), u32be(data,20)]
        }
        if (u8(data,0) == 0xff && u8(data,1) == 0xd8) {
            int cursor = 2
            while (cursor < data.length) {
                need(u8(data,cursor) == 0xff, 'Invalid JPEG preview marker')
                while (cursor < data.length && u8(data,cursor) == 0xff) cursor++
                need(cursor < data.length, 'Truncated JPEG preview')
                int marker = u8(data,cursor++)
                if (marker in [0xd9,0xda]) break
                if (marker == 0x01 || marker in 0xd0..0xd7) continue
                need(cursor + 2 <= data.length, 'Truncated JPEG preview segment')
                int length = u16be(data,cursor)
                need(length >= 2 && cursor + length <= data.length, 'Invalid JPEG preview segment')
                if (marker in [0xc0,0xc1,0xc2,0xc3,0xc5,0xc6,0xc7,0xc9,0xca,0xcb,0xcd,0xce,0xcf]) {
                    need(length >= 8, 'Invalid JPEG preview frame')
                    return [(long) u16be(data,cursor+5), (long) u16be(data,cursor+3)]
                }
                cursor += length
            }
            throw new IllegalArgumentException('JPEG preview has no image frame')
        }
        if (ascii(data,0,4) == 'RIFF' && ascii(data,8,4) == 'WEBP') {
            long end = u32le(data,4) + 8L
            need(end <= data.length && end >= 20, 'Truncated WebP preview')
            int cursor = 12
            while (cursor + 8 <= end) {
                String type = ascii(data,cursor,4)
                long length = u32le(data,cursor+4)
                int start = cursor+8
                need(start + length <= end, 'Truncated WebP preview chunk')
                if (type == 'VP8X') {
                    need(length >= 10, 'Invalid extended WebP preview')
                    return [1L+u24le(data,start+4), 1L+u24le(data,start+7)]
                }
                if (type == 'VP8L') {
                    need(length >= 5 && u8(data,start) == 0x2f, 'Invalid lossless WebP preview')
                    return [1L+(u8(data,start+1) | ((u8(data,start+2) & 0x3f) << 8)),
                        1L+((u8(data,start+2) >> 6) | (u8(data,start+3) << 2) | ((u8(data,start+4) & 0x0f) << 10))]
                }
                if (type == 'VP8 ') {
                    need(length >= 10 && u8(data,start+3) == 0x9d && u8(data,start+4) == 0x01 && u8(data,start+5) == 0x2a, 'Invalid lossy WebP preview')
                    return [(long) ((u8(data,start+6) | (u8(data,start+7) << 8)) & 0x3fff),
                        (long) ((u8(data,start+8) | (u8(data,start+9) << 8)) & 0x3fff)]
                }
                cursor = (int) (start + length + (length & 1L))
            }
            throw new IllegalArgumentException('WebP preview has no image frame')
        }
        throw new IllegalArgumentException('Theme previews must be PNG, JPEG or WebP')
    }
    private static int u8(byte[] data, int offset) { data[offset] & 0xff }
    private static int u16be(byte[] data, int offset) { (u8(data,offset) << 8) | u8(data,offset+1) }
    private static long u32be(byte[] data, int offset) { ((long) u16be(data,offset) << 16) | u16be(data,offset+2) }
    private static long u24le(byte[] data, int offset) { u8(data,offset) | (u8(data,offset+1) << 8) | (u8(data,offset+2) << 16) }
    private static long u32le(byte[] data, int offset) { u24le(data,offset) | ((long) u8(data,offset+3) << 24) }
    private static String ascii(byte[] data, int offset, int count) { new String(data,offset,count,java.nio.charset.StandardCharsets.US_ASCII) }
}
