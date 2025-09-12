public class Main {
    public static void main(String[] args) {
        ElegantDNARecursiveLedger ledger = new ElegantDNARecursiveLedger();

        // Append a few transactions into "main"
        ledger.append("main", "TX-Deposit-100");
        ledger.append("main", "TX-Withdraw-50");

        // Create a feature branch
        ledger.branch("feature-A");
        ledger.append("feature-A", "TX-Deposit-200");
        ledger.append("feature-A", "TX-Withdraw-25");

        // Merge feature-A into main
        ledger.merge("feature-A", "main", ElegantDNARecursiveLedger.MergeStrategy.APPEND_ALL);

        // Print the audit log with DNA64 analytics
        System.out.println("\n=== AUDIT LOG ===");
        ledger.printAuditLog();
    }
}
