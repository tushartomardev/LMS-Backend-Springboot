-- Migration script to create subscription_plans table and seed initial data
-- This replaces the SubscriptionPlan enum with a database table for admin management

-- Create subscription_plans table
CREATE TABLE IF NOT EXISTS subscription_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    duration_days INT NOT NULL,
    price BIGINT NOT NULL COMMENT 'Price in smallest currency unit (paise/cents)',
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    max_books_allowed INT NOT NULL,
    max_days_per_book INT NOT NULL,
    display_order INT DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    badge_text VARCHAR(50),
    admin_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    INDEX idx_plan_code (plan_code),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed initial subscription plans (migrated from enum)
INSERT INTO subscription_plans (
    plan_code, name, description, duration_days, price, currency,
    max_books_allowed, max_days_per_book, display_order, is_active, is_featured, badge_text, created_by
) VALUES
(
    'MONTHLY',
    'Monthly Plan',
    'Access to library for 1 month',
    30,
    29900, -- ₹299.00 in paise
    'INR',
    5,
    14,
    1,
    TRUE,
    FALSE,
    NULL,
    'system'
),
(
    'QUARTERLY',
    'Quarterly Plan',
    'Access to library for 3 months with extended benefits',
    90,
    79900, -- ₹799.00 in paise
    'INR',
    10,
    21,
    2,
    TRUE,
    TRUE,
    'Best Value',
    'system'
),
(
    'YEARLY',
    'Yearly Plan',
    'Best value - Full year access with maximum benefits',
    365,
    249900, -- ₹2499.00 in paise
    'INR',
    15,
    30,
    3,
    TRUE,
    TRUE,
    'Most Popular',
    'system'
);

-- Now update subscriptions table to use plan_id instead of enum
-- First, add new columns to subscriptions table
ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS plan_id BIGINT,
    ADD COLUMN IF NOT EXISTS plan_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3) DEFAULT 'INR',
    ADD CONSTRAINT fk_subscription_plan
        FOREIGN KEY (plan_id) REFERENCES subscription_plans(id);

-- Migrate existing enum values to plan_id
-- This assumes you have existing subscriptions with 'plan' column as enum
UPDATE subscriptions s
JOIN subscription_plans sp ON sp.plan_code = s.plan
SET s.plan_id = sp.id, s.plan_code = sp.plan_code
WHERE s.plan_id IS NULL;

-- Make plan_id NOT NULL after migration
ALTER TABLE subscriptions
    MODIFY COLUMN plan_id BIGINT NOT NULL;

-- Drop old enum column (uncomment when you're ready)
-- ALTER TABLE subscriptions DROP COLUMN plan;

-- Update index
CREATE INDEX idx_plan_id ON subscriptions(plan_id);

-- Clean up old index if it exists
-- DROP INDEX idx_plan ON subscriptions;
