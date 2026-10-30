#!/bin/sh
# Restores a backup written by backup.sh (task 8.2). Stop the backend first:
#   docker compose stop backend
#   docker compose exec backup /scripts/restore.sh <stamp>    # e.g. 2026-10-29_0330
#   docker compose start backend
# Replaces the whole database and all menu images with the backup's content.
set -eu

stamp=${1:?usage: restore.sh <stamp>, e.g. 2026-10-29_0330 (see ls /backups)}
dump="/backups/paynash-$stamp.dump"
images="/backups/paynash-$stamp-images.tar.gz"
[ -f "$dump" ] || { echo "missing $dump" >&2; exit 1; }
[ -f "$images" ] || { echo "missing $images" >&2; exit 1; }

pg_restore --clean --if-exists --no-owner --exit-on-error --single-transaction --dbname="$PGDATABASE" "$dump"
find /images -mindepth 1 -delete
tar -xzf "$images" -C /images
echo "restored paynash-$stamp"
