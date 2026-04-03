-- 智能菜谱功能基础表（若已存在会跳过）

CREATE TABLE IF NOT EXISTS recipes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    user_id BIGINT NULL,
    flavor_tags JSON NULL,
    cuisine_tags JSON NULL,
    -- 与线上结构保持一致：由 JSON 自动计算，不允许手动写入
    primary_flavor VARCHAR(50)
        GENERATED ALWAYS AS (json_unquote(json_extract(flavor_tags, '$[0]'))) VIRTUAL,
    primary_cuisine VARCHAR(50)
        GENERATED ALWAYS AS (json_unquote(json_extract(cuisine_tags, '$[0]'))) VIRTUAL,
    time_minutes INT NULL,
    difficulty INT NULL COMMENT '0简单1中等2困难',
    ingredients_required JSON NULL,
    steps JSON NULL,
    nutrition_summary JSON NULL,
    video_tutorials JSON NULL,
    is_public TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_recipe_user (user_id),
    INDEX idx_difficulty (difficulty),
    INDEX idx_primary_cuisine (primary_cuisine),
    INDEX idx_primary_flavor (primary_flavor),
    FULLTEXT INDEX ft_title (title),
    CONSTRAINT fk_recipe_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS user_collections (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    recipe_id BIGINT NOT NULL,
    action_type INT NOT NULL COMMENT '0浏览,1做过,2收藏',
    rating INT NULL,
    review_text TEXT NULL,
    cooked_at TIMESTAMP NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_action (user_id, action_type),
    INDEX idx_uc_recipe (recipe_id),
    INDEX idx_cooked_time (cooked_at),
    CONSTRAINT fk_coll_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

