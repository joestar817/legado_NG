package io.legado.app.help.tts.packages;

import androidx.annotation.Keep;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;

import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 微软离线整合包安装核心。不可变对象先落盘，单个原子 registry 文件发布可见状态。
 * 首期只接收经验证的 Microsoft Embedded TTS 契约；安装不执行插件代码。
 * Android 调用方须检查 API >= 26 并在全部写入操作外持有跨进程文件锁。
 */
public final class MicrosoftTtsPackageStore {
    public static final String ENGINE = "io.legado.prototype.microsoft.engine";
    public static final String ENGINE_ID = "io.legado.prototype.microsoft";
    public static final String DATA_FORMAT = "io.legado.prototype.microsoft-embedded";
    private static final Gson GSON = new Gson();
    private static final long MAX_ARCHIVE = 2L * 1024 * 1024 * 1024;
    private static final long MAX_EXPANDED = 3L * 1024 * 1024 * 1024;
    private static final int MAX_FILES = 8192;
    private static final long MAX_JSON = 1024 * 1024;
    private final File root;
    private final File objects;
    private final File staging;
    private final File registryFile;
    private final boolean readOnly;
    private Registry registry;
    private int leases;

    @Keep
    public static final class FileSpec {
        @SerializedName("path") public String path;
        @SerializedName("size") public long size;
        @SerializedName("sha256") public String sha256;
    }
    @Keep
    public static final class Publisher {
        @SerializedName("id") public String id;
        @SerializedName("name") public String name;
    }
    @Keep
    public static final class HostApi {
        @SerializedName("major") public int major;
        @SerializedName("min_minor") public int minMinor;
    }
    @Keep
    public static final class AndroidSpec {
        @SerializedName("min_sdk") public int minSdk;
        @SerializedName("abi") public String abi;
        @SerializedName("native_page_sizes_kb") public List<Integer> pageSizes;
        @SerializedName("cpu_features") public List<String> cpuFeatures;
    }
    @Keep
    public static final class Implementation {
        @SerializedName("type") public String type;
        @SerializedName("dex_path") public String dexPath;
        @SerializedName("entry_class") public String entryClass;
        @SerializedName("native_dir") public String nativeDir;
    }
    @Keep
    public static final class DataFormat {
        @SerializedName("id") public String id;
        @SerializedName("revision") public int revision;
    }
    @Keep
    public static final class Provides {
        @SerializedName("engine_id") public String engineId;
        @SerializedName("formats") public List<DataFormat> formats;
    }
    @Keep
    public static final class Access {
        @SerializedName("network") public boolean network;
        @SerializedName("credential_slots") public List<String> credentialSlots;
    }
    @Keep
    public static final class VersionRange {
        @SerializedName("min") public long min;
        @SerializedName("max_exclusive") public long maxExclusive;
    }
    @Keep
    public static final class Dependency {
        @SerializedName("alias") public String alias;
        @SerializedName("kind") public String kind;
        @SerializedName("content_role") public String contentRole;
        @SerializedName("package_id") public String packageId;
        @SerializedName("engine_id") public String engineId;
        @SerializedName("version") public VersionRange version;
        @SerializedName("version_code") public long versionCode;
        @SerializedName("variant_id") public String variantId;
        @SerializedName("manifest_sha256") public String manifestSha256;
    }
    @Keep
    public static final class Resource {
        @SerializedName("from") public String from;
        @SerializedName("path") public String path;
    }
    @Keep
    public static final class Profile {
        @SerializedName("profile_id") public String profileId;
        @SerializedName("files") public Map<String, Resource> files;
    }
    @Keep
    public static final class Voice {
        @SerializedName("voice_id") public String voiceId;
        @SerializedName("name") public String name;
        @SerializedName("locale") public String locale;
        @SerializedName("gender") public String gender;
        @SerializedName("profile_id") public String profileId;
        @SerializedName("parameters") public Map<String, Integer> parameters;
    }
    @Keep
    public static final class Member {
        @SerializedName("path") public String path;
        @SerializedName("package_id") public String packageId;
        @SerializedName("version_code") public long versionCode;
        @SerializedName("variant_id") public String variantId;
        @SerializedName("manifest_sha256") public String manifestSha256;
    }
    @Keep
    public static final class Root {
        @SerializedName("package_id") public String packageId;
        @SerializedName("voice_ids") public List<String> voiceIds;
    }
    @Keep
    public static final class Manifest {
        @SerializedName("format") public String format;
        @SerializedName("schema_version") public int schemaVersion;
        @SerializedName("kind") public String kind;
        @SerializedName("package_id") public String packageId;
        @SerializedName("version_code") public long versionCode;
        @SerializedName("version_name") public String versionName;
        @SerializedName("variant_id") public String variantId;
        @SerializedName("name") public String name;
        @SerializedName("publisher") public Publisher publisher;
        @SerializedName("required_features") public List<String> requiredFeatures;
        @SerializedName("files") public List<FileSpec> files;
        @SerializedName("requires") public List<Dependency> requires;
        @SerializedName("host_api") public HostApi hostApi;
        @SerializedName("android") public AndroidSpec android;
        @SerializedName("implementation") public Implementation implementation;
        @SerializedName("access") public Access access;
        @SerializedName("provides") public List<Provides> provides;
        @SerializedName("content_role") public String contentRole;
        @SerializedName("engine_dependency") public String engineDependency;
        @SerializedName("data_format") public DataFormat dataFormat;
        @SerializedName("profiles") public List<Profile> profiles;
        @SerializedName("voices") public List<Voice> voices;
        @SerializedName("credentials") public Map<String, Resource> credentials;
        @SerializedName("mode") public String mode;
        @SerializedName("members") public List<Member> members;
        @SerializedName("roots") public List<Root> roots;
    }
    @Keep
    public static final class SignatureSpec {
        @SerializedName("algorithm") public String algorithm;
        @SerializedName("key_id") public String keyId;
        @SerializedName("public_key") public String publicKey;
        @SerializedName("manifest_signature") public String signature;
    }
    @Keep
    public static final class Registry {
        @SerializedName("generation") public long generation;
        @SerializedName("installed") public Set<String> installed = new LinkedHashSet<>();
        @SerializedName("active") public Map<String, String> active = new LinkedHashMap<>();
        @SerializedName("previous") public Map<String, String> previous = new LinkedHashMap<>();
        @SerializedName("trusted_publishers") public Map<String, String> trusted = new LinkedHashMap<>();
        @SerializedName("catalog") public Catalog catalog;
    }
    /** Exact directory of the last confirmed complete bundle; installed also retains in-flight revisions. */
    @Keep
    public static final class Catalog {
        @SerializedName("components") public Map<String, String> components = new LinkedHashMap<>();
        @SerializedName("voice_ids") public Set<String> voiceIds = new LinkedHashSet<>();
    }
    @Keep
    public static final class Journal {
        @SerializedName("new_objects") public List<String> newObjects = new ArrayList<>();
    }
    @Keep
    public static final class Device {
        public final String abi;
        public final int sdk;
        public final int pageSizeKb;
        public Device(String abi, int sdk, int pageSizeKb) {
            this.abi = abi; this.sdk = sdk; this.pageSizeKb = pageSizeKb;
        }
    }
    @Keep
    public static final class Installed {
        public final Manifest manifest;
        public final String digest;
        public final File directory;
        public final String signer;
        Installed(Manifest m, String d, File f, String signer) {
            manifest = m; digest = d; directory = f; this.signer = signer;
        }
        public String label() { return manifest.name + " · " + manifest.versionName + " / " + manifest.variantId; }
    }
    @Keep
    public static final class Plan implements AutoCloseable {
        public final List<Installed> packages = new ArrayList<>();
        public final Map<String, String> trustRequired = new LinkedHashMap<>();
        public final File directory;
        public final long generation;
        public String bundleName;
        public String bundlePackageId;
        public String bundleVersionName;
        public final Catalog catalog = new Catalog();
        public final List<Voice> voices = new ArrayList<>();
        public int reusable;
        public long expandedBytes;
        private boolean closed;
        Plan(File directory, long generation) { this.directory = directory; this.generation = generation; }
        @Override public void close() throws IOException {
            if (!closed) { erase(directory); closed = true; }
        }
    }

