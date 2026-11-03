ALTER TABLE book_notifications ADD COLUMN event_id UUID;

UPDATE book_notifications SET event_id = gen_random_uuid() WHERE event_id IS NULL;

ALTER TABLE book_notifications ALTER COLUMN event_id SET NOT NULL;

CREATE UNIQUE INDEX ux_book_notifications_event_id ON book_notifications (event_id);
