import os
import time
import gc
import json
import base64
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes

KEY_ITERATIONS = 600000
_NONCE_LEN = 12
_VERIFIER_PLAINTEXT = b"pfm-verifier-ok"


def gen_salt() -> bytes:
    return os.urandom(16)


def derive_key(password: str, salt: bytes) -> bytes:
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        iterations=KEY_ITERATIONS,
    )
    return kdf.derive(password.encode("utf-8"))


def encrypt_bytes(data: bytes, key: bytes) -> bytes:
    nonce = os.urandom(_NONCE_LEN)
    ct = AESGCM(key).encrypt(nonce, data, None)
    return nonce + ct


def decrypt_bytes(blob: bytes, key: bytes) -> bytes:
    nonce, ct = blob[:_NONCE_LEN], blob[_NONCE_LEN:]
    return AESGCM(key).decrypt(nonce, ct, None)


def encrypt_file(src_path: str, dst_path: str, key: bytes):
    with open(src_path, "rb") as f:
        data = f.read()
    blob = encrypt_bytes(data, key)
    with open(dst_path, "wb") as f:
        f.write(blob)


def decrypt_file(src_path: str, dst_path: str, key: bytes):
    with open(src_path, "rb") as f:
        blob = f.read()
    data = decrypt_bytes(blob, key)
    with open(dst_path, "wb") as f:
        f.write(data)


def make_verifier(key: bytes) -> str:
    blob = encrypt_bytes(_VERIFIER_PLAINTEXT, key)
    return base64.b64encode(blob).decode("ascii")


def check_verifier(key: bytes, verifier_b64: str) -> bool:
    try:
        blob = base64.b64decode(verifier_b64)
        return decrypt_bytes(blob, key) == _VERIFIER_PLAINTEXT
    except Exception:
        return False


def meta_path(db_path: str) -> str:
    return db_path + ".meta"


def load_meta(db_path: str) -> dict:
    p = meta_path(db_path)
    if not os.path.exists(p):
        return {"encryption": False}
    try:
        with open(p, "r", encoding="utf-8") as f:
            meta = json.load(f)
        meta.setdefault("encryption", False)
        return meta
    except Exception:
        return {"encryption": False}


def save_meta(db_path: str, meta: dict):
    with open(meta_path(db_path), "w", encoding="utf-8") as f:
        json.dump(meta, f)


def enc_path(db_path: str) -> str:
    return db_path + ".enc"


def secure_delete(path: str, retries: int = 6):
    # On Windows, sqlite keeps an OS file lock on a just-closed connection
    # until the connection object is garbage-collected. Force collection so
    # the plaintext database file can actually be removed/renamed.
    gc.collect()
    if not os.path.exists(path):
        return
    shred = path + ".shred"
    try:
        if os.path.exists(shred):
            os.remove(shred)
        os.rename(path, shred)
    except Exception:
        shred = path
    try:
        size = os.path.getsize(shred)
        with open(shred, "r+b") as f:
            for _ in range(3):
                f.seek(0)
                f.write(os.urandom(size))
    except Exception:
        pass
    last_err = None
    for _ in range(retries):
        try:
            os.remove(shred)
            return
        except Exception as e:
            last_err = e
            time.sleep(0.2)
    # If we still cannot remove the shredded copy, leave it (the original path
    # has already been renamed away, so the app will not see a corrupted file).
    if shred != path and last_err is not None:
        try:
            os.remove(path)
        except Exception:
            pass
