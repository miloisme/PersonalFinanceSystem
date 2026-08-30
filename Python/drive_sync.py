"""Google Drive sync for the encrypted Personal Finance database.

Design notes
------------
* Only the ciphertext (``finance.db.enc``) and its ``finance.db.meta`` are
  ever uploaded. Google never sees plaintext.
* Uses the ``drive.file`` scope so the app can only see files it created
  itself (no OAuth verification required for personal use).
* The SQLite file is first exported to a clean snapshot via
  ``VACUUM INTO`` (with ``wal_checkpoint``) so we never read a half-written
  database. The plaintext snapshot is securely deleted afterwards.
* File identity is tracked by Drive ``fileId`` (stored locally) so updates
  always target the same object instead of creating duplicates.

Setup (one time, done by the user)
-----------------------------------
1. Create OAuth client ID (Desktop) in Google Cloud, enable Drive API.
2. Save the downloaded JSON as ``client_secret.json`` next to this file.
3. Click "Login to Google" in the app; a browser consent flow runs once.
"""

import os
import io
import json
import shutil
import sqlite3
import datetime

from db_crypto import (
    load_meta, save_meta, encrypt_file, decrypt_file,
    check_verifier, secure_delete, update_meta_sync_info,
)

SCOPES = ["https://www.googleapis.com/auth/drive.file"]
ENC_NAME = "finance.db.enc"
META_NAME = "finance.db.meta"

_HERE = os.path.dirname(os.path.abspath(__file__))
CLIENT_SECRET_PATH = os.path.join(_HERE, "client_secret.json")
TOKEN_PATH = os.path.join(_HERE, "drive_token.json")
STATE_PATH = os.path.join(_HERE, "drive_state.json")

try:
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload, MediaIoBaseDownload
    from google_auth_oauthlib.flow import InstalledAppFlow
    from google.auth.transport.requests import Request
    from google.oauth2.credentials import Credentials
    _HAS_GOOGLE = True
except Exception:  # pragma: no cover - library not installed
    _HAS_GOOGLE = False

_SERVICE = None


# --------------------------------------------------------------------------
# Local state (file ids + last successful sync timestamp)
# --------------------------------------------------------------------------

