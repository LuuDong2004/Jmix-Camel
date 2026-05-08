package com.vn.transform.spi;

import java.util.Objects;

/**
 * Static metadata about a {@link TransformPlugin}. Returned to the UI catalog so users
 * can pick a plugin from a dropdown.
 */
public final class TransformDescriptor {

    private final String id;
    private final String version;
    private final String displayName;
    private final String description;
    private final String category;
    private final String spiVersion;

    private TransformDescriptor(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.version = Objects.requireNonNull(b.version, "version");
        this.displayName = b.displayName != null ? b.displayName : b.id;
        this.description = b.description != null ? b.description : "";
        this.category = b.category != null ? b.category : "general";
        this.spiVersion = b.spiVersion != null ? b.spiVersion : "1.0";
    }

    public String id() { return id; }
    public String version() { return version; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public String category() { return category; }
    public String spiVersion() { return spiVersion; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String version;
        private String displayName;
        private String description;
        private String category;
        private String spiVersion;

        public Builder id(String v)          { this.id = v; return this; }
        public Builder version(String v)     { this.version = v; return this; }
        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder category(String v)    { this.category = v; return this; }
        public Builder spiVersion(String v)  { this.spiVersion = v; return this; }

        public TransformDescriptor build() { return new TransformDescriptor(this); }
    }
}
