package com.vn.jmixcamel.transform.runtime;

import com.vn.transform.spi.TransformDescriptor;
import com.vn.transform.spi.TransformPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

import com.vn.jmixcamel.transform.runtime.PluginUploadException.Code;

/**
 * Validates an uploaded plugin JAR (manifest, plugin.properties, extensions index)
 * before handing it to {@link PluginRegistry#installPlugin(Path)}.
 *
 * <p>Workflow:
 * <ol>
 *   <li>Persist upload to a temp file inside plugins-dir.</li>
 *   <li>Inspect JAR (no class loading) — verify {@code plugin.properties} + extensions index.</li>
 *   <li>Reject duplicate ids.</li>
 *   <li>Move temp → final filename {@code <pluginId>-<version>.jar}.</li>
 *   <li>Hand path to PluginRegistry to load + start; on failure, delete the file.</li>
 * </ol>
 */
@Service
public class PluginUploadService {

    private static final Logger log = LoggerFactory.getLogger(PluginUploadService.class);
    private static final Logger AUDIT = LoggerFactory.getLogger("transform.upload.audit");

    private final PluginRegistry registry;
    private final TransformProperties props;

    public PluginUploadService(PluginRegistry registry, TransformProperties props) {
        this.registry = registry;
        this.props = props;
    }

    /**
     * Validate, install, and start a plugin from an uploaded multipart file.
     *
     * @param file uploaded JAR
     * @param uploaderName who uploaded (for audit log; pass "anonymous" if unknown)
     * @return descriptor of the newly registered plugin
     */
    public TransformDescriptor handleUpload(MultipartFile file, String uploaderName) {
        if (file == null || file.isEmpty()) {
            throw new PluginUploadException(Code.EMPTY_FILE, "no file received or file is empty");
        }
        long maxBytes = props.getUploadMaxBytes();
        if (file.getSize() > maxBytes) {
            throw new PluginUploadException(Code.FILE_TOO_LARGE,
                    "file size " + file.getSize() + " exceeds limit " + maxBytes);
        }
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase().endsWith(".jar")) {
            throw new PluginUploadException(Code.NOT_A_JAR,
                    "only .jar files are allowed (got: " + original + ")");
        }

        Path pluginsDir = Paths.get(props.getPluginsDir());
        Path tempPath;
        try {
            Files.createDirectories(pluginsDir);
            tempPath = Files.createTempFile(pluginsDir, "upload-", ".jar.tmp");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new PluginUploadException(Code.IO_ERROR, "failed to persist upload: " + e.getMessage(), e);
        }

        String checksum;
        JarMeta meta;
        try {
            checksum = sha256(tempPath);
            meta = inspectJar(tempPath);
        } catch (PluginUploadException e) {
            quietDelete(tempPath);
            throw e;
        } catch (Exception e) {
            quietDelete(tempPath);
            throw new PluginUploadException(Code.INVALID_PLUGIN_JAR,
                    "JAR inspection failed: " + e.getMessage(), e);
        }

        if (registry.hasPlugin(meta.id)) {
            quietDelete(tempPath);
            throw new PluginUploadException(Code.PLUGIN_ALREADY_EXISTS,
                    "plugin id '" + meta.id + "' is already registered — uninstall first to replace");
        }
        if (!props.isUploadAutoEnable() && !props.getEnabledPlugins().contains(meta.id)) {
            quietDelete(tempPath);
            throw new PluginUploadException(Code.PLUGIN_DISABLED,
                    "plugin id '" + meta.id + "' is not in transforms.enabled-plugins allowlist");
        }

        // Final filename: <id>-<version>.jar
        Path finalPath = pluginsDir.resolve(meta.id + "-" + meta.version + ".jar");
        if (Files.exists(finalPath)) {
            quietDelete(tempPath);
            throw new PluginUploadException(Code.PLUGIN_ALREADY_EXISTS,
                    "JAR already exists on disk: " + finalPath.getFileName());
        }
        try {
            Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            quietDelete(tempPath);
            throw new PluginUploadException(Code.IO_ERROR,
                    "failed to move JAR to plugins-dir: " + e.getMessage(), e);
        }

        InstallSummary summary;
        try {
            summary = registry.installPlugin(finalPath);
        } catch (Exception e) {
            // load failed → remove JAR so it doesn't auto-load on next restart
            quietDelete(finalPath);
            throw new PluginUploadException(Code.PLUGIN_LOAD_FAILED,
                    "PF4J failed to load plugin: " + e.getMessage(), e);
        }