def _load_state() -> dict:
    if os.path.exists(STATE_PATH):
        try:
            with open(STATE_PATH, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {}


def _save_state(state: dict):
    with open(STATE_PATH, "w", encoding="utf-8") as f:
        json.dump(state, f)


def get_last_synced_at() -> str | None:
    return _load_state().get("last_synced_at")


def set_last_synced_at(iso: str | None):
    state = _load_state()
    state["last_synced_at"] = iso
    _save_state(state)


# --------------------------------------------------------------------------
# Authentication
# --------------------------------------------------------------------------

def is_client_configured() -> bool:
    return os.path.exists(CLIENT_SECRET_PATH)


def is_available() -> bool:
    """True if the Google API libraries are importable."""
    return _HAS_GOOGLE


def is_authenticated() -> bool:
    if not _HAS_GOOGLE:
        return False
    if not os.path.exists(TOKEN_PATH):
        return False
    try:
        creds = Credentials.from_authorized_user_file(TOKEN_PATH, SCOPES)
        return creds.valid or (creds.expired and creds.refresh_token)
    except Exception:
        return False


def authenticate() -> bool:
    """Run the OAuth consent flow (local browser). Returns True on success."""
    if not _HAS_GOOGLE:
        raise RuntimeError(
            "Google API libraries are not installed. Run: pip install "
            "google-api-python-client google-auth-httplib2 google-auth-oauthlib"
        )
    if not is_client_configured():
        raise RuntimeError(
            "client_secret.json not found next to drive_sync.py. "
            "Create a Desktop OAuth client in Google Cloud and save it there."
        )

    creds = None
    if os.path.exists(TOKEN_PATH):
        creds = Credentials.from_authorized_user_file(TOKEN_PATH, SCOPES)
    if creds and creds.expired and creds.refresh_token:
        creds.refresh(Request())
    if not creds or not creds.valid:
        flow = InstalledAppFlow.from_client_secrets_file(
            CLIENT_SECRET_PATH, SCOPES
        )
        creds = flow.run_local_server(port=0)
    with open(TOKEN_PATH, "w", encoding="utf-8") as f:
        f.write(creds.to_json())
    return True


def _get_service():
    global _SERVICE
    if _SERVICE is not None:
        return _SERVICE
    if not _HAS_GOOGLE:
        raise RuntimeError("Google API libraries are not installed.")
    creds = Credentials.from_authorized_user_file(TOKEN_PATH, SCOPES)
    if creds.expired and creds.refresh_token:
        creds.refresh(Request())
    _SERVICE = build("drive", "v3", credentials=creds)
    return _SERVICE


# --------------------------------------------------------------------------
# Internal helpers
# --------------------------------------------------------------------------

def _clean_snapshot(db_path: str, snapshot_path: str):
    """Export a consistent, checkpointed copy of the live database."""
    con = sqlite3.connect(db_path)
    try:
        try:
            con.execute("PRAGMA wal_checkpoint(FULL)")
        except Exception:
            pass
        try:
            con.execute(f"VACUUM INTO '{snapshot_path}'")
            return
        except Exception:
            pass
    finally:
        con.close()
    # Fallback: plain file copy (consistent only when no connection is open).
    shutil.copyfile(db_path, snapshot_path)


def _upload_file(service, local_path: str, name: str, file_id: str | None) -> str:
    media = MediaFileUpload(local_path, mimetype="application/octet-stream")
    if file_id:
        service.files().update(fileId=file_id, media_body=media).execute()
        return file_id
    file = service.files().create(
        body={"name": name}, media_body=media, fields="id"
    ).execute()
    return file.get("id")


def _download_file(service, file_id: str, local_path: str):
    request = service.files().get_media(fileId=file_id)
    with io.FileIO(local_path, "wb") as fh:
        downloader = MediaIoBaseDownload(fh, request)
        done = False
        while not done:
            _, done = downloader.next_chunk()


# --------------------------------------------------------------------------
# Public API
# --------------------------------------------------------------------------

def remote_meta() -> dict | None:
    """Download and parse the remote ``finance.db.meta``. Returns None if absent."""
    state = _load_state()
    meta_id = state.get("meta_file_id")
    if not meta_id:
        # Try to locate by name (first run / state lost).
        service = _get_service()
        resp = service.files().list(
            q=f"name='{META_NAME}' and trashed=false",
            spaces="drive", fields="files(id)", pageSize=1
        ).execute()
        files = resp.get("files", [])
        if not files:
            return None
        meta_id = files[0]["id"]
        state["meta_file_id"] = meta_id
        _save_state(state)

    tmp = STATE_PATH + ".meta.tmp"
    try:
        _download_file(_get_service(), meta_id, tmp)
        with open(tmp, "r", encoding="utf-8") as f:
            return json.load(f)
    finally:
        if os.path.exists(tmp):
            try:
                os.remove(tmp)
            except Exception:
                pass


def upload(db, device: str | None = None) -> str:
    """Encrypt the current DB and upload both files to Drive. Returns updated_at."""
    if not is_authenticated():
        authenticate()
    service = _get_service()

    db_path = db.db_path
    enc_path = db_path + ".enc"
    meta_path = db_path + ".meta"

    snapshot = STATE_PATH + ".snapshot.tmp"
    try:
        _clean_snapshot(db_path, snapshot)
        with open(snapshot, "rb") as f:
            plaintext = f.read()
        encrypt_file(snapshot, enc_path, db.crypto_key)

        updated_at = datetime.datetime.now(datetime.timezone.utc).strftime(
            "%Y-%m-%dT%H:%M:%SZ"
        )
        meta = load_meta(db_path)
        meta = update_meta_sync_info(
            meta, updated_at=updated_at,
            device=device or _device_name(), plaintext_data=plaintext
        )
        save_meta(db_path, meta)

        state = _load_state()
        state["enc_file_id"] = _upload_file(service, enc_path, ENC_NAME, state.get("enc_file_id"))
        state["meta_file_id"] = _upload_file(service, meta_path, META_NAME, state.get("meta_file_id"))
        state["last_synced_at"] = updated_at
        _save_state(state)
        return updated_at
    finally:
        if os.path.exists(snapshot):
            secure_delete(snapshot)


def download(db) -> dict:
    """Download remote ciphertext, decrypt it over the local DB, return remote meta."""
    if not is_authenticated():
        authenticate()
    service = _get_service()

    state = _load_state()
    enc_id = state.get("enc_file_id")
    meta_id = state.get("meta_file_id")
    if not enc_id or not meta_id:
        # Locate by name (state lost).
        for name, key in ((ENC_NAME, "enc_file_id"), (META_NAME, "meta_file_id")):
            resp = service.files().list(
                q=f"name='{name}' and trashed=false",
                spaces="drive", fields="files(id)", pageSize=1
            ).execute()
            files = resp.get("files", [])
            if files:
                state[key] = files[0]["id"]
        _save_state(state)
        enc_id = state.get("enc_file_id")
        meta_id = state.get("meta_file_id")
    if not enc_id or not meta_id:
        raise RuntimeError("No synced database found on Google Drive yet. Upload first.")

    tmp_enc = STATE_PATH + ".enc.tmp"
    tmp_meta = STATE_PATH + ".meta.dl.tmp"
    try:
        _download_file(service, enc_id, tmp_enc)
        _download_file(service, meta_id, tmp_meta)
        with open(tmp_meta, "r", encoding="utf-8") as f:
            meta = json.load(f)

        if not check_verifier(db.crypto_key, meta.get("verifier", "")):
            raise ValueError(
                "Decryption failed: the master password on this device does not "
                "match the one used to encrypt the cloud copy (or the file is corrupt)."
            )

        decrypt_file(tmp_enc, db.db_path, db.crypto_key)

        state = _load_state()
        state["last_synced_at"] = meta.get("updated_at")
        _save_state(state)
        return meta
    finally:
        if os.path.exists(tmp_enc):
            secure_delete(tmp_enc)
        if os.path.exists(tmp_meta):
            try:
                os.remove(tmp_meta)
            except Exception:
                pass


def _device_name() -> str:
    import socket
    try:
        return socket.gethostname() or "PC"
    except Exception:
        return "PC"
