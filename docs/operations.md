# Operations

How to install and run Paynash POS on the on-site server in the restaurant (design D2, D11). The server runs four
containers from `deploy/docker-compose.yml`:

| Service | Purpose |
|---|---|
| `web` | Caddy: HTTPS on ports 80/443, serves the web app, forwards `/api/*` to the backend |
| `backend` | Spring Boot API and print worker; not reachable from outside except through `web` |
| `postgres` | Database (volume `db`) |
| `backup` | Daily database and image backup into `POS_BACKUP_DIR` |

Menu images live in the volume `images`, Caddy's certificates in `caddy-data`. All containers restart automatically
after a crash or a reboot of the server.

## 1. Installation

### Server

- A small always-on machine (mini PC or NAS) with a 64-bit Linux, at least 2 GB RAM and 10 GB free disk.
- Connected by cable to the same LAN as the printers.
- A fixed IP address: reserve it in the router's DHCP settings (preferred) or configure it statically.
- [Docker Engine with the Compose plugin](https://docs.docker.com/engine/install/) and `git`. Check with
  `docker compose version`. (Older setups use `docker-compose` instead of `docker compose`; the commands are the same.)

### Hostname

Phones and PCs reach the system as `https://<hostname>`, for example `https://pos.local`. The name must resolve to the
server's IP address in the LAN. Add it as a local DNS entry (often called *DNS host*, *static DNS*, or *local
domain*) in the router. Do not rely on mDNS (`.local` names announced by the server itself): Android browsers do not
resolve them reliably.

### Get the software and configure it

```sh
git clone https://github.com/SwissSoftware/paynash.git
cd paynash/deploy
cp .env.example .env
```

Edit `.env`:

- `POS_HOSTNAME`: the hostname from above.
- `POS_DB_PASSWORD`: a long random value, e.g. from `openssl rand -hex 24`. Keep it with the backup notes; you need it
  only to recreate the server.
