-- Upload staging is not a business evidence link. Existing file tables are reused.
CREATE TABLE file_upload_session (
    upload_session_id BIGINT NOT NULL AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (upload_session_id),
    UNIQUE KEY uk_upload_version (version_id),
    UNIQUE KEY uk_upload_asset (asset_id),
    KEY idx_upload_order (work_order_id),
    CONSTRAINT fk_upload_work_order FOREIGN KEY (work_order_id) REFERENCES work_order (work_order_id),
    CONSTRAINT fk_upload_asset FOREIGN KEY (asset_id) REFERENCES file_asset (asset_id),
    CONSTRAINT fk_upload_version FOREIGN KEY (version_id) REFERENCES file_asset_version (version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