    public MicrosoftTtsPackageStore(File root) throws Exception {
        this(root, false);
    }

    public static MicrosoftTtsPackageStore readOnly(File root) throws Exception {
        return new MicrosoftTtsPackageStore(root, true);
    }

    private MicrosoftTtsPackageStore(File root, boolean readOnly) throws Exception {
        this.readOnly = readOnly;
        this.root = root.getCanonicalFile();
        objects = new File(this.root, "objects");
        staging = new File(this.root, "staging");
        registryFile = new File(this.root, "registry.json");
        if (readOnly) need(registryFile.isFile(), "离线语音包尚未安装");
        else {
            Files.createDirectories(objects.toPath());
            Files.createDirectories(staging.toPath());
        }
        registry = registryFile.exists() ? GSON.fromJson(json(read(registryFile, MAX_JSON)), Registry.class) : new Registry();
        need(registry != null && registry.installed != null && registry.active != null &&
                registry.previous != null && registry.trusted != null, "安装索引损坏，未覆盖原数据");
        if (registry.catalog != null) need(registry.catalog.components != null &&
                !registry.catalog.components.isEmpty() && registry.catalog.components.size() <= 32 &&
                registry.catalog.components.containsKey(ENGINE) && registry.catalog.voiceIds != null &&
                !registry.catalog.voiceIds.isEmpty() && registry.installed.containsAll(registry.catalog.components.values()),
                "离线语音目录损坏，未覆盖原数据");
        if (readOnly) return;
        // 上次提交若仅写入了对象而没有发布 registry，这些对象尚不可见。
        File journalFile = new File(this.root, "journal.json");
        if (journalFile.exists()) {
            Journal journal = GSON.fromJson(json(read(journalFile, MAX_JSON)), Journal.class);
            need(journal != null && journal.newObjects != null, "安装事务记录损坏");
            for (String digest : journal.newObjects) {
                hash(digest);
                if (!registry.installed.contains(digest)) erase(objectDirectory(digest));
            }
            Files.delete(journalFile.toPath());
        }
        for (File file : Objects.requireNonNull(staging.listFiles())) erase(file);
    }

    public synchronized Registry snapshot() { return GSON.fromJson(GSON.toJson(registry), Registry.class); }

    public synchronized List<Installed> installed() throws Exception {
        List<Installed> result = new ArrayList<>();
        for (String digest : registry.installed) result.add(readInstalled(digest));
        return result;
    }

    /** Metadata for the current directory, including on an unsupported device. */
    public synchronized List<Installed> catalogPackages() throws Exception {
        Map<String, Installed> current = new LinkedHashMap<>();
        if (registry.catalog != null) {
            for (Map.Entry<String, String> ref : registry.catalog.components.entrySet()) {
                Installed item = readIfInstalled(ref.getValue());
                need(item != null && ref.getKey().equals(item.manifest.packageId), "离线语音目录引用无效");
                current.put(ref.getKey(), item);
            }
        } else {
            for (Installed item : installed()) {
                Installed old = current.get(item.manifest.packageId);
                if (old == null || old.manifest.versionCode < item.manifest.versionCode) current.put(item.manifest.packageId, item);
            }
        }
        return new ArrayList<>(current.values());
    }

    public synchronized Plan inspect(File archive, Device device) throws Exception {
        return inspect(archive, device, () -> {});
    }

