import groovy.json.JsonSlurper
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// Optional fixture: a previously built API-2 ZIP. Repacking is test-only; no DEX is executed.
assert args.length == 2: 'Pass SDK directory and an existing API-2 ZIP fixture'
def sdk = new File(args[0]).canonicalFile
def fixture = new File(args[1]).canonicalFile
def loader = new GroovyClassLoader(getClass().classLoader)
loader.parseClass('class GradleException extends RuntimeException { GradleException(String message) { super(message) } }')
def validator = loader.parseClass(new File(sdk, 'gradle/PetInfo.groovy'))
loader.parseClass(new File(sdk, 'build.gradle'))
def extensions = [:]
new GroovyShell(loader, new Binding([ext: extensions, petInfoValidator: validator]))
    .evaluate(new File(sdk, 'gradle/verify-zip.gradle'))
def verify = extensions.verifyPetZip
Map manifest
new ZipFile(fixture).withCloseable { zip ->
    manifest = zip.getInputStream(zip.getEntry('assets/plugin.json')).withCloseable {
        new JsonSlurper().parse(it, 'UTF-8')
    }
}
verify(fixture, manifest)
def root = sdk.parentFile.parentFile
int checks = 1
['pet-gugugaga', 'pet-blue-fish'].each { name ->
    def project = new File(sdk.parentFile, name)
    Map config = new JsonSlurper().parse(new File(project, 'pet.json'), 'UTF-8')
    Map info = validator.fromAuthor(config.info)
    validator.validateAssets(project, info)
    def preview = new File(project, 'src/main/assets/' + info.preview).bytes
    def temporary = Files.createTempDirectory('pet-package-test-')
    try {
        def rewritten = temporary.resolve('with-info.zip').toFile()
        new ZipOutputStream(new FileOutputStream(rewritten)).withCloseable { output ->
            new ZipFile(fixture).withCloseable { source ->
                source.entries().each { entry ->
                    if (!(entry.name in ['assets/pet-info.json', 'assets/' + info.preview])) {
                        output.putNextEntry(new ZipEntry(entry.name))
                        if (!entry.directory) source.getInputStream(entry).withCloseable { it.transferTo(output) }
                        output.closeEntry()
                    }
                }
            }
            ['assets/pet-info.json': validator.encode(info), ('assets/' + info.preview): preview].each { path, bytes ->
                output.putNextEntry(new ZipEntry(path))
                output.write((byte[]) bytes)
                output.closeEntry()
            }
        }
        verify(rewritten, manifest, info)
        checks++
        boolean rejected = false
        try { verify(rewritten, manifest, info + [author: 'Changed']) }
        catch (IllegalArgumentException expected) { rejected = true }
        assert rejected: 'Changed display metadata must fail final ZIP verification'
        checks++
        println "${name}: source preview and final ZIP checks passed"
    } finally {
        assert temporary.toAbsolutePath().normalize().startsWith(new File(System.getProperty('java.io.tmpdir')).toPath().toAbsolutePath().normalize())
        Files.walk(temporary).withCloseable { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }
}
println "Package contract: ${checks} checks passed; SDK build script parsed"

