# Registriert per Upload/API in app.db, was sync-modrinth-profiles.ps1 nur als
# Datei nach mods/ kopiert: Content-Tab-Eintrag (source_kind='local'),
# Datei-Zeile (sha1/size/missing) und Store-Blob. Repliziert exakt, was die
# Modrinth App beim manuellen "Upload files" anlegt (project_id/version_id
# bleiben NULL = lokaler Upload ohne Online-Metadaten).
#
# Ablauf: erst Backup (sqlite backup API), dann pro "Status Mod *"-Profil:
# Statusmod-JAR in mods/ suchen, Datei-Zeile upserten, alte Statusmod-Eintraege
# entfernen, Content-Eintrag + Store-Zeilen sicherstellen.
# Die Modrinth App MUSS vorher komplett beendet sein (Quit), sonst Abbruch
# im aufrufenden PS-Skript. Idempotent: erneutes Laufen aendert nichts.
import hashlib
import os
import sqlite3
import sys
import time
import uuid

EXCLUDE = {"Status mod test fabric"}


def sha1_of(path):
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def sha512_of(path):
    h = hashlib.sha512()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def is_statusmod(name):
    return name.lower().startswith("statusmod-") and name.lower().endswith(".jar")


def main():
    repo = sys.argv[1]
    mod_version = sys.argv[2]
    app_dir = os.path.join(os.environ["APPDATA"], "ModrinthApp")
    db_path = os.path.join(app_dir, "app.db")
    profiles_root = os.path.join(app_dir, "profiles")

    con = sqlite3.connect(db_path)
    con.execute("PRAGMA foreign_keys=ON")
    now = int(time.time())
    now_ns = time.time_ns()

    # Backup zuerst (konsistent, auch mit WAL).
    bak = os.path.join(app_dir, "app.db.register-bak-%d" % now)
    with sqlite3.connect(bak) as dest:
        con.backup(dest)
    print("Backup: %s" % bak)

    done, skipped = 0, []
    want_prefix = "statusmod-%s-" % mod_version.lower()
    for profile in sorted(os.listdir(profiles_root)):
        if not profile.startswith("Status Mod "):
            continue
        if profile in EXCLUDE:
            continue
        mods_dir = os.path.join(profiles_root, profile, "mods")
        if not os.path.isdir(mods_dir):
            skipped.append("%s (kein mods-Ordner)" % profile)
            continue
        jars = [j for j in os.listdir(mods_dir) if is_statusmod(j)]
        if not jars:
            skipped.append("%s (keine Statusmod-JAR)" % profile)
            continue
        current = None
        for j in jars:
            if j.lower().startswith(want_prefix):
                current = j
                break
        if current is None:  # Fallback: neueste Datei
            current = max(jars, key=lambda j: os.path.getmtime(os.path.join(mods_dir, j)))

        inst = con.execute(
            "SELECT id FROM instances WHERE name=?", (profile,)
        ).fetchone()
        if not inst:
            skipped.append("%s (keine instance-Zeile)" % profile)
            continue
        iid = inst[0]
        cset = con.execute(
            "SELECT id FROM instance_content_sets WHERE instance_id=? ORDER BY created LIMIT 1",
            (iid,),
        ).fetchone()
        if not cset:
            skipped.append("%s (kein content-set)" % profile)
            continue
        set_id = cset[0]

        # Datei-Zeilen: missing nach Existenz setzen (wie der App-Scanner).
        for j in jars:
            rel = "mods/" + j
            exists = os.path.isfile(os.path.join(mods_dir, j))
            row = con.execute(
                "SELECT id FROM instance_files WHERE instance_id=? AND relative_path=?",
                (iid, rel),
            ).fetchone()
            if row and exists:
                fp = os.path.join(mods_dir, j)
                con.execute(
                    "UPDATE instance_files SET sha1=?, size=?, missing=0, modified_at=? WHERE id=?",
                    (sha1_of(fp), os.path.getsize(fp), now, row[0]),
                )

        # Aktuelle Datei-Zeile sicherstellen.
        rel = "mods/" + current
        fpath = os.path.join(mods_dir, current)
        fsize = os.path.getsize(fpath)
        fsha1 = sha1_of(fpath)
        fsha512 = sha512_of(fpath)
        frow = con.execute(
            "SELECT id FROM instance_files WHERE instance_id=? AND relative_path=?",
            (iid, rel),
        ).fetchone()
        if frow:
            file_id = frow[0]
            con.execute(
                "UPDATE instance_files SET file_name=?, enabled=1, sha1=?, size=?, missing=0,"
                " added_at=COALESCE(added_at,?), modified_at=? WHERE id=?",
                (current, fsha1, fsize, now, now, file_id),
            )
        else:
            file_id = "instance-file:%s" % uuid.uuid4()
            con.execute(
                "INSERT INTO instance_files (id, instance_id, relative_path, file_name,"
                " enabled, sha1, size, missing, added_at, modified_at)"
                " VALUES (?,?,?,?,1,?,?,0,?,?)",
                (file_id, iid, rel, current, fsha1, fsize, now, now),
            )

        # Alte Statusmod-Eintraege entfernen (nur unsere Dateien, nie fremde Mods).
        for (eid, fid, rpath) in con.execute(
            "SELECT e.id, e.file_id, f.relative_path FROM instance_content_entries e"
            " JOIN instance_files f ON f.id=e.file_id"
            " WHERE e.instance_id=? AND LOWER(f.relative_path) LIKE 'mods/statusmod-%.jar'",
            (iid,),
        ).fetchall():
            if fid != file_id:
                con.execute("DELETE FROM instance_content_entries WHERE id=?", (eid,))
                con.execute("DELETE FROM store_instance_files WHERE file_id=?", (fid,))

        # Content-Eintrag sicherstellen.
        exists_entry = con.execute(
            "SELECT id FROM instance_content_entries WHERE instance_id=? AND file_id=?",
            (iid, file_id),
        ).fetchone()
        if not exists_entry:
            con.execute(
                "INSERT INTO instance_content_entries (id, instance_id, content_set_id, file_id,"
                " project_type, project_id, version_id, source_kind, server_requirement,"
                " client_requirement, enabled, added_at, modified_at)"
                " VALUES (?,?,?,?, 'mod', NULL, NULL, 'local', 'required',"
                " 'required', 1, ?, ?)",
                ("content-entry:%s" % uuid.uuid4(), iid, set_id, file_id, now, now),
            )

        # Store-Blob sicherstellen (wie beim UI-Upload: status ready, copy).
        if not con.execute("SELECT sha512 FROM store_blobs WHERE sha512=?", (fsha512,)).fetchone():
            con.execute(
                "INSERT INTO store_blobs (sha512, size, status, modified_as, created_at,"
                " last_used_at, verified_at, sources) VALUES (?,?, 'ready', ?, ?, ?, ?, '[]')",
                (fsha512, fsize, now_ns, now, now, now),
            )
        if not con.execute(
            "SELECT file_id FROM store_instance_files WHERE file_id=?", (file_id,)
        ).fetchone():
            con.execute(
                "INSERT INTO store_instance_files (file_id, blob_sha512, materialization_kind)"
                " VALUES (?,?,'copy')",
                (file_id, fsha512),
            )

        con.execute("UPDATE instances SET modified=? WHERE id=?", (now, iid))
        con.execute("UPDATE instance_content_sets SET modified=? WHERE id=?", (now, set_id))
        print("OK: %s <- %s" % (profile, current))
        done += 1

    con.commit()
    # Verifikation: Eintraege pro Status-Mod-Profil zaehlen.
    n = con.execute(
        "SELECT COUNT(*) FROM instance_content_entries e JOIN instances i ON i.id=e.instance_id"
        " WHERE i.name LIKE 'Status Mod %'"
    ).fetchone()[0]
    con.close()
    print("")
    print("%d Profile registriert, %d uebersprungen, %d Content-Eintraege total." % (done, len(skipped), n))
    for s in skipped:
        print("  SKIP: %s" % s)


main()