    public synchronized Plan inspect(File archive, Device device, Runnable checkCancellation) throws Exception {
        need(!readOnly, "朗读快照不能修改安装状态");
        File dir = new File(staging, UUID.randomUUID().toString());
        Files.createDirectories(dir.toPath());
        Plan plan = new Plan(dir, registry.generation);
        try {
            checkCancellation.run();
            Installed outer = unpack(archive, new File(dir, "outer"), MAX_EXPANDED, checkCancellation);
            need("bundle".equals(outer.manifest.kind), "请选择完整的离线整合包");
            if ("bundle".equals(outer.manifest.kind)) {
                Manifest bundle = outer.manifest;
                plan.bundleName = bundle.name;
                plan.bundlePackageId = bundle.packageId;
                plan.bundleVersionName = bundle.versionName;
                need("complete".equals(bundle.mode) && bundle.members != null && !bundle.members.isEmpty() &&
                        bundle.members.size() <= 32 && bundle.roots != null && !bundle.roots.isEmpty(), "整合包不完整");
                Set<String> memberPaths = new HashSet<>();
                for (Member member : bundle.members) {
                    checkCancellation.run();
                    need(memberPaths.add(member.path) && member.path.startsWith("packages/"), "子包路径重复或无效");
                    need(declared(bundle, member.path), "子包未列入文件清单");
                    Installed child = unpack(safe(outer.directory, member.path),
                            new File(dir, "member-" + plan.packages.size()), MAX_EXPANDED - plan.expandedBytes, checkCancellation);
                    need(!"bundle".equals(child.manifest.kind), "不支持嵌套整合包");
                    need(child.digest.equals(member.manifestSha256) && child.manifest.packageId.equals(member.packageId) &&
                            child.manifest.versionCode == member.versionCode && child.manifest.variantId.equals(member.variantId), "子包身份或清单摘要不匹配");
                    for (FileSpec f : child.manifest.files) {
                        plan.expandedBytes = Math.addExact(plan.expandedBytes, f.size);
                        need(plan.expandedBytes <= MAX_EXPANDED, "整合包展开内容超过 3 GiB 限额");
                    }
                    plan.packages.add(child);
                }
                need(memberPaths.size() == bundle.files.size(), "整合包存在未声明为成员的载荷");
                Map<String, Installed> graph = index(plan.packages);
                validateGraph(graph, device, false);
                Set<String> graphVoiceIds = new HashSet<>();
                for (Installed item : plan.packages) if ("voices".equals(item.manifest.contentRole)) {
                    for (Voice voice : item.manifest.voices)
                        need(graphVoiceIds.add(voice.voiceId), "整合包包含重复的声音 ID");
                }
                for (Installed item : plan.packages) plan.catalog.components.put(item.manifest.packageId, item.digest);
                Set<String> rootPackages = new HashSet<>();
                for (Root wanted : bundle.roots) {
                    Installed voice = graph.get(wanted.packageId);
                    need(voice != null && "voices".equals(voice.manifest.contentRole) && wanted.voiceIds != null &&
                            !wanted.voiceIds.isEmpty(), "整合包的声音入口不存在");
                    Set<String> ids = new HashSet<>();
                    for (Voice v : voice.manifest.voices) ids.add(v.voiceId);
                    need(ids.containsAll(wanted.voiceIds), "整合包引用未知声音");
                    need(rootPackages.add(wanted.packageId), "整合包声音入口重复");
                    for (String id : wanted.voiceIds) {
                        need(plan.catalog.voiceIds.add(id), "整合包包含重复的声音 ID");
                        plan.voices.add(voice.manifest.voices.stream().filter(v -> id.equals(v.voiceId)).findFirst().get());
                    }
                }
            }
            index(plan.packages);
            List<Installed> existing = installed();
            for (Installed item : plan.packages) {
                compatible(item.manifest, device);
                if (registry.installed.contains(item.digest)) plan.reusable++;
                for (Installed old : existing) {
                    if (old.manifest.packageId.equals(item.manifest.packageId)) {
                        need(old.manifest.kind.equals(item.manifest.kind), "同一包 ID 不能改变类型");
                        need(Objects.equals(old.signer, item.signer), "同一包 ID 的签名来源不同");
                        if (old.manifest.versionCode == item.manifest.versionCode && old.manifest.variantId.equals(item.manifest.variantId))
                            need(old.digest.equals(item.digest), "相同版本的内容不同，必须增加版本号");
                    }
                }
                if (item.signer != null) {
                    String trusted = registry.trusted.get(item.manifest.publisher.id);
                    need(trusted == null || trusted.equals(item.signer), "发布者签名钥匙发生变化");
                    if (trusted == null) {
                        String previous = plan.trustRequired.putIfAbsent(item.manifest.publisher.id, item.signer);
                        need(previous == null || previous.equals(item.signer), "整合包中同一发布者的签名不一致");
                    }
                }
            }
            need(root.getUsableSpace() > plan.expandedBytes + 16L * 1024 * 1024, "剩余空间不足，未更改安装状态");
            return plan;
        } catch (Exception failure) {
            plan.close();
            throw failure;
        }
    }

