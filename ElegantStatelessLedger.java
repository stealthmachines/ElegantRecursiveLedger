import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

/**
 * ElegantStatelessLedger - Refined for maximum elegance with functional composition
 */
public final class ElegantStatelessLedger {
    private static final Logger LOGGER = Logger.getLogger(ElegantStatelessLedger.class.getName());

    private final List<LedgerEntry> entries;
    private final List<Checkpoint> checkpoints;
    private final Map<UUID, LedgerEntry> entryIndex;
    private final CryptoDNA64 crypto;
    private final PersistentMerkleTree merkleTree;
    private final ReentrantReadWriteLock lock;
    private final Configuration config;

    // Private constructor enforces builder pattern
    private ElegantStatelessLedger(Configuration config,
                                   List<LedgerEntry> entries,
                                   List<Checkpoint> checkpoints,
                                   PersistentMerkleTree merkleTree) {
        this.config = config;
        this.entries = List.copyOf(entries);
        this.checkpoints = List.copyOf(checkpoints);
        this.entryIndex = buildEntryIndex(entries);
        this.crypto = new CryptoDNA64();
        this.merkleTree = merkleTree;
        this.lock = new ReentrantReadWriteLock();
        
        LOGGER.fine(() -> String.format("Initialized ledger with %d entries, %d checkpoints", 
            entries.size(), checkpoints.size()));
    }

    // Elegant index building with streams
    private static Map<UUID, LedgerEntry> buildEntryIndex(List<LedgerEntry> entries) {
        return entries.stream().collect(
            () -> new HashMap<>(),
            (map, entry) -> map.put(entry.getId(), entry),
            HashMap::putAll
        );
    }

    // --- Enhanced Configuration ---
    public record Configuration(int autoCheckpointInterval, boolean enableVerboseLogging) {
        public Configuration {
            if (autoCheckpointInterval <= 0) {
                throw new IllegalArgumentException("Checkpoint interval must be positive");
            }
        }

        public static Configuration defaultConfig() {
            return new Configuration(10, false);
        }

        public Configuration withCheckpointInterval(int interval) {
            return new Configuration(interval, enableVerboseLogging);
        }

        public Configuration withVerboseLogging(boolean enabled) {
            return new Configuration(autoCheckpointInterval, enabled);
        }
    }

    // --- Fluent Builder ---
    public static final class Builder {
        private Configuration config = Configuration.defaultConfig();

        public Builder withConfig(Configuration config) {
            this.config = Objects.requireNonNull(config, "Configuration cannot be null");
            return this;
        }

        public Builder withCheckpointInterval(int interval) {
            this.config = config.withCheckpointInterval(interval);
            return this;
        }

        public Builder withVerboseLogging(boolean enabled) {
            this.config = config.withVerboseLogging(enabled);
            return this;
        }

        public ElegantStatelessLedger build() {
            return new ElegantStatelessLedger(config, List.of(), List.of(), 
                new PersistentMerkleTree(new CryptoDNA64()));
        }
    }

    // Factory methods
    public static Builder builder() { return new Builder(); }
    public static ElegantStatelessLedger create() { return builder().build(); }
    public static ElegantStatelessLedger withInterval(int interval) { 
        return builder().withCheckpointInterval(interval).build(); 
    }

    // --- Enhanced LedgerEntry ---
    public static final class LedgerEntry {
        private final UUID id;
        private final String glyphData;
        private final String dnaHash;
        private final long timestamp;
        private final String salt;
        private final int dataSize;

        private LedgerEntry(String glyphData, String dnaHash, String salt) {
            this.id = UUID.randomUUID();
            this.glyphData = Objects.requireNonNull(glyphData, "Glyph data cannot be null");
            this.dnaHash = Objects.requireNonNull(dnaHash, "DNA hash cannot be null");
            this.salt = Objects.requireNonNull(salt, "Salt cannot be null");
            this.timestamp = System.currentTimeMillis();
            this.dataSize = glyphData.length();
        }

        static LedgerEntry create(String glyphData, String dnaHash, String salt) {
            return new LedgerEntry(glyphData, dnaHash, salt);
        }

        // Getters
        public UUID getId() { return id; }
        public String getGlyphData() { return glyphData; }
        public String getDnaHash() { return dnaHash; }
        public long getTimestamp() { return timestamp; }
        public String getSalt() { return salt; }
        public int getDataSize() { return dataSize; }

        // Fluent validation and utility methods
        public boolean isValid(CryptoDNA64 crypto) {
            return crypto.validateGlyph(glyphData, salt, dnaHash);
        }

        public long ageInMillis() { 
            return System.currentTimeMillis() - timestamp; 
        }

        public boolean isOlderThan(long millis) { 
            return ageInMillis() > millis; 
        }

