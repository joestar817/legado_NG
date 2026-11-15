package io.legado.app.help.tts.packages;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class MicrosoftTtsPackageStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final Gson GSON = new Gson();
    private static final String ENGINE = MicrosoftTtsPackageStore.ENGINE;
    private static final String VOICE = "io.legado.prototype.microsoft.voice.test";
    private static final MicrosoftTtsPackageStore.Device DEVICE =
            new MicrosoftTtsPackageStore.Device("arm64-v8a", 34, 4);

    @Test public void previewCancelTrustInstallAndRepeat() throws Exception {
        File root = temporary.newFolder();
        Fixture fixture = fixture(false, false);
        MicrosoftTtsPackageStore store = new MicrosoftTtsPackageStore(root);
        try (MicrosoftTtsPackageStore.Plan cancelled = store.inspect(fixture.bundle, DEVICE)) {
            assertEquals(2, cancelled.packages.size());
            assertEquals(1, cancelled.trustRequired.size());
        }
        assertTrue(store.installed().isEmpty());
        MicrosoftTtsPackageStore.Plan unapproved = store.inspect(fixture.bundle, DEVICE);
        assertThrows(IllegalArgumentException.class, () -> store.install(unapproved, Collections.emptySet()));
        assertTrue(store.installed().isEmpty());
        MicrosoftTtsPackageStore.Plan approved = store.inspect(fixture.bundle, DEVICE);
        store.install(approved, Collections.singleton(fixture.signer));
        assertEquals(2, new MicrosoftTtsPackageStore(root).installed().size());
        try (MicrosoftTtsPackageStore.Plan repeated = store.inspect(fixture.bundle, DEVICE)) {
            assertEquals(2, repeated.reusable);
            assertTrue(repeated.trustRequired.isEmpty());
        }
    }

    @Test public void invalidPayloadOrSignatureCannotReplaceExistingObjects() throws Exception {
        File root = temporary.newFolder();
        Fixture good = fixture(false, false);
        MicrosoftTtsPackageStore store = new MicrosoftTtsPackageStore(root);
        store.install(store.inspect(good.bundle, DEVICE), Collections.singleton(good.signer));
        long generation = store.snapshot().generation;
        Fixture badHash = fixture(true, false);
        Fixture badSignature = fixture(false, true);
        assertThrows(IllegalArgumentException.class, () -> store.inspect(badHash.bundle, DEVICE));
        assertThrows(IllegalArgumentException.class, () -> store.inspect(badSignature.bundle, DEVICE));
        MicrosoftTtsPackageStore reopened = new MicrosoftTtsPackageStore(root);
        assertEquals(generation, reopened.snapshot().generation);
        assertEquals(2, reopened.installed().size());
    }

    @Test public void rejectsIndividualPackageAndUnsafePaths() throws Exception {
        File root = temporary.newFolder();
        Fixture fixture = fixture(false, false);
        MicrosoftTtsPackageStore store = new MicrosoftTtsPackageStore(root);
        assertThrows(IllegalArgumentException.class, () -> store.inspect(fixture.engine, DEVICE));
        assertThrows(IllegalArgumentException.class, () -> MicrosoftTtsPackageStore.safe(root, "../outside"));
        assertTrue(store.installed().isEmpty());
    }

    @Test public void recoversInterruptedInstallationWithoutDeletingCommittedObjects() throws Exception {
        File root = temporary.newFolder();
        Fixture fixture = fixture(false, false);
        MicrosoftTtsPackageStore store = new MicrosoftTtsPackageStore(root);
        store.install(store.inspect(fixture.bundle, DEVICE), Collections.singleton(fixture.signer));
        String committed = store.snapshot().installed.iterator().next();
        String orphan = "0".repeat(64);
        File orphanDir = new File(root, "objects/" + orphan);
        assertTrue(orphanDir.mkdir());
        File stage = new File(root, "staging/interrupted");
        assertTrue(stage.mkdir());
        Files.writeString(new File(root, "journal.json").toPath(),
                "{\"new_objects\":[\"" + committed + "\",\"" + orphan + "\"]}");
        MicrosoftTtsPackageStore recovered = new MicrosoftTtsPackageStore(root);
        assertEquals(2, recovered.installed().size());
        assertTrue(new File(root, "objects/" + committed).isDirectory());
        assertFalse(orphanDir.exists());
        assertFalse(stage.exists());
        assertFalse(new File(root, "journal.json").exists());
    }

    @Test public void cancelledInspectionCleansStagingAndCanBeRetried() throws Exception {
        File root = temporary.newFolder();
        Fixture fixture = fixture(false, false);
        MicrosoftTtsPackageStore store = new MicrosoftTtsPackageStore(root);
        AtomicInteger checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> store.inspect(fixture.bundle, DEVICE, () -> {
            if (checks.incrementAndGet() == 8) throw new CancellationException();
        }));
        assertTrue(store.installed().isEmpty());
        assertEquals(0, new File(root, "staging").list().length);
        try (MicrosoftTtsPackageStore.Plan retried = store.inspect(fixture.bundle, DEVICE)) {
            assertEquals(2, retried.packages.size());
        }
    }

    @Test public void bundleCredentialMustBeDeclaredAndResolvesFromInstalledVoice() throws Exception {
        File root = temporary.newFolder();
        Fixture fixture = fixture(false, false, true);
        MicrosoftTtsPackageStore store = new MicrosoftTtsPackageStore(root);
        store.install(store.inspect(fixture.bundle, DEVICE), Collections.singleton(fixture.signer));
        Map<String, String> graph = store.latestGraph(DEVICE);
        File license = new File(store.credential(graph, VOICE, "model_license"));
        assertEquals("fixture-license", Files.readString(license.toPath()));
        assertNull(store.credential(graph, VOICE, "unknown"));
    }

    @Test public void undeclaredBundleCredentialCannotBeInstalled() throws Exception {
        File root = temporary.newFolder();
        Fixture fixture = fixture(false, false, true, true);
        MicrosoftTtsPackageStore store = new MicrosoftTtsPackageStore(root);
        assertThrows(IllegalArgumentException.class, () -> store.inspect(fixture.bundle, DEVICE));
        assertTrue(store.installed().isEmpty());
    }

    @Test public void completeBundleReplacesDirectoryAndKeepsPinnedPlaybackGraph() throws Exception {
        File root = temporary.newFolder();
        Fixture fixture = fixture(false, false);
        File expanded = addSecondVoice(fixture.bundle);
        MicrosoftTtsPackageStore store = new MicrosoftTtsPackageStore(root);
        store.install(store.inspect(expanded, DEVICE), Collections.singleton(fixture.signer));
        Map<String, String> oldGraph = store.latestGraph(DEVICE);
        assertEquals(2, store.catalogVoices(oldGraph).size());
        byte[] before = Files.readAllBytes(new File(root, "registry.json").toPath());
        try (MicrosoftTtsPackageStore.Plan cancelled = store.inspect(fixture.bundle, DEVICE)) {
            assertEquals(1, cancelled.voices.size());
        }
        assertArrayEquals(before, Files.readAllBytes(new File(root, "registry.json").toPath()));
        store.install(store.inspect(fixture.bundle, DEVICE), Collections.singleton(fixture.signer));
        store = new MicrosoftTtsPackageStore(root);
        Map<String, String> updated = store.latestGraph(DEVICE);
        assertEquals(2, updated.size());
        assertEquals(1, store.catalogVoices(updated).size());
        assertEquals("voice", store.catalogVoices(updated).get(0).voiceId);
        assertEquals(3, store.installed().size());
        store.checkGraph(oldGraph, DEVICE);
        store.install(store.inspect(fixture.bundle, DEVICE), Collections.singleton(fixture.signer));
        assertEquals(updated, new MicrosoftTtsPackageStore(root).latestGraph(DEVICE));
    }

    @Test public void oldRegistryGetsAnExactCatalogOnlyWhenImportCommits() throws Exception {
        File root = temporary.newFolder();
        Fixture fixture = fixture(false, false);
        MicrosoftTtsPackageStore store = new MicrosoftTtsPackageStore(root);
        store.install(store.inspect(addSecondVoice(fixture.bundle), DEVICE), Collections.singleton(fixture.signer));
        File registry = new File(root, "registry.json");
        JsonObject old = JsonParser.parseString(Files.readString(registry.toPath())).getAsJsonObject();
        old.remove("catalog");
        Files.writeString(registry.toPath(), GSON.toJson(old));
        store = new MicrosoftTtsPackageStore(root);
        assertEquals(2, store.catalogVoices(store.latestGraph(DEVICE)).size());
        store.install(store.inspect(fixture.bundle, DEVICE), Collections.singleton(fixture.signer));
        MicrosoftTtsPackageStore restored = new MicrosoftTtsPackageStore(root);
        assertEquals(Set.of("voice"), restored.snapshot().catalog.voiceIds);
        assertEquals(1, restored.catalogVoices(restored.latestGraph(DEVICE)).size());
    }

    @Test public void playbackReadsCommittedSnapshotWithoutCleaningAPendingPreview() throws Exception {
        File root = temporary.newFolder();
        Fixture fixture = fixture(false, false);
        MicrosoftTtsPackageStore installer = new MicrosoftTtsPackageStore(root);
        installer.install(installer.inspect(addSecondVoice(fixture.bundle), DEVICE), Collections.singleton(fixture.signer));
        Map<String, String> pinned = installer.latestGraph(DEVICE);
        try (MicrosoftTtsPackageStore.Plan preview = installer.inspect(fixture.bundle, DEVICE)) {
            MicrosoftTtsPackageStore reader = MicrosoftTtsPackageStore.readOnly(root);
            reader.checkGraph(pinned, DEVICE);
            assertEquals(2, reader.catalogVoices(reader.latestGraph(DEVICE)).size());
            assertTrue(preview.directory.isDirectory());
            assertThrows(IllegalArgumentException.class, () -> reader.inspect(fixture.bundle, DEVICE));
            installer.install(preview, Collections.singleton(fixture.signer));
            reader.checkGraph(pinned, DEVICE);
        }
        MicrosoftTtsPackageStore reader = MicrosoftTtsPackageStore.readOnly(root);
        assertEquals(1, reader.catalogVoices(reader.latestGraph(DEVICE)).size());
        reader.checkGraph(pinned, DEVICE);
    }

    @Test public void replacementRecoveryUsesTheAtomicRegistryCommitPoint() throws Exception {
        File root = temporary.newFolder();
        Fixture fixture = fixture(false, false);
        MicrosoftTtsPackageStore store = new MicrosoftTtsPackageStore(root);
        store.install(store.inspect(addSecondVoice(fixture.bundle), DEVICE), Collections.singleton(fixture.signer));
        String orphan = "1".repeat(64);
        assertTrue(new File(root, "objects/" + orphan).mkdir());
        Files.writeString(new File(root, "journal.json").toPath(), "{\"new_objects\":[\"" + orphan + "\"]}");
        store = new MicrosoftTtsPackageStore(root);
        assertEquals(2, store.catalogVoices(store.latestGraph(DEVICE)).size());
        assertFalse(new File(root, "objects/" + orphan).exists());
        store.install(store.inspect(fixture.bundle, DEVICE), Collections.singleton(fixture.signer));
        String retained = store.snapshot().installed.iterator().next();
        Files.writeString(new File(root, "journal.json").toPath(), "{\"new_objects\":[\"" + retained + "\"]}");
        store = new MicrosoftTtsPackageStore(root);
        assertEquals(1, store.catalogVoices(store.latestGraph(DEVICE)).size());
        store.checkGraph(store.latestGraph(DEVICE), DEVICE);
    }

    @Test public void selectedRootsCanRemoveAVoiceWithoutChangingItsSharedComponent() throws Exception {
        Fixture fixture = fixture(false, false);
        File expanded = addSecondVoice(fixture.bundle);
        Map<String, byte[]> contents = unzip(expanded);
        JsonObject bundle = JsonParser.parseString(new String(contents.get("manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        bundle.getAsJsonArray("roots").remove(1);
        contents.put("manifest.json", GSON.toJson(bundle).getBytes(StandardCharsets.UTF_8));
        File filtered = temporary.newFile();
        Files.write(filtered.toPath(), zip(contents));
        MicrosoftTtsPackageStore store = new MicrosoftTtsPackageStore(temporary.newFolder());
        try (MicrosoftTtsPackageStore.Plan plan = store.inspect(filtered, DEVICE)) {
            assertEquals(1, plan.voices.size());
            store.install(plan, Collections.singleton(fixture.signer));
        }
        assertEquals(1, store.catalogVoices(store.latestGraph(DEVICE)).size());
    }

    @Test public void duplicateVoiceIdsAcrossComponentsAreRejectedBeforePublishing() throws Exception {
        File root = temporary.newFolder();
        Fixture fixture = fixture(false, false);
        MicrosoftTtsPackageStore store = new MicrosoftTtsPackageStore(root);
        store.install(store.inspect(fixture.bundle, DEVICE), Collections.singleton(fixture.signer));
        byte[] before = Files.readAllBytes(new File(root, "registry.json").toPath());
        File duplicate = addSecondVoice(fixture.bundle, "voice");
        Map<String, byte[]> contents = unzip(duplicate);
        JsonObject bundle = JsonParser.parseString(new String(contents.get("manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        bundle.getAsJsonArray("roots").remove(1); // An unselected component must not alias an exposed voice ID.
        contents.put("manifest.json", GSON.toJson(bundle).getBytes(StandardCharsets.UTF_8));
        Files.write(duplicate.toPath(), zip(contents));
        assertThrows(IllegalArgumentException.class, () -> store.inspect(duplicate, DEVICE));
        assertArrayEquals(before, Files.readAllBytes(new File(root, "registry.json").toPath()));
    }

    private static Map<String, byte[]> unzip(File file) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(file)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                try (var in = zip.getInputStream(entry)) { result.put(entry.getName(), in.readAllBytes()); }
            }
        }
        return result;
    }

    private File addSecondVoice(File original) throws Exception {
        return addSecondVoice(original, "removed");
    }

    private File addSecondVoice(File original, String voiceId) throws Exception {
        Map<String, byte[]> contents = unzip(original);
        File firstVoice = temporary.newFile();
        Files.write(firstVoice.toPath(), contents.get("packages/voice.ngvoice"));
        Map<String, byte[]> payload = unzip(firstVoice);
        JsonObject voice = JsonParser.parseString(new String(payload.get("manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        String secondId = VOICE + ".removed";
        voice.addProperty("package_id", secondId);
        voice.getAsJsonArray("voices").get(0).getAsJsonObject().addProperty("voice_id", voiceId);
        byte[] raw = GSON.toJson(voice).getBytes(StandardCharsets.UTF_8);
        payload.put("manifest.json", raw);
        contents.put("packages/removed.zip", zip(payload));
        JsonObject bundle = JsonParser.parseString(new String(contents.remove("manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        bundle.add("files", files(contents));
        bundle.getAsJsonArray("members").add(member("packages/removed.zip", secondId, "common", raw));
        bundle.getAsJsonArray("roots").add(json("{\"package_id\":\"" + secondId + "\",\"voice_ids\":[\"" + voiceId + "\"]}"));
        contents.put("manifest.json", GSON.toJson(bundle).getBytes(StandardCharsets.UTF_8));
        File result = temporary.newFile();
        Files.write(result.toPath(), zip(contents));
        return result;
    }

    private static class Fixture {
        File bundle, engine;
        String signer;
    }

    private Fixture fixture(boolean badHash, boolean badSignature) throws Exception {
        return fixture(badHash, badSignature, false);
    }

    private Fixture fixture(boolean badHash, boolean badSignature, boolean includeCredential) throws Exception {
        return fixture(badHash, badSignature, includeCredential, false);
    }

    private Fixture fixture(boolean badHash, boolean badSignature, boolean includeCredential,
                            boolean badCredentialReference) throws Exception {
        KeyPair pair = new net.i2p.crypto.eddsa.KeyPairGenerator().generateKeyPair();
        byte[] key = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
        String keyId = MicrosoftTtsPackageStore.sha256(key);
        Map<String, byte[]> engineFiles = new LinkedHashMap<>();
        engineFiles.put("code/adapter.jar", new byte[]{1, 2, 3});
        engineFiles.put("native/arm64-v8a/libfixture.so", elf());
        JsonObject engine = manifest(ENGINE, "engine");
        engine.add("requires", new JsonArray());
        engine.add("host_api", json("{\"major\":1,\"min_minor\":0}"));
        engine.add("android", json("{\"min_sdk\":26,\"abi\":\"arm64-v8a\",\"native_page_sizes_kb\":[4],\"cpu_features\":[]}"));
        engine.add("implementation", json("{\"type\":\"dex-jni\",\"dex_path\":\"code/adapter.jar\",\"entry_class\":\"io.legado.microsoft.plugin.MicrosoftEngine\",\"native_dir\":\"native/arm64-v8a\"}"));
        engine.add("access", json("{\"network\":false,\"credential_slots\":[\"model_license\"]}"));
        engine.add("provides", JsonParser.parseString("[{\"engine_id\":\"io.legado.prototype.microsoft\",\"formats\":[{\"id\":\"io.legado.prototype.microsoft-embedded\",\"revision\":1}]}]"));
        engine.add("files", files(engineFiles));
        byte[] engineManifest = GSON.toJson(engine).getBytes(StandardCharsets.UTF_8);
        EdDSAEngine signer = new EdDSAEngine(MessageDigest.getInstance("SHA-512"));
        signer.initSign(pair.getPrivate());
        signer.update(engineManifest);
        byte[] signature = signer.sign();
        if (badSignature) signature[0] ^= 1;
        JsonObject signatureJson = new JsonObject();
        signatureJson.addProperty("algorithm", "ed25519");
        signatureJson.addProperty("key_id", keyId);
        signatureJson.addProperty("public_key", Base64.getEncoder().encodeToString(key));
        signatureJson.addProperty("manifest_signature", Base64.getEncoder().encodeToString(signature));
        if (badHash) engineFiles.put("code/adapter.jar", new byte[]{4, 5, 6});
        engineFiles.put("manifest.json", engineManifest);
        engineFiles.put("signature.json", GSON.toJson(signatureJson).getBytes(StandardCharsets.UTF_8));
        byte[] engineZip = zip(engineFiles);

        Map<String, byte[]> voiceFiles = new LinkedHashMap<>();
        voiceFiles.put("data/model.bin", new byte[]{1, 2, 3});
        voiceFiles.put("data/config.bin", new byte[]{4, 5, 6});
        if (includeCredential) voiceFiles.put("data/license.txt", "fixture-license".getBytes(StandardCharsets.UTF_8));
        JsonObject voice = manifest(VOICE, "voice");
        voice.addProperty("variant_id", "common");
        voice.addProperty("content_role", "voices");
        voice.addProperty("engine_dependency", "engine");
        voice.add("data_format", json("{\"id\":\"io.legado.prototype.microsoft-embedded\",\"revision\":1}"));
        voice.add("requires", JsonParser.parseString("[{\"alias\":\"engine\",\"kind\":\"engine\",\"package_id\":\"" + ENGINE + "\",\"engine_id\":\"io.legado.prototype.microsoft\",\"version\":{\"min\":1,\"max_exclusive\":4}}]"));
        voice.add("profiles", JsonParser.parseString("[{\"profile_id\":\"profile\",\"files\":{\"model\":{\"from\":\"self\",\"path\":\"data/model.bin\"},\"voice_config\":{\"from\":\"self\",\"path\":\"data/config.bin\"}}}]"));
        voice.add("voices", JsonParser.parseString("[{\"voice_id\":\"voice\",\"name\":\"Test voice\",\"locale\":\"zh-CN\",\"profile_id\":\"profile\",\"parameters\":{\"speaker_id\":0}}]"));
        if (includeCredential) voice.add("credentials", json("{\"model_license\":{\"from\":\"self\",\"path\":\"" +
                (badCredentialReference ? "data/missing.txt" : "data/license.txt") + "\"}}"));
        voice.add("files", files(voiceFiles));
        byte[] voiceManifest = GSON.toJson(voice).getBytes(StandardCharsets.UTF_8);
        voiceFiles.put("manifest.json", voiceManifest);
        byte[] voiceZip = zip(voiceFiles);

        Map<String, byte[]> members = new LinkedHashMap<>();
        members.put("packages/engine.ngengine", engineZip);
        members.put("packages/voice.ngvoice", voiceZip);
        JsonObject bundle = manifest("io.legado.prototype.microsoft.bundle.test", "bundle");
        bundle.addProperty("mode", "complete");
        bundle.add("files", files(members));
        JsonArray memberList = new JsonArray();
        memberList.add(member("packages/engine.ngengine", ENGINE, "arm64-v8a", engineManifest));
        memberList.add(member("packages/voice.ngvoice", VOICE, "common", voiceManifest));
        bundle.add("members", memberList);
        bundle.add("roots", JsonParser.parseString("[{\"package_id\":\"" + VOICE + "\",\"voice_ids\":[\"voice\"]}]"));
        members.put("manifest.json", GSON.toJson(bundle).getBytes(StandardCharsets.UTF_8));
        Fixture result = new Fixture();
        result.bundle = temporary.newFile();
        result.engine = temporary.newFile();
        result.signer = keyId;
        Files.write(result.bundle.toPath(), zip(members));
        Files.write(result.engine.toPath(), engineZip);
        return result;
    }

    private static JsonObject manifest(String id, String kind) {
        JsonObject m = new JsonObject();
        m.addProperty("format", "ng-tts-package");
        m.addProperty("schema_version", 1);
        m.addProperty("kind", kind);
        m.addProperty("package_id", id);
        m.addProperty("version_code", 1);
        m.addProperty("version_name", "1");
        m.addProperty("variant_id", "arm64-v8a");
        m.addProperty("name", "test");
        m.add("publisher", json("{\"id\":\"fixture\",\"name\":\"Test Publisher\"}"));
        m.add("required_features", new JsonArray());
        return m;
    }

    private static JsonObject member(String path, String id, String variant, byte[] manifest) throws Exception {
        JsonObject m = new JsonObject();
        m.addProperty("path", path);
        m.addProperty("package_id", id);
        m.addProperty("version_code", 1);
        m.addProperty("variant_id", variant);
        m.addProperty("manifest_sha256", MicrosoftTtsPackageStore.sha256(manifest));
        return m;
    }

    private static JsonArray files(Map<String, byte[]> contents) throws Exception {
        JsonArray files = new JsonArray();
        for (Map.Entry<String, byte[]> entry : contents.entrySet()) {
            JsonObject file = new JsonObject();
            file.addProperty("path", entry.getKey());
            file.addProperty("size", entry.getValue().length);
            file.addProperty("sha256", MicrosoftTtsPackageStore.sha256(entry.getValue()));
            files.add(file);
        }
        return files;
    }

    private static byte[] elf() {
        byte[] data = new byte[120];
        data[0] = 0x7f; data[1] = 'E'; data[2] = 'L'; data[3] = 'F'; data[4] = 2; data[5] = 1;
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort(18, (short) 183); b.putLong(32, 64); b.putShort(54, (short) 56);
        b.putShort(56, (short) 1); b.putInt(64, 1); b.putLong(112, 4096);
        return data;
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }

    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