    public synchronized String install(Plan plan, Set<String> approvedKeyIds) throws Exception {
        need(!readOnly, "朗读快照不能修改安装状态");
        try {
            need(!plan.closed && registry.generation == plan.generation, "安装状态已变化，请重新预览");
            need(approvedKeyIds.containsAll(plan.trustRequired.values()), "请先确认引擎发布者指纹");
        } catch (Exception failure) { plan.close(); throw failure; }
        Registry next = snapshot();
        Journal journal = new Journal();
        for (Installed item : plan.packages) if (!next.installed.contains(item.digest)) journal.newObjects.add(item.digest);
        File journalFile = new File(root, "journal.json");
        boolean committed = false;
        try {
            atomic(journalFile, GSON.toJson(journal));
            for (Installed item : plan.packages) {
                File target = objectDirectory(item.digest);
                if (!target.exists()) Files.move(item.directory.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
                else verifyPayload(readInstalled(item.digest));
                next.installed.add(item.digest);
            }
            next.trusted.putAll(plan.trustRequired);
            next.catalog = GSON.fromJson(GSON.toJson(plan.catalog), Catalog.class);
            next.generation++;
            save(next);
            committed = true;
            // 已提交后清理失败只留恢复记录，不能向用户谎报“没有安装”。
            try { Files.deleteIfExists(journalFile.toPath()); } catch (IOException ignored) { }
            return "安装 " + (plan.packages.size() - plan.reusable) + " 个组件，复用 " + plan.reusable + " 个；语音目录已更新";
        } catch (Exception failure) {
            // registry 是提交点；提交后的清理失败不会把已发布对象当作孤立对象删除。
            for (String digest : journal.newObjects) if (!registry.installed.contains(digest)) erase(objectDirectory(digest));
            throw failure;
        } finally {
            try { plan.close(); } catch (IOException cleanup) { if (!committed) throw cleanup; }
        }
    }

    public synchronized Map<String, String> latestGraph(Device device) throws Exception {
        return graphForEngine(device, null);
    }

    public synchronized Map<String, String> graphForEngine(Device device, String engineDigest) throws Exception {
        if (registry.catalog != null) {
            Map<String, Installed> graph = new LinkedHashMap<>();
            for (Map.Entry<String, String> ref : registry.catalog.components.entrySet()) {
                Installed item = readIfInstalled(ref.getValue());
                need(item != null && item.manifest.packageId.equals(ref.getKey()), "离线语音目录引用无效");
                graph.put(ref.getKey(), item);
            }
            if (engineDigest != null) {
                Installed engine = readIfInstalled(engineDigest);
                need(engine != null && ENGINE.equals(engine.manifest.packageId), "指定的引擎版本未安装");
                graph.put(ENGINE, engine);
            }
            validateGraph(graph, device, true);
            selectedVoices(graph);
            Map<String, String> refs = new LinkedHashMap<>();
            for (Installed item : graph.values()) refs.put(item.manifest.packageId, item.digest);
            return refs;
        }
        // Registries written before complete-bundle replacement retain their current directory
        // until the user confirms an import; the first import publishes an exact catalog atomically.
        Map<String, Installed> latest = new LinkedHashMap<>();
        for (Installed item : installed()) {
            // Different native variants of the same engine can coexist in the store.
            if ("engine".equals(item.manifest.kind) && !engineCompatible(item.manifest, device)) continue;
            Installed old = latest.get(item.manifest.packageId);
            if (old == null || old.manifest.versionCode < item.manifest.versionCode) latest.put(item.manifest.packageId, item);
        }
        need(latest.containsKey(ENGINE), "待补引擎：请导入 Microsoft 引擎插件");
        if (engineDigest != null) {
            Installed requested = readIfInstalled(engineDigest);
            need(requested != null && ENGINE.equals(requested.manifest.packageId), "指定的引擎版本未安装");
            latest.put(ENGINE, requested);
        }
        Map<String, Installed> graph = new LinkedHashMap<>();
        graph.put(ENGINE, latest.get(ENGINE));
        for (Installed item : latest.values()) if ("voices".equals(item.manifest.contentRole)) {
            graph.put(item.manifest.packageId, item);
            for (Dependency dep : item.manifest.requires) if ("voice".equals(dep.kind)) {
                Installed exact = readIfInstalled(dep.manifestSha256);
                need(exact != null, "待补共享数据：" + dep.packageId);
                Installed old = graph.put(dep.packageId, exact);
                need(old == null || old.digest.equals(exact.digest), "多个声音要求不同共享数据版本");
            }
        }
        need(graph.values().stream().anyMatch(it -> "voices".equals(it.manifest.contentRole)), "待补语音包");
        validateGraph(graph, device, true);
        Map<String, String> refs = new LinkedHashMap<>();
        for (Installed item : graph.values()) refs.put(item.manifest.packageId, item.digest);
        return refs;
    }

    public synchronized List<Voice> catalogVoices(Map<String, String> refs) throws Exception {
        Map<String, Installed> graph = new LinkedHashMap<>();
        for (Map.Entry<String, String> ref : refs.entrySet()) {
            Installed item = readIfInstalled(ref.getValue());
            need(item != null && ref.getKey().equals(item.manifest.packageId), "离线语音目录引用无效");
            graph.put(ref.getKey(), item);
        }
        return selectedVoices(graph);
    }

    private List<Voice> selectedVoices(Map<String, Installed> graph) {
        List<Voice> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Installed item : graph.values()) if ("voices".equals(item.manifest.contentRole)) {
            for (Voice voice : item.manifest.voices) {
                if (registry.catalog != null && !registry.catalog.voiceIds.contains(voice.voiceId)) continue;
                need(ids.add(voice.voiceId), "离线语音目录包含重复的声音 ID");
                result.add(voice);
            }
        }
        need(!result.isEmpty() && (registry.catalog == null || ids.equals(registry.catalog.voiceIds)),
                "离线语音目录引用未知声音");
        return result;
    }

    public synchronized void checkGraph(Map<String, String> refs, Device device) throws Exception {
        need(refs != null && !refs.isEmpty(), "尚未启用声音");
        Map<String, Installed> graph = new LinkedHashMap<>();
        for (Map.Entry<String, String> ref : refs.entrySet()) {
            need(registry.installed.contains(ref.getValue()), "依赖尚未登记");
            Installed item = readInstalled(ref.getValue());
            need(item.manifest.packageId.equals(ref.getKey()), "安装图身份不匹配");
            verifyPayload(item);
            graph.put(ref.getKey(), item);
        }
        validateGraph(graph, device, true);
    }

    public synchronized void activate(Map<String, String> refs, long expectedGeneration, Device device) throws Exception {
        need(!readOnly, "朗读快照不能修改安装状态");
        need(leases == 0, "试听进行中，请先停止再启用");
        need(registry.generation == expectedGeneration, "检查期间安装状态已变化，请重试");
        checkGraph(refs, device);
        if (registry.active.equals(refs)) return;
        Registry next = snapshot();
        next.previous = new LinkedHashMap<>(next.active);
        next.active = new LinkedHashMap<>(refs);
        next.generation++;
        save(next);
    }

    public synchronized void rollback(long expectedGeneration, Device device) throws Exception {
        need(!readOnly, "朗读快照不能修改安装状态");
        need(leases == 0, "试听进行中，请先停止再回退");
        need(registry.generation == expectedGeneration, "检查期间安装状态已变化，请重试");
        need(!registry.previous.isEmpty(), "没有可回退版本");
        checkGraph(registry.previous, device);
        Registry next = snapshot();
        Map<String, String> old = next.active;
        next.active = next.previous;
        next.previous = old;
        next.generation++;
        save(next);
    }

    public synchronized AutoCloseable lease() {
        leases++;
        return new AutoCloseable() {
            private boolean closed;
            @Override public void close() { synchronized (MicrosoftTtsPackageStore.this) { if (!closed) { leases--; closed = true; } } }
        };
    }

    public synchronized Installed readInstalled(String digest) throws Exception {
        return runtimeObject(root, digest);
    }

    /** 工作进程只读装载；不能执行恢复/清理安装进程中的暂存事务。 */
    public static Installed runtimeObject(File packageRoot, String digest) throws Exception {
        hash(digest);
        File directory = safe(new File(packageRoot, "objects"), digest);
        byte[] bytes = read(new File(directory, "manifest.json"), MAX_JSON);
        need(sha256(bytes).equals(digest), "安装清单摘要错误");
        Manifest m = parseManifest(bytes);
        String signer = signature(directory, bytes, "engine".equals(m.kind));
        return new Installed(m, digest, directory, signer);
    }

