-- The platform no longer provides in-app conversations or messages.
-- Drop the child table first to satisfy the foreign-key dependency.
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS conversation_do;
