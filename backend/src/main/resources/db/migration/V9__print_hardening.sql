-- Print hardening for events (change harden-printing-for-events, design D1-D7).

-- Job states (D1): 'printing' is committed before a ticket goes to the printer, 'unclear' means the printer may have
-- printed it. waiting_reason says why a pending ticket waits (offline, paper end, cover open).
ALTER TABLE print_job
    ALTER COLUMN status TYPE VARCHAR(8),
    DROP CONSTRAINT print_job_status_check,
    ADD CONSTRAINT print_job_status_check
        CHECK (status IN ('pending', 'printing', 'printed', 'unclear', 'failed')),
    ADD COLUMN waiting_reason VARCHAR(200),
    -- Running number of the ticket on its printer (D3); null when no printer was configured.
    ADD COLUMN ticket_no INTEGER,
    -- Number of item lines on the ticket, shown in the station list.
    ADD COLUMN line_count INTEGER NOT NULL DEFAULT 0,
    -- How station staff resolved an unclear ticket (D5).
    ADD COLUMN resolution VARCHAR(7) CHECK (resolution IN ('printed', 'reprint')),
    ADD COLUMN resolved_at TIMESTAMPTZ,
    ADD COLUMN resolved_by_station BIGINT REFERENCES staff (id),
    ADD CONSTRAINT print_job_resolved_check CHECK ((resolution IS NULL) = (resolved_at IS NULL));

-- The station view reads a printer's tickets by time.
CREATE INDEX print_job_printer_created_idx ON print_job (printer_id, created_at);

-- Per-printer ticket counter (D3). It is never reset, so numbers stay unique across midnight during an event.
ALTER TABLE printer ADD COLUMN last_ticket_no INTEGER NOT NULL DEFAULT 0;

-- Station accounts (D6) and which printers each one shows. last_seen_at is the time of the station's last poll.
ALTER TABLE staff
    DROP CONSTRAINT staff_role_check,
    ADD CONSTRAINT staff_role_check CHECK (role IN ('admin', 'waiter', 'station')),
    ADD COLUMN last_seen_at TIMESTAMPTZ;

CREATE TABLE station_printer (
    station_id BIGINT NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    printer_id BIGINT NOT NULL REFERENCES printer (id) ON DELETE CASCADE,
    PRIMARY KEY (station_id, printer_id)
);

-- Cancellations confirmed at a station (D7): cancelled_by / voided_by is the confirming staff member, these columns
-- name the station.
ALTER TABLE order_line ADD COLUMN cancelled_at_station BIGINT REFERENCES staff (id);
ALTER TABLE orders ADD COLUMN voided_at_station BIGINT REFERENCES staff (id);
