import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def root=new File(args[0]).canonicalFile
Class validator=new GroovyClassLoader(getClass().classLoader).parseClass(new File(root,'plugins/effect-theme-sdk/gradle/ThemeManifest.groovy'))
def projects=['theme-cats','theme-sakura','theme-rain-night','theme-fire','theme-fluid'].collect { new File(root,'plugins/'+it) } + new File(root,'plugins/effect-theme-sdk/template')
int passed=0
projects.each { project ->
    def input=new JsonSlurper().parse(new File(project,'theme.json'),'UTF-8')
    def manifest=validator.fromAuthor(input)
    validator.validateAssets(project,manifest)
    assert !manifest.containsKey('build')
    passed++
}
def seed=new JsonSlurper().parse(new File(root,'plugins/theme-fire/theme.json'),'UTF-8')
def reject={ Closure mutate ->
    def m=new JsonSlurper().parseText(JsonOutput.toJson(seed))
    mutate(m)
    boolean rejected=false
    try { validator.fromAuthor(m) } catch (IllegalArgumentException expected) { rejected=true }
    assert rejected : 'Invalid manifest was accepted'
    passed++
}
reject { it.version=1 }
reject { it.format='scene-only' }
reject { it.unexpected=true }
reject { it.theme.id='builtin.fake' }
reject { it.theme.id='local.anonymous.fake' }
reject { it.theme.id='../bad' }
reject { it.format='reading-ng-listening-effect'; it.theme.effect.targets=['player']; it.theme.remove('id') }

def anonymous = new JsonSlurper().parseText(JsonOutput.toJson(seed))
anonymous.format = 'reading-ng-theme'
anonymous.theme.effect.targets = ['app']
anonymous.distributions = ['theme']
anonymous.theme.remove('id')
def anonymousTheme = validator.packagesFromAuthor(anonymous).theme
assert !anonymousTheme.theme.containsKey('id')
passed++
reject { it.theme.name=' ' }
reject { it.theme.colors=null }
reject { it.theme.colors.manualLight.primary=null }
reject { it.theme.lightBackground.path='/sdcard/source.webp' }
reject { it.lightBackgroundAsset='assets/../escape.png' }
reject { it.darkBackgroundAsset='/absolute.png' }
reject { it.theme.effect.preview='assets/a\\b.png' }
reject { it.theme.effect=null }
reject { it.theme.effect.apiVersion=2 }
reject { it.theme.effect.entryClass='MissingPackage' }
reject { it.theme.effect.targets=[] }
reject { it.theme.effect.targets=['app','app'] }
reject { it.theme.effect.targets=['camera'] }
reject { it.theme.effect.targets=['app','player'] }
reject { it.format='reading-ng-theme' }
reject { it.theme.effect.targets=['app'] }
// A reusable author project publishes distinct manifests, not a dual-use package.
projects.each { project ->
    def input=new JsonSlurper().parse(new File(project,'theme.json'),'UTF-8')
    def packages=validator.packagesFromAuthor(input)
    assert packages.keySet() == input.distributions.toSet()
    packages.each { purpose, manifest ->
        assert manifest.theme.effect.targets == [purpose == 'theme' ? 'app' : 'player']
        assert manifest.format == (purpose == 'theme' ? 'reading-ng-theme' : 'reading-ng-listening-effect')
        assert !manifest.containsKey('build') && !manifest.containsKey('distributions')
        validator.fromAuthor(manifest)
        passed++
    }
}

