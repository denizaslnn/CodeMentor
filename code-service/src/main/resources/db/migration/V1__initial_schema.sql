-- ============================================================================
-- V1__initial_schema.sql
-- CodeMentor initial database schema (managed by Flyway).
--
-- Tablolar, JPA entity'lerinden ('User', 'RefreshToken', 'AnalysisRequest',
-- 'AnalysisTask') birebir çıkarılmıştır; böylece
--   spring.jpa.hibernate.ddl-auto=validate
-- startup'ta başarıyla geçer.
--
-- Geçiş güvenliği:
--   * Daha önce ddl-auto:update ile oluşmuş şema varsa (users/refresh_tokens/
--     analysis_* tabloları) CREATE TABLE IF NOT EXISTS sayesinde veri/şema
--     korunur, migration no-op olur ve Flyway bundan sonra şemayı sahiplenir.
--   * Hiçbir tablo DROP edilmez. Veri kaybı yoktur.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- users
-- Karşılık: com.codementor.codeservice.entity.User
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(255) NOT NULL,
    username      VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(30)  NOT NULL,
    enabled       BOOLEAN      NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
);

-- ---------------------------------------------------------------------------
-- refresh_tokens
-- Karşılık: com.codementor.codeservice.entity.RefreshToken
-- Yalnızca token_hash (SHA-256 hex) saklanır; ham refresh token asla DB'ye
-- yazılmaz.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id                   VARCHAR(255) NOT NULL,
    user_id              VARCHAR(255) NOT NULL,
    token_hash           VARCHAR(64)  NOT NULL,
    expires_at           TIMESTAMP    NOT NULL,
    created_at           TIMESTAMP    NOT NULL,
    revoked_at           TIMESTAMP    NULL,
    replaced_by_token_id VARCHAR(255) NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Repository sorguları (findByTokenHash, rotation CAS UPDATE, user bazlı
-- revoke) için gerekli indexler.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);

-- ---------------------------------------------------------------------------
-- analysis_requests
-- Karşılık: com.codementor.codeservice.entity.AnalysisRequest (ve
--           com.codementor.aiservice.entity.AnalysisRequest - aynı tablo)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analysis_requests (
    id          VARCHAR(255) NOT NULL,
    source_code TEXT         NOT NULL,
    prompt      TEXT         NULL,
    status      VARCHAR(255) NOT NULL,
    ai_response TEXT         NULL,
    created_at  TIMESTAMP    NULL,
    CONSTRAINT pk_analysis_requests PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- analysis_tasks
-- Karşılık: com.codementor.codeservice.entity.AnalysisTask
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analysis_tasks (
    id     UUID         NOT NULL,
    code   TEXT         NOT NULL,
    prompt TEXT         NULL,
    status VARCHAR(255) NOT NULL,
    CONSTRAINT pk_analysis_tasks PRIMARY KEY (id)
);