        public boolean isLargerThan(int bytes) { 
            return dataSize > bytes; 
        }

        // Elegant string representation
        public String toShortString() {
            return String.format("%s[%s...]", 
                id.toString().substring(0, 8),
                dnaHash.substring(0, Math.min(6, dnaHash.length())));
        }

        @Override
        public String toString() {
            return String.format("LedgerEntry{id=%s, size=%db, age=%dms, hash=%s}",
                toShortString(), dataSize, ageInMillis(), 
                dnaHash.substring(0, Math.min(8, dnaHash.length())) + "...");
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof LedgerEntry that && id.equals(that.id));
        }

        @Override
        public int hashCode() { return Objects.hash(id); }
    }

    // --- Enhanced Checkpoint ---
    public static final class Checkpoint {
        private final UUID id;
        private final String merkleRoot;
        private final List<UUID> entryIds;
        private final long timestamp;
        private final CheckpointMetadata metadata;

        private Checkpoint(String merkleRoot, List<UUID> entryIds) {
            this.id = UUID.randomUUID();
            this.merkleRoot = Objects.requireNonNull(merkleRoot, "Merkle root cannot be null");
            this.entryIds = List.copyOf(entryIds);
            this.timestamp = System.currentTimeMillis();
            this.metadata = new CheckpointMetadata(entryIds.size(), merkleRoot.length());
        }

        static Checkpoint create(String merkleRoot, List<UUID> entryIds) {
            return new Checkpoint(merkleRoot, entryIds);
        }

        // Nested metadata for cleaner organization
        public record CheckpointMetadata(int entryCount, int rootHashLength) {
            public boolean isEmpty() { return entryCount == 0; }
            public boolean isLarge() { return entryCount > 1000; }
        }

        // Getters
        public UUID getId() { return id; }
        public String getMerkleRoot() { return merkleRoot; }
        public List<UUID> getEntryIds() { return entryIds; }
        public long getTimestamp() { return timestamp; }
        public CheckpointMetadata getMetadata() { return metadata; }

        // Convenience delegates
        public int getEntryCount() { return metadata.entryCount(); }

        // Fluent query methods
        public long ageInMillis() { return System.currentTimeMillis() - timestamp; }
        public boolean isOlderThan(long millis) { return ageInMillis() > millis; }
        public boolean contains(UUID entryId) { return entryIds.contains(entryId); }
        public boolean isEmpty() { return metadata.isEmpty(); }
        public boolean isLarge() { return metadata.isLarge(); }

        // Stream-based queries
        public Stream<UUID> streamEntryIds() { return entryIds.stream(); }

        @Override
        public String toString() {
            return String.format("Checkpoint{id=%s, entries=%d, age=%dms}",
                id.toString().substring(0, 8), getEntryCount(), ageInMillis());
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof Checkpoint that && id.equals(that.id));
        }

        @Override
        public int hashCode() { return Objects.hash(id); }
    }

    // --- Thread-Safe Crypto with Enhanced Methods ---
    public static final class CryptoDNA64 {
        private final SecureRandom secureRandom;
        private final ThreadLocal<MessageDigest> threadDigest;

        public CryptoDNA64() {
            this.secureRandom = new SecureRandom();
            this.threadDigest = ThreadLocal.withInitial(() -> {
                try { 
                    return MessageDigest.getInstance("SHA-256"); 
                } catch (NoSuchAlgorithmException e) { 
                    throw new RuntimeException("SHA-256 not available", e); 
                }
            });
        }

        public String generateSalt() {
            return bytesToHex(secureRandom.generateSeed(16));
        }

        public String computeDNAHash(String data, String salt) {
            Objects.requireNonNull(data, "Data cannot be null");
            Objects.requireNonNull(salt, "Salt cannot be null");
            
            MessageDigest digest = threadDigest.get();
            digest.reset();
            return bytesToHex(digest.digest((data + salt).getBytes(StandardCharsets.UTF_8)));
        }

        public boolean validateGlyph(String data, String salt, String expectedHash) {
            return computeDNAHash(data, salt).equals(expectedHash);
        }

        // Deterministic hash combination for merkle trees
        public String combineHashes(String left, String right) {
            Objects.requireNonNull(left, "Left hash cannot be null");
            Objects.requireNonNull(right, "Right hash cannot be null");
            
            return left.compareTo(right) <= 0 
                ? computeDNAHash(left, right) 
                : computeDNAHash(right, left);
        }

        // Batch validation for efficiency
        public boolean validateEntries(List<LedgerEntry> entries) {
            return entries.stream().allMatch(entry -> 
                validateGlyph(entry.getGlyphData(), entry.getSalt(), entry.getDnaHash())
            );
        }

        private static String bytesToHex(byte[] bytes) {
            return HexFormat.of().formatHex(bytes);
        }
    }

    // --- Persistent Merkle Tree with Enhanced Navigation ---
    public static final class PersistentMerkleTree {
        public record Node(String hash, Node left, Node right, int depth) {
            public Node(String hash) { 
                this(hash, null, null, 0); 
            }
            
            public Node(String hash, Node left, Node right) {
                this(hash, left, right, Math.max(
                    left != null ? left.depth : -1, 
                    right != null ? right.depth : -1
                ) + 1);
            }
            
            public boolean isLeaf() { return left == null && right == null; }
            public boolean isRoot() { return depth == 0 && !isLeaf(); }
            public int getSubtreeSize() { 
                return isLeaf() ? 1 : 
                    (left != null ? left.getSubtreeSize() : 0) + 
                    (right != null ? right.getSubtreeSize() : 0);
            }
        }

        private final CryptoDNA64 crypto;
        private final Node root;
        private final List<Node> leaves;

        public PersistentMerkleTree(CryptoDNA64 crypto) {
            this.crypto = Objects.requireNonNull(crypto, "Crypto cannot be null");
            this.root = null;
            this.leaves = List.of();
        }

        private PersistentMerkleTree(CryptoDNA64 crypto, Node root, List<Node> leaves) {
            this.crypto = crypto;
            this.root = root;
            this.leaves = List.copyOf(leaves);
        }

        // Immutable leaf addition
        public PersistentMerkleTree addLeaf(String hash) {
            Objects.requireNonNull(hash, "Hash cannot be null");
            
            List<Node> newLeaves = Stream.concat(
                leaves.stream(), 
                Stream.of(new Node(hash))
            ).toList();
            
            Node newRoot = buildTree(newLeaves);
            return new PersistentMerkleTree(crypto, newRoot, newLeaves);
        }

        private Node buildTree(List<Node> nodes) {
            return switch (nodes.size()) {
                case 0 -> null;
                case 1 -> nodes.get(0);
                default -> {
                    List<Node> nextLevel = new ArrayList<>();
                    for (int i = 0; i < nodes.size(); i += 2) {
                        Node left = nodes.get(i);
                        Node right = (i + 1 < nodes.size()) ? nodes.get(i + 1) : left;
                        String combinedHash = crypto.combineHashes(left.hash, right.hash);
                        nextLevel.add(new Node(combinedHash, left, right));
                    }
                    yield buildTree(nextLevel);
                }
            };
        }

        // Enhanced accessors
        public Optional<String> getRootHash() {
            return Optional.ofNullable(root).map(Node::hash);
        }

        public Optional<Node> getRootNode() { 
            return Optional.ofNullable(root); 
        }

        public List<Node> getLeaves() { return leaves; }
        public boolean isEmpty() { return leaves.isEmpty(); }
        public int size() { return leaves.size(); }
        public int depth() { return root != null ? root.depth : 0; }

        // Tree statistics
        public TreeStats getStats() {
            return new TreeStats(size(), depth(), 
                getRootHash().map(String::length).orElse(0));
        }

        public record TreeStats(int leafCount, int depth, int rootHashLength) {
            public boolean isEmpty() { return leafCount == 0; }
            public boolean isBalanced() { 
                return leafCount == 0 || Math.abs(Math.log(leafCount) / Math.log(2) - depth) < 2;
            }
            public double efficiency() {
                return leafCount > 0 ? (double) leafCount / Math.pow(2, depth) : 0.0;
            }
        }
    }

    // --- Core Ledger Operations ---
    public ElegantStatelessLedger addEntry(String glyphData) {
        validateGlyphData(glyphData);

        return withWriteLock(() -> {
            // Create new entry
            LedgerEntry entry = createValidatedEntry(glyphData);
            
            // Build new state immutably
            List<LedgerEntry> newEntries = appendEntry(entry);
            PersistentMerkleTree newTree = merkleTree.addLeaf(entry.getDnaHash());
            List<Checkpoint> newCheckpoints = maybeCreateCheckpoint(newEntries, newTree);

            logEntryAddition(entry, newCheckpoints.size() > checkpoints.size());
            
            return new ElegantStatelessLedger(config, newEntries, newCheckpoints, newTree);
        });
    }

    private void validateGlyphData(String glyphData) {
        Objects.requireNonNull(glyphData, "Glyph data cannot be null");
        if (glyphData.isBlank()) {
            throw new IllegalArgumentException("Glyph data cannot be empty");
        }
    }

    private LedgerEntry createValidatedEntry(String glyphData) {
        String salt = crypto.generateSalt();
        String dnaHash = crypto.computeDNAHash(glyphData, salt);
        return LedgerEntry.create(glyphData, dnaHash, salt);
    }

    private List<LedgerEntry> appendEntry(LedgerEntry entry) {
        return Stream.concat(entries.stream(), Stream.of(entry)).toList();
    }

    private List<Checkpoint> maybeCreateCheckpoint(List<LedgerEntry> newEntries, PersistentMerkleTree newTree) {
        if (newEntries.size() % config.autoCheckpointInterval() != 0) {
            return checkpoints;
        }

        String rootHash = newTree.getRootHash().orElse("");
        List<UUID> entryIds = newEntries.stream().map(LedgerEntry::getId).toList();
        Checkpoint checkpoint = Checkpoint.create(rootHash, entryIds);
        
        return Stream.concat(checkpoints.stream(), Stream.of(checkpoint)).toList();
    }

    private void logEntryAddition(LedgerEntry entry, boolean checkpointCreated) {
        if (config.enableVerboseLogging()) {
            LOGGER.info(() -> String.format("Added %s%s", 
                entry.toShortString(), 
                checkpointCreated ? " (checkpoint created)" : ""));
        } else {
            LOGGER.fine(() -> "Added entry: " + entry.toShortString());
        }
    }

    // --- Query and Analysis Methods ---
    public <T> T transformState(Function<Stream<LedgerEntry>, T> transformer) {
        Objects.requireNonNull(transformer, "Transformer cannot be null");
        return withReadLock(() -> transformer.apply(entries.stream()));
    }

    // Enhanced finders with type safety
    public Optional<LedgerEntry> findEntry(UUID entryId) {
        return withReadLock(() -> Optional.ofNullable(entryIndex.get(entryId)));
    }

    public Optional<Checkpoint> findCheckpoint(UUID checkpointId) {
        return withReadLock(() -> 
            checkpoints.stream()
                .filter(cp -> cp.getId().equals(checkpointId))
                .findFirst()
        );
    }

    // Batch queries
    public List<LedgerEntry> findEntriesOlderThan(long millis) {
        return withReadLock(() ->
            entries.stream()
                .filter(entry -> entry.isOlderThan(millis))
                .toList()
        );
    }

    public List<Checkpoint> findCheckpointsOlderThan(long millis) {
        return withReadLock(() ->
            checkpoints.stream()
                .filter(cp -> cp.isOlderThan(millis))
                .toList()
        );
    }

    // Stream-based access for functional programming
    public Stream<LedgerEntry> streamEntries() { 
        return entries.stream(); 
    }
    
    public Stream<Checkpoint> streamCheckpoints() { 
        return checkpoints.stream(); 
    }

    // Collection accessors (immutable views)
    public List<LedgerEntry> getEntries() { return entries; }
    public List<Checkpoint> getCheckpoints() { return checkpoints; }

    // --- Statistics and Analysis ---
    public LedgerStats getStats() {
        return withReadLock(() -> new LedgerStats(
            entries.size(),
            checkpoints.size(),
            entries.stream().mapToLong(LedgerEntry::getTimestamp).max().orElse(0L),
            entries.stream().mapToInt(LedgerEntry::getDataSize).sum(),
            merkleTree.getStats()
        ));
    }

    // Enhanced statistics with tree info
    public record LedgerStats(
        int entryCount, 
        int checkpointCount, 
        long lastModified,
        int totalDataSize,
        PersistentMerkleTree.TreeStats treeStats
    ) {
        public LedgerStats {
            if (entryCount < 0) throw new IllegalArgumentException("Entry count cannot be negative");
            if (checkpointCount < 0) throw new IllegalArgumentException("Checkpoint count cannot be negative");
        }

        public boolean isEmpty() { return entryCount == 0; }
        public boolean hasCheckpoints() { return checkpointCount > 0; }
        public long ageInMillis() { return System.currentTimeMillis() - lastModified; }
        
        public double averageEntriesPerCheckpoint() { 
            return checkpointCount > 0 ? (double) entryCount / checkpointCount : 0.0; 
        }
        
        public double averageDataSizePerEntry() {
            return entryCount > 0 ? (double) totalDataSize / entryCount : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                "LedgerStats{entries=%d, checkpoints=%d, totalSize=%db, treeDepth=%d, age=%dms}", 
                entryCount, checkpointCount, totalDataSize, treeStats.depth(), ageInMillis()
            );
        }
    }

    // --- Thread-Safe Lock Helpers ---
    private <T> T withReadLock(Supplier<T> operation) {
        lock.readLock().lock();
        try {
            return operation.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    private <T> T withWriteLock(Supplier<T> operation) {
        lock.writeLock().lock();
        try {
            return operation.get();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Configuration accessor
    public Configuration getConfiguration() { return config; }
}
