CREATE TABLE announcement_config (
    id BIGINT NOT NULL PRIMARY KEY,
    title VARCHAR(50) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO announcement_config (id, title, content, enabled, revision)
VALUES (1, '公告', '', FALSE, 1);