reject { it.theme.effect.playerPalette='unknown' }
reject { it.theme.publication.versionCode=0 }
reject { it.theme.publication.versionName='' }
reject { it.theme.publication.sha256='forged' }
reject { it.theme.effect.parameterSchema[0].key='host.coverColor' }
reject { it.theme.effect.parameterSchema[1].key='style' }
reject { it.theme.effect.parameterSchema[0].defaultValue='not-a-style' }
reject { it.theme.effect.parameterSchema[1].defaultValue='NaN' }
reject { it.theme.effect.parameterSchema[1].defaultValue='101' }
reject { it.theme.effect.parameterSchema[1].step=0 }
reject { it.theme.effect.parameterSchema[1].step=Double.POSITIVE_INFINITY }
reject { it.theme.effect.parameterSchema[3].defaultValue='red' }
reject { it.theme.effectValues=[unknown:'42'] }
reject { it.theme.effectValues=[style:'missing'] }
// Host parity: previews are thumbnails; large production textures stay untouched.
def imageBytes = { String format, int width, int height ->
    def image = new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_RGB)
    def output = new ByteArrayOutputStream()
    assert javax.imageio.ImageIO.write(image,format,output)
    output.toByteArray()
}
def previewCase = { boolean accepted, Closure action ->
    boolean rejected=false
    try { action() } catch (IllegalArgumentException expected) { rejected=true }
    assert rejected != accepted : accepted ? 'Valid preview rejected' : 'Invalid preview accepted'
    passed++
}
byte[] png = imageBytes('png',384,854)
previewCase(true) { validator.validatePreview(png) }
previewCase(true) { validator.validatePreview(imageBytes('png',2048,1)) }
previewCase(true) { validator.validatePreview(imageBytes('jpeg',1,2048)) }
previewCase(false) { validator.validatePreview(imageBytes('png',2049,1)) }
previewCase(false) { validator.validatePreview(imageBytes('png',1,2049)) }
previewCase(false) { validator.validatePreview(imageBytes('jpeg',2049,1)) }
previewCase(true) { validator.validatePreview(Arrays.copyOf(png,2*1024*1024)) }
previewCase(false) { validator.validatePreview(Arrays.copyOf(png,2*1024*1024+1)) }
previewCase(false) { validator.validatePreview(new byte[0]) }
previewCase(false) { validator.validatePreview(Arrays.copyOf(png,20)) }
byte[] zeroWidth=Arrays.copyOf(png,png.length); (16..19).each { zeroWidth[it]=0 }
previewCase(false) { validator.validatePreview(zeroWidth) }

// These are the original author artwork that caused the Android import regression.
// Keep the originals as renderer/fallback assets, but never accept them as previews.
projects.take(3).each { project ->
    def manifest=validator.fromAuthor(new JsonSlurper().parse(new File(project,'theme.json'),'UTF-8'))
    byte[] original=new File(project,'src/main/'+manifest.lightBackgroundAsset).bytes
    assert validator.previewDimensions(original).any { it > 2048 }
    previewCase(false) { validator.validatePreview(original) }
}

def temporary=java.nio.file.Files.createTempDirectory('effect-theme-preview-test-')
def assetDirectory=temporary.resolve('src/main/assets')
java.nio.file.Files.createDirectories(assetDirectory)
def fixture=assetDirectory.resolve('preview.png')
def packageFile=temporary.resolve('fixture.zip')
def assetManifest=validator.fromAuthor(new JsonSlurper().parseText(JsonOutput.toJson(seed)))
assetManifest.lightBackgroundAsset='assets/preview.png'
assetManifest.darkBackgroundAsset='assets/preview.png'
assetManifest.theme.effect.preview='assets/preview.png'
try {
    java.nio.file.Files.write(fixture,imageBytes('png',1,2049))
    previewCase(false) { validator.validateAssets(temporary.toFile(),assetManifest) }
    new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(packageFile)).withCloseable { output ->
        output.putNextEntry(new java.util.zip.ZipEntry('assets/preview.png'))
        output.write(java.nio.file.Files.readAllBytes(fixture))
        output.closeEntry()
    }
    new java.util.zip.ZipFile(packageFile.toFile()).withCloseable { zip ->
        previewCase(false) { validator.verifyAssets(zip,assetManifest) }
    }
    java.nio.file.Files.write(fixture,png)
    previewCase(true) { validator.validateAssets(temporary.toFile(),assetManifest) }
} finally {
    [packageFile,fixture,assetDirectory,assetDirectory.parent,assetDirectory.parent.parent,temporary].each {
        java.nio.file.Files.deleteIfExists(it)
    }
}
println "PASS ${passed} complete-theme manifest and preview cases"
