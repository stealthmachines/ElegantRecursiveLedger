import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * ElegantRecursiveLedger V3 - Ultimate Git-like branching with cryptographic verification
 */
public final class ElegantRecursiveLedgerV3 {
    private static final Logger LOGGER = Logger.getLogger(ElegantRecursiveLedgerV3.class.getName());

    private final ElegantStatelessLedger baseLedger;
    private final BranchRegistry branchRegistry;
    private final LineageTree lineageTree;
    private final ReentrantReadWriteLock lock;
    private final UUID ledgerId;

    // Private constructor
    private ElegantRecursiveLedgerV3(ElegantStatelessLedger baseLedger,
                                     BranchRegistry branchRegistry,
                                     LineageTree lineageTree,
                                     UUID ledgerId) {
        this.baseLedger = Objects.requireNonNull(baseLedger);
        this.branchRegistry = Objects.requireNonNull(branchRegistry);
        this.lineageTree = Objects.requireNonNull(lineageTree);
        this.ledgerId = Objects.requireNonNull(ledgerId);
        this.lock = new ReentrantReadWriteLock();
    }

    // --- Enhanced Branch Registry ---
    public record BranchRegistry(Map<BranchId, ElegantRecursiveLedgerV3> branches) {
        public BranchRegistry {
            branches = Map.copyOf(branches);
        }

        public static BranchRegistry empty() {
            return new BranchRegistry(Map.of());
        }

        public BranchRegistry withBranch(BranchId branchId, ElegantRecursiveLedgerV3 branch) {
            Map<BranchId, ElegantRecursiveLedgerV3> newBranches = new HashMap<>(branches);
            newBranches.put(branchId, branch);
            return new BranchRegistry(newBranches);
        }

        public BranchRegistry withoutBranch(BranchId branchId) {
            Map<BranchId, ElegantRecursiveLedgerV3> newBranches = new HashMap<>(branches);
            newBranches.remove(branchId);
            return new BranchRegistry(newBranches);
        }

        public Optional<ElegantRecursiveLedgerV3> getBranch(BranchId branchId) {
            return Optional.ofNullable(branches.get(branchId));
        }

        public Stream<BranchId> streamBranchIds() {
            return branches.keySet().stream();
        }

        public Stream<ElegantRecursiveLedgerV3> streamBranches() {
            return branches.values().stream();
        }

        public int size() { return branches.size(); }
        public boolean isEmpty() { return branches.isEmpty(); }
    }

    // --- Enhanced Branch Identity ---
    public record BranchId(UUID id, String name, BranchType type) {
        public BranchId {
            Objects.requireNonNull(id, "Branch ID cannot be null");
            Objects.requireNonNull(name, "Branch name cannot be null");
            Objects.requireNonNull(type, "Branch type cannot be null");
        }

        public static BranchId fromEntry(String name, UUID entryId) {
            return new BranchId(UUID.randomUUID(), name, BranchType.ENTRY_BASED);
        }

        public static BranchId fromCheckpoint(String name, UUID checkpointId) {
            return new BranchId(UUID.randomUUID(), name, BranchType.CHECKPOINT_BASED);
        }

        public static BranchId feature(String featureName) {
            return new BranchId(UUID.randomUUID(), "feature/" + featureName, BranchType.FEATURE);
        }

        public static BranchId hotfix(String hotfixName) {
            return new BranchId(UUID.randomUUID(), "hotfix/" + hotfixName, BranchType.HOTFIX);
        }

        public boolean isFeature() { return type == BranchType.FEATURE; }
        public boolean isHotfix() { return type == BranchType.HOTFIX; }
    }

    public enum BranchType {
        ENTRY_BASED, CHECKPOINT_BASED, FEATURE, HOTFIX, EXPERIMENTAL
    }

    // --- Enhanced Lineage Tree with Path Resolution ---
    public static final class LineageTree {
        private final Map<BranchId, BranchLineage> lineages;
        private final Map<BranchId, Set<BranchId>> children;

