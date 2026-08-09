-- =====================================================
-- Fine Entity Migration Script
-- =====================================================
-- Purpose: Migrate existing fine data from book_loans table
--          to the new separate fines table
-- Date: 2025-01-XX
-- =====================================================

-- STEP 1: Verify the fines table exists
-- (Should be automatically created by JPA/Hibernate)
SELECT COUNT(*) FROM information_schema.tables
WHERE table_name = 'fines';

-- STEP 2: Backup existing book_loans data (IMPORTANT!)
CREATE TABLE IF NOT EXISTS book_loans_backup_before_fine_migration AS
SELECT * FROM book_loans;

-- Verify backup
SELECT COUNT(*) FROM book_loans_backup_before_fine_migration;

-- STEP 3: Migrate existing fines from book_loans to fines table
-- Only migrate book loans that have fines (fine_amount > 0)
INSERT INTO fines (
    book_loan_id,
    user_id,
    type,
    amount,
    amount_paid,
    status,
    reason,
    notes,
    created_at,
    updated_at
)
SELECT
    bl.id AS book_loan_id,
    bl.user_id,
    'OVERDUE' AS type,  -- All legacy fines assumed to be overdue fines
    bl.fine_amount AS amount,
    CASE
        WHEN bl.fine_paid = TRUE THEN bl.fine_amount
        ELSE 0
    END AS amount_paid,
    CASE
        WHEN bl.fine_paid = TRUE THEN 'PAID'
        WHEN bl.fine_amount > 0 THEN 'PENDING'
        ELSE 'PENDING'
    END AS status,
    CONCAT('Migrated fine from legacy system - ', bl.overdue_days, ' days overdue') AS reason,
    CONCAT('Book: ', b.title, ' (ISBN: ', b.isbn, ')') AS notes,
    bl.created_at AS created_at,
    CURRENT_TIMESTAMP AS updated_at
FROM book_loans bl
INNER JOIN books b ON b.id = bl.book_id
WHERE bl.fine_amount > 0
  AND NOT EXISTS (
      SELECT 1 FROM fines f
      WHERE f.book_loan_id = bl.id
  );  -- Prevent duplicate migrations if script runs twice

-- STEP 4: Verification queries
-- Check migration success
SELECT '=== Migration Summary ===' AS info;

-- Count of migrated fines
SELECT COUNT(*) AS total_fines_migrated
FROM fines
WHERE reason LIKE 'Migrated%';

-- Compare totals
SELECT
    'Legacy System' AS system,
    COUNT(*) AS fine_count,
    COALESCE(SUM(fine_amount), 0) AS total_amount,
    COUNT(CASE WHEN fine_paid = TRUE THEN 1 END) AS paid_count
FROM book_loans
WHERE fine_amount > 0
UNION ALL
SELECT
    'New System' AS system,
    COUNT(*) AS fine_count,
    COALESCE(SUM(amount), 0) AS total_amount,
    COUNT(CASE WHEN status = 'PAID' THEN 1 END) AS paid_count
FROM fines;

-- STEP 5: Detailed verification
-- Sample comparison (first 10 records)
SELECT
    bl.id AS book_loan_id,
    u.full_name AS user_name,
    b.title AS book_title,
    bl.fine_amount AS legacy_fine_amount,
    bl.fine_paid AS legacy_paid,
    f.amount AS new_fine_amount,
    f.amount_paid AS new_amount_paid,
    f.status AS new_status
FROM book_loans bl
INNER JOIN users u ON u.id = bl.user_id
INNER JOIN books b ON b.id = bl.book_id
LEFT JOIN fines f ON f.book_loan_id = bl.id
WHERE bl.fine_amount > 0
ORDER BY bl.id
LIMIT 10;

-- STEP 6: Check for any discrepancies
-- This should return 0 rows if migration is perfect
SELECT
    bl.id,
    bl.fine_amount AS legacy_amount,
    COALESCE(SUM(f.amount), 0) AS new_amount,
    bl.fine_amount - COALESCE(SUM(f.amount), 0) AS difference
FROM book_loans bl
LEFT JOIN fines f ON f.book_loan_id = bl.id
WHERE bl.fine_amount > 0
GROUP BY bl.id, bl.fine_amount
HAVING bl.fine_amount != COALESCE(SUM(f.amount), 0);

-- STEP 7: Statistics queries (optional)
-- Fine breakdown by status
SELECT status, COUNT(*) as count, SUM(amount) as total_amount
FROM fines
GROUP BY status
ORDER BY count DESC;

-- Fine breakdown by type
SELECT type, COUNT(*) as count, SUM(amount) as total_amount
FROM fines
GROUP BY type
ORDER BY count DESC;

-- Users with most fines
SELECT
    u.id,
    u.full_name,
    u.email,
    COUNT(f.id) AS fine_count,
    SUM(f.amount) AS total_fines,
    SUM(f.amount - f.amount_paid) AS outstanding
FROM users u
INNER JOIN fines f ON f.user_id = u.id
GROUP BY u.id, u.full_name, u.email
ORDER BY total_fines DESC
LIMIT 10;

-- =====================================================
-- ROLLBACK PROCEDURE (if needed)
-- =====================================================
-- Uncomment and run these commands ONLY if you need to rollback

-- DELETE FROM fines WHERE reason LIKE 'Migrated%';
-- SELECT COUNT(*) FROM fines; -- Should be 0 or only new fines

-- Restore from backup (if you dropped columns - NOT RECOMMENDED YET)
-- DROP TABLE book_loans;
-- CREATE TABLE book_loans AS SELECT * FROM book_loans_backup_before_fine_migration;

-- =====================================================
-- POST-MIGRATION CLEANUP (DO NOT RUN YET!)
-- =====================================================
-- Only run these after thorough testing and verification
-- WAIT AT LEAST 1-2 WEEKS before removing legacy columns

-- To remove legacy fine columns (DANGEROUS - backup first!):
-- ALTER TABLE book_loans DROP COLUMN fine_amount;
-- ALTER TABLE book_loans DROP COLUMN fine_paid;

-- To drop backup table (after everything is stable):
-- DROP TABLE book_loans_backup_before_fine_migration;

-- =====================================================
-- END OF MIGRATION SCRIPT
-- =====================================================
