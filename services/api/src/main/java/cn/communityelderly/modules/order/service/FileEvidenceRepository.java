package cn.communityelderly.modules.order.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class FileEvidenceRepository {
    private final JdbcTemplate jdbc;
    public FileEvidenceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Evidence(Long assetId, Long versionId, Long workOrderId, Long uploaderId,
                           String filename, String mimeType, long size, String sha256, String assetUuid) {}

    public Long create(String uuid, Long uploader, String filename, String originalName,
                       String mime, long size, String hash, Long workOrderId) {
        jdbc.update("INSERT INTO file_asset(asset_uuid, category, created_by_user_id) VALUES (?, 'WORK_ORDER_PHOTO', ?)", uuid, uploader);
        Long assetId = jdbc.queryForObject("SELECT asset_id FROM file_asset WHERE asset_uuid = ?", Long.class, uuid);
        jdbc.update("INSERT INTO file_asset_version(asset_id, version_no, storage_path, original_filename, file_size_bytes, mime_type, sha256_hash) VALUES (?, 1, ?, ?, ?, ?, ?)",
                assetId, filename, originalName, size, mime, hash);
        Long versionId = jdbc.queryForObject("SELECT version_id FROM file_asset_version WHERE asset_id = ? AND version_no = 1", Long.class, assetId);
        jdbc.update("INSERT INTO file_upload_session(work_order_id, asset_id, version_id, uploaded_by_user_id) VALUES (?, ?, ?, ?)", workOrderId, assetId, versionId, uploader);
        return versionId;
    }

    public Evidence find(Long versionId) {
        List<Evidence> result = jdbc.query("""
                SELECT a.asset_id, v.version_id, s.work_order_id, s.uploaded_by_user_id,
                       v.storage_path, v.mime_type, v.file_size_bytes, v.sha256_hash, a.asset_uuid
                FROM file_asset a JOIN file_asset_version v ON v.asset_id = a.asset_id
                JOIN file_upload_session s ON s.version_id = v.version_id AND s.asset_id = a.asset_id
                WHERE v.version_id = ? AND a.category = 'WORK_ORDER_PHOTO'
                  AND a.status = 'ACTIVE' AND v.status = 'ACTIVE' AND s.status = 'ACTIVE'
                """, (rs, row) -> new Evidence(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                rs.getString(5), rs.getString(6), rs.getLong(7), rs.getString(8), rs.getString(9)), versionId);
        return result.isEmpty() ? null : result.get(0);
    }

    public Evidence findByAssetUuid(String uuid) {
        List<Long> ids = jdbc.queryForList("SELECT s.version_id FROM file_asset a JOIN file_upload_session s ON s.asset_id = a.asset_id WHERE a.asset_uuid = ?", Long.class, uuid);
        return ids.size() == 1 ? find(ids.get(0)) : null;
    }

    public List<Long> legacyWorkOrderIds(String filename) {
        return jdbc.queryForList("SELECT work_order_id FROM work_order WHERE FIND_IN_SET(?, REPLACE(photo_urls, ';', ',')) > 0", Long.class, "/api/v1/files/view/" + filename);
    }

    public Evidence findByFilename(String filename) {
        List<Long> ids = jdbc.queryForList("SELECT version_id FROM file_asset_version WHERE storage_path = ?", Long.class, filename);
        return ids.size() == 1 ? find(ids.get(0)) : null;
    }

    public boolean linked(Long workOrderId, Long versionId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM business_file_link WHERE business_type = 'WORK_ORDER' AND business_id = ? AND version_id = ?", Integer.class,
                String.valueOf(workOrderId), versionId);
        return count != null && count > 0;
    }

    public void link(Evidence evidence, Long workOrderId) {
        if (!linked(workOrderId, evidence.versionId())) {
            jdbc.update("INSERT INTO business_file_link(business_type, business_id, asset_id, version_id, tag) VALUES ('WORK_ORDER', ?, ?, ?, 'SERVICE_PROOF')",
                    String.valueOf(workOrderId), evidence.assetId(), evidence.versionId());
        }
    }

    public String latestSubmission(Long workOrderId) {
        List<String> rows = jdbc.queryForList("SELECT action_payload FROM work_order_action_history WHERE work_order_id = ? AND action_name = 'SUBMIT_RESULT' ORDER BY action_id DESC LIMIT 1", String.class, workOrderId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
