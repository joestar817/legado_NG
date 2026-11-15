import groovy.json.JsonOutput
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Shared author sources produce independent complete archives, never references to another package. */
class ThemePackager {
    static void write(File source, File target, Map manifest, Closure verify) {
        target.parentFile.mkdirs()
        def candidate = new File(target.parentFile, target.name + '.candidate')
        try {
            new ZipFile(source).withCloseable { input ->
                if (input.entries().any { it.name.startsWith('lib/') }) {
                    throw new IllegalArgumentException('Native libraries are not supported by API 1')
                }
                new ZipOutputStream(candidate.newOutputStream()).withCloseable { output ->
                    def rootEntry = new ZipEntry('manifest.json'); rootEntry.time = 0
                    output.putNextEntry(rootEntry)
                    output.write((JsonOutput.prettyPrint(JsonOutput.toJson(manifest)) + '\n').getBytes('UTF-8'))
                    output.closeEntry()
                    input.entries().findAll { !it.directory && (it.name.startsWith('assets/') || it.name ==~ /classes(?:[0-9]+)?\.dex/) }.sort { it.name }.each { entry ->
                        def result = new ZipEntry(entry.name); result.time = 0
                        output.putNextEntry(result)
                        input.getInputStream(entry).withCloseable { it.transferTo(output) }
                        output.closeEntry()
                    }
                }
            }
            verify(candidate, manifest)
            Files.move(candidate.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(candidate.toPath()) }
    }
}