- `POS_BACKUP_DIR`: where backups go; see [Backup](#5-backup-and-restore). Keep `./backups` for now if no external
  storage is mounted yet.
- `POS_TLS`: keep `internal` unless you have your own certificate (see below).

Start everything (the first build takes several minutes):

```sh
docker compose up -d --build
docker compose ps        # all services "Up"; backend and postgres "(healthy)"
```

### HTTPS certificate

With `POS_TLS=internal`, Caddy creates its own small certificate authority and a certificate for `POS_HOSTNAME`.
Every device must trust that authority once, otherwise the browser shows a certificate warning and the app does not
connect. Export its root certificate:

```sh
docker compose cp web:/data/caddy/pki/authorities/local/root.crt ./paynash-root.crt
```

Install `paynash-root.crt` on each device (send it by mail or offer it as a download in the LAN):

- **iPhone/iPad:** open the file, *Settings → General → VPN & Device Management* → install the profile, then
  *Settings → General → About → Certificate Trust Settings* → enable full trust for it.
- **Android:** *Settings → Security → More security settings → Encryption & credentials → Install a certificate →
  CA certificate* (menu names vary by manufacturer).
- **Windows:** double-click → *Install Certificate* → *Local Machine* → *Trusted Root Certification Authorities*.
- **macOS:** double-click to add it to the *System* keychain, open it there, and set *Trust → Always Trust*.

The root certificate stays valid for years and survives restarts and updates as long as the `caddy-data` volume is
kept. **Back up `paynash-root.crt`** together with the database backups.

*Alternative:* use a real subdomain (e.g. `pos.example.ch`) that points to the server's LAN IP and a certificate for
it (e.g. from Let's Encrypt via DNS-01). Then no device needs extra setup. Put the files in `deploy/certs/` and set
`POS_TLS=/certs/fullchain.pem /certs/privkey.pem`. Renew the certificate before it expires and run
`docker compose restart web`.

### Check

From another device in the LAN, open `https://<hostname>/api/health`. It must answer `{"status":"UP"}` without a
certificate warning. Then open `https://<hostname>/`; the login screen appears.

## 2. First admin

On the very first start the backend creates the user `admin` with a one-time PIN and writes it to its log once:

```sh
docker compose logs backend | grep "One-time PIN"
```

Log in at `https://<hostname>/` as `admin` with that PIN, then immediately set a new PIN under **Staff**, using the
**Reset PIN** button of `admin`. Create a personal admin account and the waiter accounts there as well.

## 3. Printer setup

Set up both Epson TM-m30III printers and enter them in the app as described in [printing.md](printing.md). The server
must reach the printers' IP addresses; check from the server with the `curl` command in step 7 there.

Then, as admin: enter the categories and menu items with images under **Menu setup**, and the tables under **Tables**.

## 4. Update

Take a backup first; database migrations only go forward, so the backup is the way back.

```sh
cd paynash/deploy
docker compose exec backup /scripts/backup.sh      # note the printed stamp
git pull
docker compose up -d --build
docker compose ps
```

Update outside service hours: the backend restarts and devices show "no connection" for about a minute.

**Rollback:** check out the previous version (`git checkout <previous tag or commit>`), run
`docker compose up -d --build`, and restore the backup taken before the update (next section).

## 5. Backup and restore

### What is backed up

Every day at `POS_BACKUP_TIME` (default 03:30, local time) the `backup` service writes a pair of files into
`POS_BACKUP_DIR`:

- `paynash-<stamp>.dump`: the whole database (staff, menu, orders, print jobs, printers, tables);
- `paynash-<stamp>-images.tar.gz`: all menu images.

`<stamp>` is the date and time, e.g. `2026-10-29_0330`. Backups older than `POS_BACKUP_KEEP_DAYS` (default 14) are
deleted. A backup can also be taken at any time:

```sh
docker compose exec backup /scripts/backup.sh
```

### Where to keep it

A backup on the same disk does not survive a broken server. Mount external storage (a NAS share or a USB disk) on the
server, set `POS_BACKUP_DIR` to that mount point in `.env`, and run `docker compose up -d backup`. Also keep a copy of
`.env` and `paynash-root.crt` there.

Check once a week that new backups appear:

```sh
ls -l <POS_BACKUP_DIR>
docker compose logs --tail 20 backup     # "backup paynash-... written"; "BACKUP FAILED" means a problem
```

### Restore

This replaces **all** data and images with the content of the backup. Orders sent after that backup are lost.

```sh
cd paynash/deploy
ls <POS_BACKUP_DIR>                                  # pick the stamp
docker compose stop backend
docker compose exec backup /scripts/restore.sh 2026-10-29_0330
docker compose start backend
```

The restore script stops at the first error and leaves the database unchanged in that case. Afterwards, staff log in
with the PINs that were valid at the time of the backup.

### Restore on a new server

1. Install as in [section 1](#1-installation), using the old `.env` (or at least the same `POS_HOSTNAME`).
2. Copy the backup pair into the new `POS_BACKUP_DIR`.
3. `docker compose up -d --build`, then restore as above. Ignore the one-time PIN of the new installation; the restore
   replaces that admin.
4. With `POS_TLS=internal` the new server has a new certificate authority; install its `paynash-root.crt` on the
   devices again. (To avoid this, also back up and restore the `caddy-data` volume.)

## 6. Troubleshooting

| Symptom | Check | Fix |
|---|---|---|
| Devices show "no connection" | `docker compose ps`; `https://<hostname>/api/health` from a device | Start missing services: `docker compose up -d`. Check WiFi and that the hostname resolves to the server |
| Certificate warning in the browser | Is `paynash-root.crt` trusted on the device? Does the address match `POS_HOSTNAME`? | Install the root certificate (section 1); always use the hostname, not the IP |
| `backend` not healthy | `docker compose logs --tail 100 backend` | Database errors: check `postgres` is healthy and `POS_DB_PASSWORD` was not changed after the first start |
| Login says invalid username or PIN | Account locked after 5 wrong PINs? | Wait 5 minutes, or an admin resets the PIN |
| Admin PIN lost and no other admin | — | Ask the developer to reset it in the database; a restore would bring back the old PIN but loses newer orders |
| Ticket failed / printer errors | Error text in the order history | See the troubleshooting table in [printing.md](printing.md), then **Reprint** |
| No new backups | `docker compose logs backup`; is the external storage mounted? | Remount, then `docker compose up -d backup` and take a backup by hand |
| Disk full | `df -h`; `docker system df` | Lower `POS_BACKUP_KEEP_DAYS`; remove old images with `docker image prune` |

Logs of all services: `docker compose logs -f` (Ctrl+C to stop).
