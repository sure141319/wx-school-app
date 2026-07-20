CREATE TABLE upload_object_do (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    usage_type VARCHAR(16) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    thumbnail_object_key VARCHAR(500) NULL,
    display_object_key VARCHAR(500) NULL,
    audit_thumbnail_object_key VARCHAR(500) NULL,
    source_size_bytes BIGINT NOT NULL,
    total_size_bytes BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    bound_type VARCHAR(16) NULL,
    bound_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,

    CONSTRAINT uk_upload_object_key UNIQUE (object_key),
    CONSTRAINT fk_upload_object_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_upload_object_user_status
    ON upload_object_do (user_id, status);

CREATE INDEX idx_upload_object_expiry
    ON upload_object_do (status, expires_at);

-- Backfill objects that were already bound before lifecycle tracking existed.
-- Only internal object keys are imported; arbitrary external URLs must never be
-- treated as MinIO objects or become automatic-deletion candidates.
INSERT INTO upload_object_do (
    user_id, usage_type, object_key, thumbnail_object_key, display_object_key,
    audit_thumbnail_object_key, source_size_bytes, total_size_bytes, status,
    bound_type, bound_id, created_at, updated_at, expires_at
)
SELECT
    g.seller_id,
    'goods',
    gi.image_url,
    MAX(gi.thumbnail_url),
    MAX(gi.display_url),
    MAX(gi.audit_thumbnail_url),
    0,
    0,
    'BOUND',
    'GOODS',
    MIN(gi.goods_id),
    MIN(gi.created_at),
    CURRENT_TIMESTAMP,
    NULL
FROM goods_image_do gi
JOIN goods_do g ON g.id = gi.goods_id
WHERE gi.image_url LIKE 'images/%'
GROUP BY g.seller_id, gi.image_url;

INSERT INTO upload_object_do (
    user_id, usage_type, object_key, source_size_bytes, total_size_bytes, status,
    bound_type, bound_id, created_at, updated_at, expires_at
)
SELECT
    u.id,
    'avatar',
    u.avatar_url,
    0,
    0,
    'BOUND',
    'AVATAR',
    u.id,
    u.created_at,
    CURRENT_TIMESTAMP,
    NULL
FROM users u
WHERE u.avatar_url LIKE 'images/%';
