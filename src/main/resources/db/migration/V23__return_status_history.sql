CREATE TABLE return_status_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    return_request_id BIGINT UNSIGNED NOT NULL,
    from_status VARCHAR(50) NULL,
    to_status VARCHAR(50) NOT NULL,
    notes TEXT NULL,
    reason VARCHAR(500) NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_id BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_return_history_request (return_request_id, created_at),
    CONSTRAINT fk_return_history_request FOREIGN KEY (return_request_id)
        REFERENCES return_requests(id) ON DELETE CASCADE
);

INSERT INTO return_status_history
    (return_request_id, from_status, to_status, notes, reason, actor_type, actor_id, created_at)
SELECT rr.id, NULL, rr.status, rr.admin_notes, rr.rejection_reason, 'SYSTEM', NULL, rr.requested_at
FROM return_requests rr
WHERE NOT EXISTS (
    SELECT 1 FROM return_status_history h WHERE h.return_request_id = rr.id
);
