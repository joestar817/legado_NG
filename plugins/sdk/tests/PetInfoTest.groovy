import groovy.json.JsonOutput
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

// Run with Groovy 3 + groovy-json on JDK 17. No Android build, Gradle task or plugin execution.
def sdk = new File(args ? args[0] : '.').canonicalFile
def validator = new GroovyClassLoader(getClass().classLoader).parseClass(new File(sdk, 'gradle/PetInfo.groovy'))
int checks = 0
def reject = { Closure action ->
    boolean failed = false
    try { action() } catch (IllegalArgumentException | java.nio.charset.CharacterCodingException expected) { failed = true }
    assert failed: 'Expected the invalid input to be rejected'
    checks++
}
def valid = { Closure action -> action(); checks++ }
def chunk = { String type, byte[] bytes ->
    def result = new ByteArrayOutputStream()
    def out = new DataOutputStream(result)
    byte[] name = type.getBytes(StandardCharsets.US_ASCII)
    out.writeInt(bytes.length)
    out.write(name)
    out.write(bytes)
    def crc = new CRC32()
    crc.update(name)
    crc.update(bytes)
    out.writeInt((int) crc.value)
    result.toByteArray()
}
byte[] signature = [137, 80, 78, 71, 13, 10, 26, 10].collect { (byte) it } as byte[]
def png = { int width = 1, int height = 1, int depth = 8, int color = 6 ->
    byte[] header = ByteBuffer.allocate(13).putInt(width).putInt(height)
        .put((byte) depth).put((byte) color).put((byte) 0).put((byte) 0).put((byte) 0).array()
    def out = new ByteArrayOutputStream()
    out.write(signature)
    out.write(chunk('IHDR', header))
    out.write(chunk('IDAT', [120, 156, 99, 96, 0, 2, 0, 0, 5, 0, 1] as byte[]))
    out.write(chunk('IEND', new byte[0]))
    out.toByteArray()
}
def image = new ByteArrayOutputStream()
ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB), 'png', image)
byte[] realPng = image.toByteArray()
Map info = validator.fromAuthor([versionName: '2.2', author: '阅读 NG', description: '第一行\n第二行\t说明', license: 'MIT', preview: 'preview/pet.png'])
valid { assert info.schemaVersion == 1; assert validator.parse(validator.encode(info)) == info }
valid { assert validator.fromAuthor([:]) == [schemaVersion: 1] }
valid { assert validator.validatePng(realPng) == [width: 2L, height: 3L] }
valid { assert validator.validatePng(png(1024, 1024)) == [width: 1024L, height: 1024L] }
[null, [], [schemaVersion: 1], [extra: 'x'], [versionName: null], [author: '   '], [description: '\u0000'], [author: 'a\nb']].each { bad ->
    reject { validator.fromAuthor(bad) }
}
[versionName: 64, author: 128, description: 4096, license: 256].each { key, limit ->
    valid { validator.fromAuthor([(key): 'a' * limit]) }
    reject { validator.fromAuthor([(key): 'a' * (limit + 1)]) }
}
[1, '', '../p.png', 'a/../p.png', '/p.png', 'a//p.png', './p.png', 'a/./p.png', 'a\\p.png',
 'https://example.test/p.png', 'C:/p.png', 'a\u0000.png', 'p.jpg', 'a/' + ('b' * 235) + '.png'].each { bad ->
    reject { validator.fromAuthor([preview: bad]) }
}
valid { validator.fromAuthor([preview: '目录/PET.PNG']) }
[[schemaVersion: 2], [schemaVersion: 1.0], [schemaVersion: 1, unknown: 'x']].each { bad ->
    reject { validator.validate(bad) }
}
reject { validator.parse([123, 34, 97, 34, 58, 34, -1, 34, 125] as byte[]) }
reject { validator.parse(new byte[32769]) }
reject { validator.readBounded(new ByteArrayInputStream(new byte[1025]), 1024, 'test') }
[ png(0, 1), png(1025, 1), png(1, 1025), png(1, 1, 3), png(1, 1, 8, 3),
  Arrays.copyOf(realPng, realPng.length - 1), Arrays.copyOf(realPng, realPng.length + 1), new byte[1024 * 1024 + 1] ].each { bad ->
    reject { validator.validatePng(bad) }
}
byte[] badCrc = Arrays.copyOf(realPng, realPng.length)
badCrc[29] = (byte) (badCrc[29] ^ 1)
reject { validator.validatePng(badCrc) }
byte[] noData = (signature.toList() + chunk('IHDR', ByteBuffer.allocate(13).putInt(1).putInt(1)
    .put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0).array()).toList() +
    chunk('IEND', new byte[0]).toList()) as byte[]
reject { validator.validatePng(noData) }

def temporary = Files.createTempDirectory('pet-info-test-')
try {
    def project = temporary.resolve('project')
    def assets = project.resolve('src/main/assets')
    Files.createDirectories(assets.resolve('preview'))
    Files.write(assets.resolve('preview/pet.png'), realPng)
    valid { validator.validateAssets(project.toFile(), info) }
    valid { validator.validateAssets(project.toFile(), null) }
    reject { validator.validateAssets(project.toFile(), [schemaVersion: 1, preview: 'missing.png']) }
    ['plugin.json', 'pet-info.json'].each { name ->
        Files.write(assets.resolve(name), '{}'.bytes)
        reject { validator.validateAssets(project.toFile(), info) }
        Files.delete(assets.resolve(name))
    }
    def link = assets.resolve('outside.png')
    try {
        Files.createSymbolicLink(link, temporary.resolve('outside.png'))
        reject { validator.validateAssets(project.toFile(), info) }
    } catch (java.nio.file.FileSystemException unsupported) {
        println 'SKIP: this host does not permit creating symbolic links'
    } finally {
        Files.deleteIfExists(link)
    }
    int zipIndex = 0
    def checkZip = { Map entries, Map expected ->
        def file = temporary.resolve("sample-${zipIndex++}.zip").toFile()
        new ZipOutputStream(new FileOutputStream(file)).withCloseable { output ->
            entries.each { name, bytes ->
                output.putNextEntry(new ZipEntry(name))
                output.write((byte[]) bytes)
                output.closeEntry()
            }
        }
        new ZipFile(file).withCloseable { validator.verifyZipInfo(it, expected) }
    }
    def entries = ['assets/pet-info.json': validator.encode(info), 'assets/preview/pet.png': realPng]
    valid { checkZip(entries, info) }
    valid { checkZip([:], null) }
    reject { checkZip([:], info) }
    reject { checkZip(entries, null) }
    reject { checkZip(['assets/pet-info.json': validator.encode(info)], info) }
    reject { checkZip(entries + ['assets/preview/pet.png': badCrc], info) }
    reject { checkZip(entries, info + [author: 'Different']) }
    reject { checkZip(entries + ['assets/pet-info.json': new byte[32769]], info) }
} finally {
    assert temporary.toAbsolutePath().normalize().startsWith(new File(System.getProperty('java.io.tmpdir')).toPath().toAbsolutePath().normalize())
    Files.walk(temporary).withCloseable { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
    }
}
println "PetInfo: ${checks} checks passed"