    public synchronized Map<String, String> resources(Map<String, String> refs, String voicePackage, String voiceId) throws Exception {
        Installed item = readInstalled(refs.get(voicePackage));
        Voice voice = item.manifest.voices.stream().filter(v -> voiceId.equals(v.voiceId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("声音不存在"));
        Profile profile = item.manifest.profiles.stream().filter(p -> voice.profileId.equals(p.profileId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("声音资源配置不存在"));
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Resource> entry : profile.files.entrySet()) {
            Resource resource = entry.getValue();
            Installed provider = item;
            if (!"self".equals(resource.from)) {
                Dependency dep = item.manifest.requires.stream().filter(d -> resource.from.equals(d.alias) && "voice".equals(d.kind))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("资源引用未声明的依赖"));
                need(dep.manifestSha256.equals(refs.get(dep.packageId)), "资源不属于锁定依赖");
                provider = readInstalled(dep.manifestSha256);
            }
            need(declared(provider.manifest, resource.path), "模型文件未列入清单");
            result.put(entry.getKey(), safe(provider.directory, resource.path).getCanonicalPath());
        }
        return result;
    }

    public synchronized String credential(Map<String, String> refs, String voicePackage, String slot) throws Exception {
        Installed item = readInstalled(refs.get(voicePackage));
        Resource resource = item.manifest.credentials == null ? null : item.manifest.credentials.get(slot);
        if (resource == null) return null;
        Installed provider = item;
        if (!"self".equals(resource.from)) {
            Dependency dependency = item.manifest.requires.stream()
                    .filter(d -> resource.from.equals(d.alias) && "voice".equals(d.kind))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("许可来源未声明"));
            need(dependency.manifestSha256.equals(refs.get(dependency.packageId)), "许可不属于锁定依赖");
            provider = readInstalled(dependency.manifestSha256);
        }
        need(declared(provider.manifest, resource.path), "许可文件不在清单中");
        File file = safe(provider.directory, resource.path);
        need(file.isFile() && file.length() > 0 && file.length() <= 65536, "许可文件无效");
        return file.getCanonicalPath();
    }

    public synchronized Voice voice(Map<String, String> refs, String packageId, String voiceId) throws Exception {
        return readInstalled(refs.get(packageId)).manifest.voices.stream().filter(v -> voiceId.equals(v.voiceId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("声音不存在"));
    }

    private Installed readIfInstalled(String digest) throws Exception {
        return registry.installed.contains(digest) ? readInstalled(digest) : null;
    }

    private void validateGraph(Map<String, Installed> graph, Device device, boolean requireTrusted) throws Exception {
        for (Installed item : graph.values()) {
            Manifest m = item.manifest;
            compatible(m, device);
            if (requireTrusted && item.signer != null)
                need(item.signer.equals(registry.trusted.get(m.publisher.id)), "引擎发布者尚未信任");
            for (Dependency dep : m.requires) {
                Installed provider = graph.get(dep.packageId);
                need(provider != null, "待补依赖：" + dep.packageId);
                Manifest p = provider.manifest;
                need(dep.kind.equals(p.kind), "依赖类型不匹配");
                if ("engine".equals(dep.kind)) {
                    need(p.versionCode >= dep.version.min && p.versionCode < dep.version.maxExclusive,
                            "声音不兼容引擎版本：" + m.name);
                    need(p.provides.stream().anyMatch(v -> dep.engineId.equals(v.engineId) && v.formats.stream()
                            .anyMatch(f -> m.dataFormat.id.equals(f.id) && m.dataFormat.revision == f.revision)), "引擎数据格式不兼容");
                } else {
                    need("shared-data".equals(p.contentRole) && p.versionCode == dep.versionCode &&
                            p.variantId.equals(dep.variantId) && provider.digest.equals(dep.manifestSha256), "共享数据固定版本不匹配");
                }
            }
        }
        for (Installed item : graph.values()) if ("voices".equals(item.manifest.contentRole)) {
            for (Profile profile : item.manifest.profiles) for (Resource resource : profile.files.values()) {
                Installed source = item;
                if (!"self".equals(resource.from)) {
                    Dependency d = item.manifest.requires.stream().filter(dep -> resource.from.equals(dep.alias) && "voice".equals(dep.kind))
                            .findFirst().orElseThrow(() -> new IllegalArgumentException("资源引用未声明的共享数据"));
                    source = graph.get(d.packageId);
                }
                need(source != null && declared(source.manifest, resource.path), "资源文件不存在：" + resource.path);
            }
            if (item.manifest.credentials != null) for (Resource resource : item.manifest.credentials.values()) {
                Installed source = item;
                if (!"self".equals(resource.from)) {
                    Dependency dep = item.manifest.requires.stream().filter(d -> resource.from.equals(d.alias) && "voice".equals(d.kind))
                            .findFirst().orElseThrow(() -> new IllegalArgumentException("许可引用未声明的共享数据"));
                    source = graph.get(dep.packageId);
                }
                need(source != null && declared(source.manifest, resource.path), "许可文件不存在");
                File file = safe(source.directory, resource.path);
                need(file.isFile() && file.length() > 0 && file.length() <= 65536, "许可文件无效");
            }
        }
    }

    private static Map<String, Installed> index(List<Installed> packages) {
        Map<String, Installed> result = new LinkedHashMap<>();
        for (Installed item : packages) need(result.put(item.manifest.packageId, item) == null, "整合包包含重复的组件 ID");
        return result;
    }

    private Installed unpack(File archive, File directory, long remainingBytes, Runnable checkCancellation) throws Exception {
        need(archive.isFile() && archive.length() <= MAX_ARCHIVE, "文件不存在或超过 2 GiB 限额");
        inspectCentralDirectory(archive);
        Files.createDirectories(directory.toPath());
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry manifestEntry = zip.getEntry("manifest.json");
            need(manifestEntry != null, "不是支持的微软离线整合包：缺少清单");
            byte[] manifestBytes;
            try (InputStream in = zip.getInputStream(manifestEntry)) { manifestBytes = read(in, MAX_JSON); }
            Manifest m = parseManifest(manifestBytes);
            Map<String, FileSpec> expected = new HashMap<>();
            long total = 0;
            for (FileSpec f : m.files) { expected.put(f.path, f); total = Math.addExact(total, f.size); }
            need(total <= remainingBytes, "整合包展开内容超过 3 GiB 限额");
            need(root.getUsableSpace() > total + 16L * 1024 * 1024, "剩余空间不足，未展开包");
            Set<String> names = new HashSet<>();
            Set<String> actual = new HashSet<>();
            int count = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                checkCancellation.run();
                ZipEntry entry = entries.nextElement();
                need(++count <= MAX_FILES, "包内文件过多");
                String path = entry.getName();
                if (entry.isDirectory()) path = path.substring(0, path.length() - 1);
                safe(directory, path);
                String normalized = Normalizer.normalize(path, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
                need(names.add(normalized), "包内路径重名");
                if (entry.isDirectory()) continue;
                FileSpec spec = expected.get(path);
                long limit = "manifest.json".equals(path) || "signature.json".equals(path) ? MAX_JSON : spec == null ? -1 : spec.size;
                need(limit >= 0, "包内存在清单外文件：" + path);
                File target = safe(directory, path);
                Files.createDirectories(target.getParentFile().toPath());
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long written = 0;
                try (InputStream in = zip.getInputStream(entry); FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[64 * 1024]; int n;
                    while ((n = in.read(buffer)) != -1) {
                        checkCancellation.run();
                        written += n; need(written <= limit, "文件实际大小超过声明");
                        digest.update(buffer, 0, n); out.write(buffer, 0, n);
                    }
                    out.getFD().sync();
                }
                if (spec != null) {
                    need(written == spec.size && hex(digest.digest()).equals(spec.sha256), "文件大小或摘要错误：" + path);
                    actual.add(path);
                    if ("voice".equals(m.kind)) rejectCode(target, path);
                }
            }
            need(actual.equals(expected.keySet()), "清单中有文件未收录");
            String signer = signature(directory, manifestBytes, "engine".equals(m.kind));
            Installed item = new Installed(m, sha256(manifestBytes), directory, signer);
            if ("engine".equals(m.kind)) verifyElfLibraries(item);
            return item;
        }
    }

    private static Manifest parseManifest(byte[] bytes) throws Exception {
        JsonObject obj = json(bytes).getAsJsonObject();
        integer(obj, "schema_version"); integer(obj, "version_code");
        need(obj.has("files") && obj.get("files").isJsonArray(), "缺少文件清单");
        for (JsonElement e : obj.getAsJsonArray("files")) integer(e.getAsJsonObject(), "size");
        Manifest m = GSON.fromJson(obj, Manifest.class);
        need("ng-tts-package".equals(m.format) && m.schemaVersion == 1, "不支持的包格式");
        need(Arrays.asList("engine", "voice", "bundle").contains(m.kind), "未知包类型");
        need(m.packageId != null && m.packageId.length() <= 256 &&
                m.packageId.matches("io\\.legado\\.prototype\\.microsoft\\.[a-z0-9._-]+"), "不支持的微软离线包标识");
        need(m.versionCode > 0 && notBlank(m.versionName) && notBlank(m.name) && notBlank(m.variantId), "包身份不完整");
        need(m.publisher != null && notBlank(m.publisher.id) && notBlank(m.publisher.name), "发布者缺失");
        need(m.requiredFeatures != null && m.requiredFeatures.isEmpty(), "不支持该格式扩展");
        need(m.files != null && m.files.size() <= MAX_FILES, "文件清单过大");
        Set<String> paths = new HashSet<>();
        for (FileSpec f : m.files) {
            need(f != null && f.size >= 0 && f.size <= MAX_ARCHIVE, "文件大小超过限额");
            hash(f.sha256); safe(new File("."), f.path);
            need(!"manifest.json".equals(f.path) && !"signature.json".equals(f.path) && paths.add(f.path), "重复或保留清单路径");
        }
        if ("bundle".equals(m.kind)) return m;
        need(m.requires != null && m.requires.size() <= 16, "依赖声明缺失或过多");
        if ("engine".equals(m.kind)) {
            need(ENGINE.equals(m.packageId) && m.requires.isEmpty(), "引擎包依赖声明不受支持");
            need(m.hostApi != null && m.hostApi.major == 1 && m.hostApi.minMinor == 0, "宿主接口不兼容");
            need(m.android != null && m.android.minSdk >= 26 && m.android.pageSizes != null && m.android.cpuFeatures != null && m.android.cpuFeatures.isEmpty(), "Android 条件不完整");
            need(m.implementation != null && "dex-jni".equals(m.implementation.type) &&
                    "io.legado.microsoft.plugin.MicrosoftEngine".equals(m.implementation.entryClass), "未知插件入口");
            need(declared(m, m.implementation.dexPath) && m.implementation.nativeDir != null &&
                    m.implementation.nativeDir.equals("native/" + m.android.abi), "代码或库目录错误");
            need(m.access != null && !m.access.network && m.access.credentialSlots != null && m.access.credentialSlots.equals(Collections.singletonList("model_license")), "不支持该引擎的权限或许可配置");
            need(m.provides != null && m.provides.size() == 1 && ENGINE_ID.equals(m.provides.get(0).engineId) &&
                    m.provides.get(0).formats != null && m.provides.get(0).formats.size() == 1 &&
                    DATA_FORMAT.equals(m.provides.get(0).formats.get(0).id) && m.provides.get(0).formats.get(0).revision == 1, "引擎与语音包格式不匹配");
        } else {
            need("common".equals(m.variantId), "语音包变体不受支持");
            need(m.dataFormat != null && DATA_FORMAT.equals(m.dataFormat.id) && m.dataFormat.revision == 1, "不支持的微软语音数据格式");
            need(Arrays.asList("voices", "shared-data").contains(m.contentRole) && m.voices != null && m.profiles != null, "语音数据声明不完整");
            Set<String> aliases = new HashSet<>(); int engines = 0;
            for (Dependency d : m.requires) {
                need(d != null && notBlank(d.alias) && aliases.add(d.alias), "依赖别名缺失或重复");
                if ("engine".equals(d.kind)) {
                    engines++;
                    need(ENGINE.equals(d.packageId) && ENGINE_ID.equals(d.engineId) && d.alias.equals(m.engineDependency) &&
                            d.version != null && d.version.min > 0 && d.version.maxExclusive > d.version.min, "引擎依赖无效");
                } else {
                    need("voice".equals(d.kind) && "shared-data".equals(d.contentRole) && "voices".equals(m.contentRole) &&
                            notBlank(d.packageId) && d.versionCode > 0 && "common".equals(d.variantId), "不支持该共享依赖");
                    hash(d.manifestSha256);
                }
            }
            need(engines == 1, "语音包必须恰好依赖一个引擎");
            if ("shared-data".equals(m.contentRole)) need(m.voices.isEmpty() && m.profiles.isEmpty() && m.requires.size() == 1 &&
                    (m.credentials == null || m.credentials.isEmpty()), "共享数据不能再嵌套共享数据或许可槽");
            else {
                need(!m.voices.isEmpty() && !m.profiles.isEmpty(), "声音或资源配置为空");
                if (m.credentials != null) {
                    need(m.credentials.keySet().equals(Collections.singleton("model_license")), "不支持该语音包的许可槽");
                    for (Resource resource : m.credentials.values()) {
                        need(resource != null && ("self".equals(resource.from) || aliases.contains(resource.from)), "许可来源无效");
                        safe(new File("."), resource.path);
                    }
                }
                Set<String> profiles = new HashSet<>(); Set<String> voices = new HashSet<>();
                for (Profile p : m.profiles) {
                    need(p != null && notBlank(p.profileId) && profiles.add(p.profileId) && p.files != null &&
                            p.files.keySet().containsAll(Arrays.asList("model", "voice_config")), "Embedded TTS 资源配置不完整");
                    need(new HashSet<>(Arrays.asList("model", "voice_config")).containsAll(p.files.keySet()), "未知语音资源类型");
                    for (Resource r : p.files.values()) { need(r != null && ("self".equals(r.from) || aliases.contains(r.from)), "资源来源无效"); safe(new File("."), r.path); }
                }
                for (Voice v : m.voices) need(v != null && notBlank(v.voiceId) && voices.add(v.voiceId) && notBlank(v.name) &&
                        profiles.contains(v.profileId) && v.parameters != null && v.parameters.size() == 1 &&
                        v.parameters.get("speaker_id") != null && v.parameters.get("speaker_id") >= 0, "声音声明无效");
            }
        }
        return m;
    }

    private static void compatible(Manifest m, Device d) {
        if (!"engine".equals(m.kind)) return;
        need(engineCompatible(m, d),
                "引擎与设备架构、Android 版本或页大小不兼容");
    }

    private static boolean engineCompatible(Manifest m, Device d) {
        return d.sdk >= m.android.minSdk && ("*".equals(d.abi) || d.abi.equals(m.android.abi)) &&
                m.android.pageSizes.contains(d.pageSizeKb);
    }

    private static String signature(File dir, byte[] manifest, boolean required) throws Exception {
        File file = new File(dir, "signature.json");
        if (!file.isFile()) { need(!required, "引擎插件缺少发布者签名"); return null; }
        SignatureSpec s = GSON.fromJson(json(read(file, 16384)), SignatureSpec.class);
        need(s != null && "ed25519".equals(s.algorithm), "未知签名算法");
        byte[] key = Base64.getDecoder().decode(s.publicKey);
        byte[] sig = Base64.getDecoder().decode(s.signature);
        need(key.length == 32 && sig.length == 64 && sha256(key).equals(s.keyId), "签名公钥或指纹无效");
        EdDSAEngine verifier = new EdDSAEngine(MessageDigest.getInstance("SHA-512"));
        verifier.initVerify(new EdDSAPublicKey(new EdDSAPublicKeySpec(key, EdDSANamedCurveTable.getByName("Ed25519"))));
        verifier.update(manifest);
        need(verifier.verify(sig), "清单签名校验失败");
        return s.keyId;
    }

    public static void verifyPayload(Installed item) throws Exception {
        for (FileSpec f : item.manifest.files) {
            File file = safe(item.directory, f.path);
            need(file.isFile() && file.length() == f.size && sha256(file).equals(f.sha256), "安装内容损坏：" + f.path);
        }
        if ("engine".equals(item.manifest.kind)) verifyElfLibraries(item);
    }

    private static void verifyElfLibraries(Installed item) throws Exception {
        int libraries = 0;
        for (FileSpec f : item.manifest.files) if (f.path.startsWith(item.manifest.implementation.nativeDir + "/")) {
            libraries++;
            need(f.path.endsWith(".so") && f.path.substring(item.manifest.implementation.nativeDir.length() + 1).indexOf('/') < 0, "原生库目录不允许额外载荷");
            try (RandomAccessFile in = new RandomAccessFile(safe(item.directory, f.path), "r")) {
                byte[] elf = new byte[64]; in.readFully(elf);
                need(elf[0] == 0x7f && elf[1] == 'E' && elf[2] == 'L' && elf[3] == 'F' && elf[4] == 2 && elf[5] == 1, "只支持 64 位小端 ELF");
                int machine = (elf[18] & 255) | (elf[19] & 255) << 8;
                need(("arm64-v8a".equals(item.manifest.android.abi) && machine == 183) ||
                        ("x86_64".equals(item.manifest.android.abi) && machine == 62), "ELF 架构与清单不一致");
                ByteBuffer h = ByteBuffer.wrap(elf).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                long phoff = h.getLong(32); int phsize = h.getShort(54) & 65535; int phnum = h.getShort(56) & 65535;
                need(phsize >= 56 && phnum > 0 && phnum <= 1024 && phoff >= 64 && phoff + (long) phsize * phnum <= in.length(), "ELF 段表无效");
                for (int i = 0; i < phnum; i++) {
                    in.seek(phoff + (long) i * phsize); byte[] ph = new byte[56]; in.readFully(ph);
                    ByteBuffer p = ByteBuffer.wrap(ph).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    if (p.getInt(0) == 1) for (int page : item.manifest.android.pageSizes) {
                        need((page == 4 || page == 16) && p.getLong(48) >= page * 1024L &&
                                Math.floorMod(p.getLong(8) - p.getLong(16), page * 1024L) == 0, "ELF 页对齐与清单不一致");
                    }
                }
            }
        }
        need(libraries > 0, "引擎缺少原生库");
    }

    private static void rejectCode(File file, String path) throws Exception {
        String lower = path.toLowerCase(Locale.ROOT);
        need(!(lower.endsWith(".so") || lower.endsWith(".dex") || lower.endsWith(".jar") || lower.endsWith(".apk") || lower.endsWith(".class") || lower.endsWith(".zip")), "语音包含可执行或嵌套归档内容");
        try (InputStream in = new FileInputStream(file)) {
            byte[] magic = new byte[4]; int n = in.read(magic);
            if (n == 4) need(!(magic[0] == 0x7f && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F') &&
                    !(magic[0] == 'd' && magic[1] == 'e' && magic[2] == 'x') && !(magic[0] == 'P' && magic[1] == 'K' && ((magic[2] == 3 && magic[3] == 4) || (magic[2] == 5 && magic[3] == 6) || (magic[2] == 7 && magic[3] == 8))), "数据载荷实际包含执行/归档文件");
        }
    }

    /** 原型拒绝 ZIP64/加密/特殊文件；先读中央目录才能检查 Unix 符号链接类型。 */
    private static void inspectCentralDirectory(File file) throws Exception {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long start = Math.max(0, in.length() - 65557); long eocd = -1;
            for (long at = in.length() - 22; at >= start; at--) {
                in.seek(at);
                if (u32(in) == 0x06054b50L) { in.seek(at + 20); if (at + 22 + u16(in) == in.length()) { eocd = at; break; } }
            }
            need(eocd >= 0, "ZIP 中央目录缺失");
            in.seek(eocd + 4); need(u16(in) == 0 && u16(in) == 0, "不支持分卷 ZIP");
            int diskCount = u16(in), count = u16(in); long size = u32(in), offset = u32(in);
            need(count == diskCount && count < 65535 && count <= MAX_FILES && offset != 0xffffffffL && size != 0xffffffffL && offset + size == eocd,
                    "只支持普通单卷 ZIP，暂不支持 ZIP64");
            in.seek(offset);
            for (int i = 0; i < count; i++) {
                need(u32(in) == 0x02014b50L, "ZIP 中央目录损坏");
                byte[] c = new byte[42]; in.readFully(c);
                int flags = (c[4] & 255) | (c[5] & 255) << 8;
                int method = (c[6] & 255) | (c[7] & 255) << 8;
                int nameLength = (c[24] & 255) | (c[25] & 255) << 8;
                int extraLength = (c[26] & 255) | (c[27] & 255) << 8;
                int commentLength = (c[28] & 255) | (c[29] & 255) << 8;
                int mode = (c[36] & 255) | (c[37] & 255) << 8;
                need((flags & 1) == 0 && (method == 0 || method == 8), "ZIP 加密或压缩方法不支持");
                need((mode & 0170000) == 0 || (mode & 0170000) == 0100000 || (mode & 0170000) == 0040000, "ZIP 含链接或特殊文件");
                in.seek(in.getFilePointer() + nameLength + extraLength + commentLength);
            }
            need(in.getFilePointer() == eocd, "ZIP 目录长度不一致");
        }
    }
    private static int u16(RandomAccessFile f) throws IOException { return f.readUnsignedByte() | f.readUnsignedByte() << 8; }
    private static long u32(RandomAccessFile f) throws IOException { return (long) u16(f) | (long) u16(f) << 16; }

    private File objectDirectory(String digest) throws IOException { hash(digest); return safe(objects, digest); }
    private static boolean declared(Manifest m, String path) { return m.files.stream().anyMatch(f -> f.path.equals(path)); }
    public static File safe(File root, String path) throws IOException {
        need(path != null && !path.isEmpty() && !path.startsWith("/") && path.indexOf('\\') < 0 && path.indexOf(':') < 0 && path.indexOf('\0') < 0, "包路径无效");
        need(path.chars().noneMatch(c -> c < 32 || c == 127), "包路径包含控制字符");
        for (String part : path.split("/", -1)) need(!part.isEmpty() && !part.equals(".") && !part.equals(".."), "包路径越界");
        File target = new File(root, path).getCanonicalFile();
        String prefix = root.getCanonicalPath();
        if (!prefix.endsWith(File.separator)) prefix += File.separator;
        need(target.getPath().startsWith(prefix), "包路径越界");
        return target;
    }
    private void save(Registry next) throws Exception { atomic(registryFile, GSON.toJson(next)); registry = next; }
    private static void atomic(File file, String value) throws Exception {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) { out.write(value.getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    public static String sha256(byte[] bytes) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    public static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) != -1) digest.update(b, 0, n); }
        return hex(digest.digest());
    }
    private static String hex(byte[] bytes) { StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b & 255)); return s.toString(); }
    private static void hash(String value) { need(value != null && value.matches("[0-9a-f]{64}"), "摘要格式无效"); }
    private static boolean notBlank(String value) { return value != null && !value.trim().isEmpty(); }
    private static void need(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    public static byte[] read(File file, long limit) throws Exception { try (InputStream in = new FileInputStream(file)) { return read(in, limit); } }
    private static byte[] read(InputStream in, long limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) { need((long) out.size() + n <= limit, "配置文件过大"); out.write(buffer, 0, n); }
        return out.toByteArray();
    }
    private static JsonElement json(byte[] bytes) throws Exception {
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement result = value(reader, 0);
            need(reader.peek() == com.google.gson.stream.JsonToken.END_DOCUMENT, "JSON 存在多余内容");
            return result;
        }
    }
    private static JsonElement value(JsonReader r, int depth) throws Exception {
        need(depth < 32, "JSON 嵌套过深");
        switch (r.peek()) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject(); r.beginObject();
                while (r.hasNext()) { String name = r.nextName(); need(!object.has(name), "JSON 字段重复：" + name); object.add(name, value(r, depth + 1)); }
                r.endObject(); return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray(); r.beginArray(); while (r.hasNext()) array.add(value(r, depth + 1)); r.endArray(); return array;
            case STRING: return new JsonPrimitive(r.nextString());
            case NUMBER: return new JsonPrimitive(new BigDecimal(r.nextString()));
            case BOOLEAN: return new JsonPrimitive(r.nextBoolean());
            case NULL: r.nextNull(); return JsonNull.INSTANCE;
            default: throw new IllegalArgumentException("JSON 类型无效");
        }
    }
    private static void integer(JsonObject object, String field) {
        JsonElement e = object.get(field);
        need(e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() && e.toString().matches("0|[1-9][0-9]*") &&
                new BigDecimal(e.toString()).compareTo(new BigDecimal("9007199254740991")) <= 0, "整数值无效：" + field);
    }
    private static void erase(File file) throws IOException {
        if (!file.exists()) return;
        if (Files.isSymbolicLink(file.toPath())) { Files.delete(file.toPath()); return; }
        if (file.isDirectory()) { File[] children = file.listFiles(); if (children == null) throw new IOException("无法读取临时目录"); for (File child : children) erase(child); }
        Files.delete(file.toPath());
    }
}
