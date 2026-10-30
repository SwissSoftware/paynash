#!/bin/sh
# Backs up the database and the menu images into /backups (task 8.2).
#   backup.sh           one backup now
#   backup.sh --daily   one backup every day at $POS_BACKUP_TIME (local time), forever
# Each backup is a pair: paynash-<stamp>.dump (pg_dump custom format) and
# paynash-<stamp>-images.tar.gz. Pairs older than $POS_BACKUP_KEEP_DAYS days are deleted.
set -eu

backup_once() {
	stamp=$(date +%Y-%m-%d_%H%M)
	# Write to temporary names first so a half-written file never looks like a backup.
	pg_dump --format=custom --file="/backups/.paynash-$stamp.dump.tmp"
	tar -czf "/backups/.paynash-$stamp-images.tar.gz.tmp" -C /images .
	mv "/backups/.paynash-$stamp.dump.tmp" "/backups/paynash-$stamp.dump"
	mv "/backups/.paynash-$stamp-images.tar.gz.tmp" "/backups/paynash-$stamp-images.tar.gz"
	find /backups -maxdepth 1 -name 'paynash-*' -mtime "+${POS_BACKUP_KEEP_DAYS:-14}" -delete
	echo "$(date -Iseconds) backup paynash-$stamp written"
}

if [ "${1:-}" != "--daily" ]; then
	backup_once
	exit 0
fi

while true; do
	now=$(date +%s)
	next=$(date -d "$POS_BACKUP_TIME" +%s)
	[ "$next" -gt "$now" ] || next=$((next + 86400))
	echo "$(date -Iseconds) next backup at $(date -d "@$next" '+%Y-%m-%d %H:%M')"
	sleep $((next - now))
	backup_once || echo "$(date -Iseconds) BACKUP FAILED" >&2
done
