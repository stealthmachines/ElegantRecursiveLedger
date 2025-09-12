import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * ElegantDNARecursiveLedger
 *
 * Combines:
 *  - ElegantRecursiveLedgerV3 backbone (Git-like branching, Merkle-based tamper-evidence)
 *  - DNA64 holographic signature metadata (entropy, GC-content, φ resonance)
 *
 * Integrity is preserved by the Merkle tree, while DNA64 provides ephemeral analytics.
 */
public final class ElegantDNARecursiveLedger {
    private static final Logger LOGGER = Logger.getLogger(ElegantDNARecursiveLedger.class.getName());

    private final Map<String, Branch> branches = new HashMap<>();
    private final PersistentMerkleTree merkleTree = new PersistentMerkleTree();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<String> auditLog = new ArrayList<>();
    private long opCounter = 0;

    public ElegantDNARecursiveLedger() {
        branches.put("main", new Branch("main"));
    }

    /** Append payload into branch with Merkle integrity + DNA64 metadata */
    public void append(String branch, String payload) {
        lock.writeLock().lock();
        try {
            Branch b = branches.get(branch);
            if (b == null) throw new BranchNotFoundException(branch);

            // Step 1: Compute Merkle root
            BigInteger hash = merkleTree.append(payload);

            // Step 2: Generate DNA64 signature
            DNA64Signature dna64 = DNA64Generator.generate(hash, ++opCounter);

            // Step 3: Wrap as LedgerEntry
            LedgerEntry entry = new LedgerEntry(payload, hash, dna64);

            b.append(entry);
            auditLog.add(entry.summary());

            LOGGER.info(() -> "Appended to " + branch + ": " + entry.summary());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Merge branch B into branch A (fast-forward or append-all) */
    public void merge(String from, String into, MergeStrategy strategy) {
        lock.writeLock().lock();
        try {
            Branch src = branches.get(from);
            Branch dest = branches.get(into);
            if (src == null || dest == null) throw new BranchNotFoundException(from + " or " + into);

            switch (strategy) {
                case FAST_FORWARD:
                    dest.entries.addAll(src.entries);
                    break;
                case APPEND_ALL:
                    for (LedgerEntry e : src.entries) {
                        BigInteger newHash = merkleTree.append(e.payload);
                        DNA64Signature dna64 = DNA64Generator.generate(newHash, ++opCounter);
                        dest.append(new LedgerEntry(e.payload, newHash, dna64));
                    }
                    break;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Create a new branch */
    public void branch(String name) {
        lock.writeLock().lock();
        try {
            if (branches.containsKey(name)) throw new IllegalStateException("Branch exists: " + name);
            branches.put(name, new Branch(name));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Print audit log */
    public void printAuditLog() {
        auditLog.forEach(System.out::println);
    }

    // ─────────────────────────────
    // Inner Classes
    // ─────────────────────────────

    public enum MergeStrategy { FAST_FORWARD, APPEND_ALL }

    public static final class Branch {
        private final String name;
        private final List<LedgerEntry> entries = new ArrayList<>();
        Branch(String name) { this.name = name; }
        void append(LedgerEntry entry) { entries.add(entry); }
    }

    public static final class LedgerEntry {
        public final String payload;
        public final BigInteger merkleHash;
        public final DNA64Signature dna64;
        public final long timestamp;

        LedgerEntry(String payload, BigInteger merkleHash, DNA64Signature dna64) {
            this.payload = payload;
            this.merkleHash = merkleHash;
            this.dna64 = dna64;
            this.timestamp = System.currentTimeMillis();
        }

        public String summary() {
            return String.format("[%s] hash=%s DNA64=%s φ=%.3f entropy=%.3f GC=%.3f",
                payload,
                merkleHash.toString(16),
                dna64.sequence,
                dna64.phiResonance,
                dna64.entropy,
                dna64.gcContent
            );
        }
    }

    public static final class DNA64Signature {
        public final String sequence;
        public final double entropy;
        public final double gcContent;
        public final double phiResonance;
        DNA64Signature(String seq, double entropy, double gc, double phi) {
            this.sequence = seq;
            this.entropy = entropy;
            this.gcContent = gc;
            this.phiResonance = phi;
        }
    }

    public static final class DNA64Generator {
        private static final double PHI = (1.0 + Math.sqrt(5)) / 2;
        private static final double INV_PHI = 1.0 / PHI;
        private static final char[] DNA_MAP = {'A','G','T','C'};

        public static DNA64Signature generate(BigInteger ledgerHash, long opID) {
            String seq = generateDNA64(ledgerHash, opID, 64);
            double entropy = shannonEntropy(seq);
            double gc = gcContent(seq);
            double phiRes = Math.abs(Math.sin(opID * PHI)) * Math.abs(Math.cos(opID * INV_PHI));
            return new DNA64Signature(seq, entropy, gc, phiRes);
        }

        private static String generateDNA64(BigInteger hash, long opID, int length) {
            byte[] input = hash.xor(BigInteger.valueOf(opID)).toByteArray();
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(input);
                StringBuilder sb = new StringBuilder(length);
                for (int i = 0; i < length; i++) {
                    int idx = digest[i % digest.length] & 0xFF;
                    sb.append(DNA_MAP[idx % DNA_MAP.length]);
                }
                return sb.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        private static double shannonEntropy(String seq) {
            int[] counts = new int[4];
            for (char c : seq.toCharArray()) {
                switch (c) {
                    case 'A': counts[0]++; break;
                    case 'G': counts[1]++; break;
                    case 'T': counts[2]++; break;
                    case 'C': counts[3]++; break;
                }
            }
            double entropy = 0.0;
            for (int c : counts) {
                if (c > 0) {
                    double p = (double)c / seq.length();
                    entropy -= p * (Math.log(p) / Math.log(2));
                }
            }
            return entropy;
        }

        private static double gcContent(String seq) {
            long gc = seq.chars().filter(c -> c=='G' || c=='C').count();
            return (double) gc / seq.length();
        }
    }

    public static final class BranchNotFoundException extends RuntimeException {
        BranchNotFoundException(String branch) { super("Branch not found: " + branch); }
    }

    // ─────────────────────────────
    // Persistent Merkle Tree
    // ─────────────────────────────
    public static final class PersistentMerkleTree {
        private final List<BigInteger> leaves = new ArrayList<>();

        public BigInteger append(String payload) {
            leaves.add(hash(payload));
            return rootHash();
        }

        public BigInteger rootHash() {
            List<BigInteger> level = new ArrayList<>(leaves);
            while (level.size() > 1) {
                List<BigInteger> next = new ArrayList<>();
                for (int i = 0; i < level.size(); i += 2) {
                    if (i+1 < level.size()) {
                        next.add(hash(level.get(i).toString() + level.get(i+1).toString()));
                    } else {
                        next.add(level.get(i));
                    }
                }
                level = next;
            }
            return level.isEmpty() ? BigInteger.ZERO : level.get(0);
        }

        private BigInteger hash(String input) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                return new BigInteger(1, md.digest(input.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
