CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(128) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    avatar_url VARCHAR(500),
    avatar_audit_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
    avatar_audit_remark VARCHAR(500) NULL,
    avatar_audited_by BIGINT NULL,
    avatar_audited_at TIMESTAMP NULL,
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE category_do (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL UNIQUE,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1
);

CREATE TABLE goods_do (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    seller_id BIGINT NOT NULL,
    category_id BIGINT NULL,
    title VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    condition_level VARCHAR(50) NOT NULL,
    campus_location VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_goods_seller FOREIGN KEY (seller_id) REFERENCES users (id),
    CONSTRAINT fk_goods_category FOREIGN KEY (category_id) REFERENCES category_do (id)
);

CREATE TABLE goods_image_do (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    goods_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    audit_remark VARCHAR(500) NULL,
    audited_by BIGINT NULL,
    audited_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_goods_image_goods
    FOREIGN KEY (goods_id) REFERENCES goods_do (id) ON DELETE CASCADE
);

CREATE TABLE conversation_do (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    goods_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_message_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_conversation_goods
    FOREIGN KEY (goods_id) REFERENCES goods_do (id),

    CONSTRAINT fk_conversation_buyer
    FOREIGN KEY (buyer_id) REFERENCES users (id),

    CONSTRAINT fk_conversation_seller
    FOREIGN KEY (seller_id) REFERENCES users (id),

    CONSTRAINT uk_conversation_goods_buyer
    UNIQUE (goods_id, buyer_id)
);

CREATE TABLE messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_messages_conversation
    FOREIGN KEY (conversation_id) REFERENCES conversation_do (id) ON DELETE CASCADE,

    CONSTRAINT fk_messages_sender
    FOREIGN KEY (sender_id) REFERENCES users (id)
);