        // Auto-enable in dev so the static enabled-plugins allowlist doesn't have to be edited.
        if (props.isUploadAutoEnable()) {
            registry.enableAtRuntime(summary.pluginCode());
        }

        AUDIT.info("plugin upload: uploader={} pluginCode={} version={} sha256={} bytes={} file={} transforms={} extensions={}",
                uploaderName, summary.pluginCode(), summary.version(),
                checksum, file.getSize(), finalPath.getFileName(),
                summary.hasTransform() ? 1 : 0,
                summary.extensions().size());

        // Build a descriptor for the response: prefer the TransformPlugin descriptor if any,
        // else synthesize one from the pluginCode + version.
        if (summary.hasTransform()) {
            return summary.transformPlugin().descriptor();
        }
        return TransformDescriptor.builder()
                .id(summary.pluginCode())
                .version(summary.version())
                .displayName(summary.pluginCode())
                .description(summary.extensions().size() + " extension(s) registered")
                .category("plugin-call")
                .build();
    }

    /**
     * Uninstall a plugin and delete its JAR from plugins-dir.
     */
    public void handleDelete(String id, String uploaderName) {
        if (id == null || id.isBlank()) {
            throw new PluginUploadException(Code.INVALID_PLUGIN_JAR, "plugin id required");
        }
        if (!registry.hasPlugin(id)) {
            throw new PluginUploadException(Code.INVALID_PLUGIN_JAR, "plugin not found: " + id);
        }
        registry.uninstallPlugin(id);
        registry.disableAtRuntime(id);

        // Delete any JAR in plugins-dir whose filename starts with <id>-
        Path dir = Paths.get(props.getPluginsDir());
        try (var stream = Files.newDirectoryStream(dir, id + "-*.jar")) {
            for (Path p : stream) quietDelete(p);
        } catch (IOException e) {
            log.warn("could not list plugins-dir to delete JAR for {}: {}", id, e.getMessage());
        }

        AUDIT.info("plugin delete: uploader={} id={}", uploaderName, id);
    }

    // ─── Internals ─────────────────────────────────────────────────────────────

    private record JarMeta(String id, String version, String pluginClass) {}

    private JarMeta inspectJar(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry pp = jar.getJarEntry("plugin.properties");
            if (pp == null) {
                throw new PluginUploadException(Code.INVALID_PLUGIN_JAR,
                        "JAR missing plugin.properties at root");
            }
            Properties properties = new Properties();
            try (InputStream in = jar.getInputStream(pp)) {
                properties.load(in);
            }
            String id = blankToNull(properties.getProperty("plugin.id"));
            String version = blankToNull(properties.getProperty("plugin.version"));
            String cls = blankToNull(properties.getProperty("plugin.class"));
            if (id == null || version == null || cls == null) {
                throw new PluginUploadException(Code.INVALID_PLUGIN_JAR,
                        "plugin.properties missing required keys (plugin.id, plugin.version, plugin.class)");
            }
            if (!id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
                throw new PluginUploadException(Code.INVALID_PLUGIN_JAR,
                        "plugin.id contains invalid characters: " + id);
            }

            // Need at least one of the PF4J extension indexes:
            //   META-INF/extensions.idx  (legacy/default)
            //   META-INF/extensions/*    (new format)
            JarEntry idx = jar.getJarEntry("META-INF/extensions.idx");
            boolean hasNewFormat = jar.stream()
                    .anyMatch(e -> e.getName().startsWith("META-INF/extensions/"));
            if (idx == null && !hasNewFormat) {
                throw new PluginUploadException(Code.INVALID_PLUGIN_JAR,
                        "JAR missing PF4J extensions index (META-INF/extensions.idx). " +
                        "Did the build run the PF4J annotation processor?");
            }

            return new JarMeta(id, version, cls);
        } catch (ZipException ze) {
            throw new PluginUploadException(Code.INVALID_PLUGIN_JAR,
                    "not a valid JAR (zip) archive: " + ze.getMessage(), ze);
        }
    }

    private String sha256(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            return "sha256-unavailable";
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private void quietDelete(Path p) {
        try { Files.deleteIfExists(p); } catch (Exception e) {
            log.warn("failed to delete {}: {}", p, e.getMessage());
        }
    }
}