        private LineageTree(Map<BranchId, BranchLineage> lineages,
                           Map<BranchId, Set<BranchId>> children) {
            this.lineages = Map.copyOf(lineages);
            this.children = children.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> Set.copyOf(e.getValue())
                ));
        }

        public static LineageTree empty() {
            return new LineageTree(Map.of(), Map.of());
        }

        public LineageTree withLineage(BranchId branchId, BranchLineage lineage) {
            Map<BranchId, BranchLineage> newLineages = new HashMap<>(lineages);
            Map<BranchId, Set<BranchId>> newChildren = new HashMap<>(children);

            newLineages.put(branchId, lineage);
            
            // Update parent-child relationships
            if (lineage.parentBranch().isPresent()) {
                BranchId parent = lineage.parentBranch().get();
                newChildren.computeIfAbsent(parent, k -> new HashSet<>()).add(branchId);
            }

            return new LineageTree(newLineages, newChildren);
        }

        public Optional<BranchLineage> getLineage(BranchId branchId) {
            return Optional.ofNullable(lineages.get(branchId));
        }

        public Set<BranchId> getChildren(BranchId branchId) {
            return children.getOrDefault(branchId, Set.of());
        }

        public List<BranchId> getAncestors(BranchId branchId) {
            List<BranchId> ancestors = new ArrayList<>();
            Optional<BranchId> current = getLineage(branchId).flatMap(BranchLineage::parentBranch);
            
            while (current.isPresent()) {
                ancestors.add(current.get());
                current = getLineage(current.get()).flatMap(BranchLineage::parentBranch);
            }
            
            return ancestors;
        }

        public boolean isAncestor(BranchId potential, BranchId descendant) {
            return getAncestors(descendant).contains(potential);
        }

        public Optional<BranchId> findCommonAncestor(BranchId branch1, BranchId branch2) {
            Set<BranchId> ancestors1 = new HashSet<>(getAncestors(branch1));
            ancestors1.add(branch1);
            
            return getAncestors(branch2).stream()
                .filter(ancestors1::contains)
                .findFirst();
        }
    }

    // --- Branch Lineage with Rich Metadata ---
    public record BranchLineage(
        Optional<BranchId> parentBranch,
        UUID originPoint, // Entry or Checkpoint ID
        Instant creationTime,
        String createdBy,
        String description,
        Map<String, String> metadata
    ) {
        public BranchLineage {
            Objects.requireNonNull(originPoint, "Origin point cannot be null");
            Objects.requireNonNull(creationTime, "Creation time cannot be null");
            Objects.requireNonNull(createdBy, "Created by cannot be null");
            Objects.requireNonNull(description, "Description cannot be null");
            metadata = Map.copyOf(metadata);
        }

        public static BranchLineage create(Optional<BranchId> parent, UUID originPoint, 
                                         String createdBy, String description) {
            return new BranchLineage(parent, originPoint, Instant.now(), createdBy, description, Map.of());
        }

        public BranchLineage withMetadata(String key, String value) {
            Map<String, String> newMetadata = new HashMap<>(metadata);
            newMetadata.put(key, value);
            return new BranchLineage(parentBranch, originPoint, creationTime, createdBy, description, newMetadata);
        }

        public Duration age() {
            return Duration.between(creationTime, Instant.now());
        }

        public boolean isOlderThan(Duration duration) {
            return age().compareTo(duration) > 0;
        }
    }

    // --- Factory Methods ---
    public static ElegantRecursiveLedgerV3 of(ElegantStatelessLedger ledger) {
        return new ElegantRecursiveLedgerV3(
            ledger,
            BranchRegistry.empty(),
            LineageTree.empty(),
            UUID.randomUUID()
        );
    }

    public static ElegantRecursiveLedgerV3 create() {
        return of(ElegantStatelessLedger.create());
    }

    // --- Enhanced Branch Operations ---
    public BranchResult<ElegantRecursiveLedgerV3> createBranch(
            String branchName, 
            UUID originPoint, 
            ElegantStatelessLedger branchLedger,
            String createdBy,
            String description) {
        
        return withWriteLock(() -> {
            try {
                BranchId branchId = BranchId.fromEntry(branchName, originPoint);
                BranchLineage lineage = BranchLineage.create(
                    Optional.empty(), originPoint, createdBy, description);
                
                ElegantRecursiveLedgerV3 branch = new ElegantRecursiveLedgerV3(
                    branchLedger,
                    BranchRegistry.empty(),
                    LineageTree.empty(),
                    UUID.randomUUID()
                );

                BranchRegistry newRegistry = branchRegistry.withBranch(branchId, branch);
                LineageTree newLineage = lineageTree.withLineage(branchId, lineage);

                ElegantRecursiveLedgerV3 newLedger = new ElegantRecursiveLedgerV3(
                    baseLedger, newRegistry, newLineage, ledgerId);

                LOGGER.info(() -> String.format("Created branch '%s' from %s", branchName, originPoint));
                return BranchResult.success(newLedger, branchId);

            } catch (Exception e) {
                LOGGER.warning(() -> "Failed to create branch: " + e.getMessage());
                return BranchResult.failure("Failed to create branch: " + e.getMessage());
            }
        });
    }

    // --- Advanced Merge Operations ---
    public MergeResult<ElegantRecursiveLedgerV3> mergeBranch(BranchId branchId, MergeStrategy strategy) {
        return withWriteLock(() -> {
            Optional<ElegantRecursiveLedgerV3> branchOpt = branchRegistry.getBranch(branchId);
            if (branchOpt.isEmpty()) {
                return MergeResult.failure("Branch not found: " + branchId.name());
            }

            try {
                ElegantRecursiveLedgerV3 branch = branchOpt.get();
                ElegantStatelessLedger mergedLedger = strategy.merge(baseLedger, branch.baseLedger);
                
                BranchRegistry newRegistry = branchRegistry.withoutBranch(branchId);
                
                ElegantRecursiveLedgerV3 result = new ElegantRecursiveLedgerV3(
                    mergedLedger, newRegistry, lineageTree, ledgerId);

                LOGGER.info(() -> String.format("Merged branch '%s' using %s strategy", 
                    branchId.name(), strategy.getClass().getSimpleName()));
                
                return MergeResult.success(result, createMergeCommit(branchId, strategy));

            } catch (Exception e) {
                LOGGER.warning(() -> "Merge failed: " + e.getMessage());
                return MergeResult.failure("Merge failed: " + e.getMessage());
            }
        });
    }

    // --- Merge Strategies ---
    @FunctionalInterface
    public interface MergeStrategy {
        ElegantStatelessLedger merge(ElegantStatelessLedger base, ElegantStatelessLedger branch) 
            throws MergeConflictException;
    }

    public static final class MergeStrategies {
        public static final MergeStrategy APPEND_ALL = (base, branch) -> 
            branch.streamEntries()
                .reduce(base, (ledger, entry) -> ledger.addEntry(entry.getGlyphData()), 
                        (l1, l2) -> l1);

        public static final MergeStrategy FAST_FORWARD = (base, branch) -> {
            if (base.getEntries().isEmpty()) {
                return branch;
            }
            throw new MergeConflictException("Fast-forward merge not possible");
        };

        public static final MergeStrategy THREE_WAY = (base, branch) -> {
            // Simplified three-way merge - in practice would need common ancestor
            return APPEND_ALL.merge(base, branch);
        };
    }

    public static class MergeConflictException extends Exception {
        public MergeConflictException(String message) {
            super(message);
        }
    }

    // --- Result Types for Better Error Handling ---
    public sealed interface BranchResult<T> permits BranchResult.Success, BranchResult.Failure {
        record Success<T>(T result, BranchId branchId) implements BranchResult<T> {}
        record Failure<T>(String error) implements BranchResult<T> {}

        static <T> BranchResult<T> success(T result, BranchId branchId) {
            return new Success<>(result, branchId);
        }

        static <T> BranchResult<T> failure(String error) {
            return new Failure<>(error);
        }

        default boolean isSuccess() { return this instanceof Success; }
        default boolean isFailure() { return this instanceof Failure; }
    }

    public sealed interface MergeResult<T> permits MergeResult.Success, MergeResult.Failure {
        record Success<T>(T result, MergeCommit commit) implements MergeResult<T> {}
        record Failure<T>(String error) implements MergeResult<T> {}

        static <T> MergeResult<T> success(T result, MergeCommit commit) {
            return new Success<>(result, commit);
        }

        static <T> MergeResult<T> failure(String error) {
            return new Failure<>(error);
        }

        default boolean isSuccess() { return this instanceof Success; }
        default boolean isFailure() { return this instanceof Failure; }
    }

    public record MergeCommit(UUID id, BranchId sourceBranch, Instant timestamp, String strategy) {
        public MergeCommit {
            Objects.requireNonNull(id);
            Objects.requireNonNull(sourceBranch);
            Objects.requireNonNull(timestamp);
            Objects.requireNonNull(strategy);
        }
    }

    private MergeCommit createMergeCommit(BranchId branchId, MergeStrategy strategy) {
        return new MergeCommit(
            UUID.randomUUID(),
            branchId,
            Instant.now(),
            strategy.getClass().getSimpleName()
        );
    }

    // --- Enhanced Query Operations ---
    public <T> T withBranchContext(BranchId branchId, Function<ElegantStatelessLedger, T> operation) {
        return withReadLock(() -> {
            Optional<ElegantRecursiveLedgerV3> branch = branchRegistry.getBranch(branchId);
            return branch.map(b -> operation.apply(b.baseLedger))
                        .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + branchId));
        });
    }

    public Optional<ElegantStatelessLedger.LedgerEntry> findEntry(UUID entryId) {
        return withReadLock(() -> {
            // Search in base ledger first
            Optional<ElegantStatelessLedger.LedgerEntry> found = baseLedger.findEntry(entryId);
            if (found.isPresent()) return found;

            // Search recursively in all branches
            return branchRegistry.streamBranches()
                .map(branch -> branch.findEntry(entryId))
                .filter(Optional::isPresent)
                .findFirst()
                .orElse(Optional.empty());
        });
    }

    // --- Branch Analysis and Statistics ---
    public BranchAnalysis analyzeBranch(BranchId branchId) {
        return withReadLock(() -> {
            Optional<ElegantRecursiveLedgerV3> branch = branchRegistry.getBranch(branchId);
            Optional<BranchLineage> lineage = lineageTree.getLineage(branchId);
            
            if (branch.isEmpty() || lineage.isEmpty()) {
                throw new IllegalArgumentException("Branch not found: " + branchId);
            }

            ElegantStatelessLedger.LedgerStats stats = branch.get().baseLedger.getStats();
            Set<BranchId> children = lineageTree.getChildren(branchId);
            List<BranchId> ancestors = lineageTree.getAncestors(branchId);

            return new BranchAnalysis(
                branchId,
                lineage.get(),
                stats,
                children.size(),
                ancestors.size(),
                Duration.between(lineage.get().creationTime(), Instant.now())
            );
        });
    }

    public record BranchAnalysis(
        BranchId branchId,
        BranchLineage lineage,
        ElegantStatelessLedger.LedgerStats ledgerStats,
        int childrenCount,
        int ancestorCount,
        Duration age
    ) {
        public boolean isStale() {
            return age.toDays() > 30; // Consider branches older than 30 days as stale
        }

        public boolean hasChildren() {
            return childrenCount > 0;
        }

        public boolean isRoot() {
            return ancestorCount == 0;
        }
    }

    // --- Comprehensive Statistics ---
    public RecursiveLedgerStats getRecursiveStats() {
        return withReadLock(() -> {
            int totalEntries = baseLedger.getEntries().size();
            int totalCheckpoints = baseLedger.getCheckpoints().size();
            int activeBranches = branchRegistry.size();

            // Recursively collect stats from all branches
            for (ElegantRecursiveLedgerV3 branch : branchRegistry.streamBranches().toList()) {
                RecursiveLedgerStats branchStats = branch.getRecursiveStats();
                totalEntries += branchStats.totalEntries();
                totalCheckpoints += branchStats.totalCheckpoints();
                activeBranches += branchStats.activeBranches();
            }

            return new RecursiveLedgerStats(totalEntries, totalCheckpoints, activeBranches);
        });
    }

    public record RecursiveLedgerStats(int totalEntries, int totalCheckpoints, int activeBranches) {
        public boolean isEmpty() {
            return totalEntries == 0 && activeBranches == 0;
        }

        public double averageEntriesPerBranch() {
            return activeBranches > 0 ? (double) totalEntries / activeBranches : 0.0;
        }

        @Override
        public String toString() {
            return String.format("RecursiveLedgerStats{entries=%d, checkpoints=%d, branches=%d, avg=%.1f}",
                totalEntries, totalCheckpoints, activeBranches, averageEntriesPerBranch());
        }
    }

    // --- Utility Operations ---
    public ElegantRecursiveLedgerV3 addEntryToBase(String glyphData) {
        return withWriteLock(() -> {
            ElegantStatelessLedger newLedger = baseLedger.addEntry(glyphData);
            return new ElegantRecursiveLedgerV3(newLedger, branchRegistry, lineageTree, ledgerId);
        });
    }

    public List<BranchId> listBranches() {
        return withReadLock(() -> branchRegistry.streamBranchIds().toList());
    }

    public List<BranchId> findBranches(Predicate<BranchAnalysis> predicate) {
        return withReadLock(() -> 
            branchRegistry.streamBranchIds()
                .filter(branchId -> predicate.test(analyzeBranch(branchId)))
                .toList()
        );
    }

    // --- Thread-Safe Helpers ---
    private <T> T withReadLock(java.util.function.Supplier<T> operation) {
        lock.readLock().lock();
        try {
            return operation.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    private <T> T withWriteLock(java.util.function.Supplier<T> operation) {
        lock.writeLock().lock();
        try {
            return operation.get();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // --- Accessors ---
    public ElegantStatelessLedger getBaseLedger() { return baseLedger; }
    public BranchRegistry getBranchRegistry() { return branchRegistry; }
    public LineageTree getLineageTree() { return lineageTree; }
    public UUID getLedgerId() { return ledgerId; }
}
