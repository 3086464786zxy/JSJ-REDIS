-- New installations only. Create credentials separately through your secret-management process.
-- IF NOT EXISTS is intentionally omitted to avoid silently reusing a business database.
CREATE DATABASE itmk CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
