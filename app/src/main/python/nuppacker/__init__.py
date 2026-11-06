"""
NUPPacker Core Engine — High Performance Android Overhaul
Optimized for mobile storage throughput, single-pass streaming, multi-threading and background jobs.
Licensed under GNU AGPL v3.0.
"""

import hashlib
import json
import os
import re
import struct
import sys
import traceback
import unicodedata
from pathlib import Path

NO_DIR = 0xFFFFFFFF
CHUNK = 4 * 1024 * 1024  # 4MB optimal buffer for mobile flash storage page cache
align16 = lambda n: (n + 15) & ~15


# ----------------------------------------------------------------- bridge helpers
def _normalize_obj(obj):
    """
    Recursively normalizes Java/Kotlin proxies (LinkedHashMap, ArrayList),
    JSON strings, and native Python data structures into pure Python dicts/lists.
    """
    if obj is None:
        return None
    if isinstance(obj, str):
        s = obj.strip()
        if (s.startswith("{") and s.endswith("}")) or (s.startswith("[") and s.endswith("]")):
            try:
                return _normalize_obj(json.loads(s))
            except Exception:
                pass
        return str(obj)
    if hasattr(obj, "entrySet"):
        out = {}
        for entry in obj.entrySet():
            k = str(entry.getKey())
            out[k] = _normalize_obj(entry.getValue())
        return out
    if hasattr(obj, "items"):
        return {str(k): _normalize_obj(v) for k, v in obj.items()}
    if hasattr(obj, "toArray"):
        return [_normalize_obj(x) for x in obj.toArray()]
    if isinstance(obj, (list, tuple)):
        return [_normalize_obj(x) for x in obj]
    if isinstance(obj, (int, float, bool)):
        return obj
    return obj


# ----------------------------------------------------------------- tree model
class Dir:
    def __init__(s, name):
        s.name, s.dirs, s.files, s.idx, s.first, s.count = name, [], [], 0, 0, 0


class File:
    def __init__(s, name, path):
        s.name, s.path, s.idx = name, path, 0
        s.size = 0
        s.sha = b""
        s.codec = 0
        s.eoff = 0
        s.esize = 0


RESERVED = {".listing"}


def scan(path, name):
    d = Dir(name)
    for e in path.iterdir():
        if e.name in RESERVED:
            continue
        n = unicodedata.normalize("NFC", e.name)
        if e.is_dir():
            d.dirs.append(scan(e, n))
        elif e.is_file():
            d.files.append(File(n, e))
    return d


def collect(d, dirs, files):
    d.idx = len(dirs)
    dirs.append(d)
    for s in d.dirs:
        collect(s, dirs, files)
    for f in d.files:
        f.idx = len(files)
        files.append(f)


# ----------------------------------------------------------------- helpers
HEX16 = re.compile(r"^[0-9A-Fa-f]{16}$")


def is_pkg_dir(p: Path):
    return p.is_dir() and any((p / n).is_dir() for n in ("exefs", "romfs", "data"))


def parse_version_name(name):
    n = name.lower().lstrip("v")
    try:
        return int(n, 16) if n.startswith("0x") else int(n)
    except ValueError:
        return None


def version_dirs(root: Path):
    if is_pkg_dir(root):
        return [(None, root)]
    out = []
    for c in root.iterdir():
        v = parse_version_name(c.name)
        if v is not None and is_pkg_dir(c):
            out.append((v, c))
    return sorted(out)


def discover_dlc(dlc: Path):
    if HEX16.match(dlc.name):
        return [(2, int(dlc.name, 16), v, p) for v, p in version_dirs(dlc)]
    out = []
    for t in sorted(dlc.iterdir()):
        if t.is_dir() and HEX16.match(t.name):
            out += [(2, int(t.name, 16), v, p) for v, p in version_dirs(t)]
    return out


def discover(folder: Path):
    if is_pkg_dir(folder):
        return [(0, None, None, folder)]
    specs = []
    if is_pkg_dir(folder / "base"):
        specs.append((0, None, None, folder / "base"))
    if (folder / "updates").is_dir():
        specs += [(1, None, v, p) for v, p in version_dirs(folder / "updates")]
    for n in ("dlc", "addon"):
        if (folder / n).is_dir():
            specs += discover_dlc(folder / n)
    if not specs:
        specs = [(1, None, v, p) for v, p in version_dirs(folder)] or discover_dlc(
            folder
        )
    return specs


KIND_NAMES = {0: "base", 1: "update", 2: "dlc"}
ver_str = lambda v: f"{v >> 16}.{(v >> 8) & 0xFF}.{v & 0xFF}"


def plan_packages(cfg, log):
    cfg = _normalize_obj(cfg)
    raw_tid = str(cfg.get("title_id", "")).strip()
    if raw_tid.lower().startswith("0x"):
        raw_tid = raw_tid[2:]
    if not raw_tid:
        raise ValueError("Title ID is required.")
    try:
        tid = int(raw_tid, 16)
    except ValueError:
        raise ValueError(f"Invalid Title ID '{raw_tid}': must be a 16-hex-digit number.")

    folder_path = Path(str(cfg["folder"]).strip())
    specs = discover(folder_path)

    update_opt = cfg.get("update")
    if update_opt and Path(str(update_opt).strip()).is_dir():
        specs += [(1, None, v, p) for v, p in version_dirs(Path(str(update_opt).strip()))]

    dlc_opt = cfg.get("dlc")
    if dlc_opt and Path(str(dlc_opt).strip()).is_dir():
        specs += discover_dlc(Path(str(dlc_opt).strip()))

    if not specs:
        raise ValueError(
            "No packages found: expected base/, update/<version>/, dlc/<titleid>/ "
            "(or a folder that directly contains exefs/ or romfs/)."
        )
    bases = [s for s in specs if s[0] == 0]
    if len(bases) > 1:
        raise ValueError("More than one base package.")
    has_base = bool(bases)

    norm = []
    for kind, ptid, ver, path in specs:
        if kind == 0:
            ptid, ver = tid, parse_ver(cfg.get("version", "1.0.0"))
        elif kind == 1:
            ptid = tid | 0x800
            ver = ver if ver is not None else parse_ver(cfg.get("update_version", "1.0.1"))
        else:
            if ptid is None:
                raise ValueError(
                    f"{path}: DLC needs a 16-hex-digit title ID folder name."
                )
            ver = ver or 0
        norm.append((kind, ptid, ver, path))
    norm.sort(key=lambda s: (s[0], s[2]))
    keys = [(k, t, v) for k, t, v, _ in norm]
    if len(set(keys)) != len(keys):
        raise ValueError("Two packages share the same kind, title ID and version.")

    updates = [s for s in norm if s[0] == 1]
    best_update = updates[-1] if updates else None
    disp_target = best_update or norm[0]
    pkgs = []
    for s in norm:
        kind, ptid, ver, path = s
        meta = b""
        if kind == 0 or (not has_base and s is best_update):
            meta += app_meta(cfg, path, log)
        if cfg.get("display_version") and s is disp_target:
            meta += tlv(0x0004, str(cfg["display_version"]).encode("utf-8"))
        pkgs.append(
            Pkg(path, kind, ptid, ver, 1 if (kind == 1 and not has_base) else 0, meta)
        )
        log(f"  {KIND_NAMES[kind]:6} {ptid:016X}  v{ver} ({ver_str(ver)})  {path}")
    return pkgs


def tlv(t, payload):
    return struct.pack("<HHI", t, 0, len(payload)) + payload


def loc_block(items):
    seen, out = set(), b""
    for lang, data in items:
        l = lang.strip().lower()
        if not l or l in seen:
            continue
        seen.add(l)
        lb = l.encode("utf-8")
        out += bytes([len(lb)]) + lb + struct.pack("<I", len(data)) + data
    return out


def sha256_file(p):
    h = hashlib.sha256()
    with open(p, "rb", buffering=1024 * 1024) as f:
        while c := f.read(CHUNK):
            h.update(c)
    return h.digest()


def app_meta(cfg, base_dir, log=print):
    m = b""

    nacp_path = cfg.get("nacp")
    if nacp_path and Path(str(nacp_path)).is_file():
        m += tlv(0x0002, Path(str(nacp_path)).read_bytes())

    icon_path = cfg.get("icon")
    if icon_path and Path(str(icon_path)).is_file():
        m += tlv(0x0003, Path(str(icon_path)).read_bytes())

    if cfg.get("title"):
        m += tlv(0x0005, str(cfg["title"]).encode("utf-8"))

    if cfg.get("publisher"):
        m += tlv(0x0006, str(cfg["publisher"]).encode("utf-8"))

    if base_dir and (base_dir / "exefs" / "main").is_file():
        m += tlv(0x0007, sha256_file(base_dir / "exefs" / "main"))

    folder_name = Path(str(cfg["folder"])).name
    m += tlv(0x0008, folder_name.encode("utf-8"))

    loc_titles = cfg.get("loc_titles")
    if loc_titles and isinstance(loc_titles, (list, tuple)):
        valid_items = []
        for item in loc_titles:
            if isinstance(item, (list, tuple)) and len(item) >= 2 and item[0] and item[1]:
                valid_items.append((str(item[0]).strip(), str(item[1]).strip().encode("utf-8")))
        if valid_items:
            m += tlv(0x0009, loc_block(valid_items))

    return m


def parse_ver(s):
    if isinstance(s, int):
        return s
    s = str(s).strip()
    if s.lower().startswith("0x"):
        return int(s, 16)
    if "." not in s:
        return int(s)
    a = [int(x) for x in s.split(".")] + [0, 0, 0]
    return ((a[0] & 0xFFFF) << 16) | ((a[1] & 0xFF) << 8) | (a[2] & 0xFF)


def guess_title_id(folder: Path):
    try:
        folder = Path(folder)
        npdm_path = folder / "exefs" / "main.npdm"
        if not npdm_path.is_file():
            npdm_path = folder / "main.npdm"
        if not npdm_path.is_file():
            return ""
        d = npdm_path.read_bytes()
        if d[:4] != b"META":
            return ""
        aci0 = struct.unpack_from("<I", d, 0x70)[0]
        if d[aci0 : aci0 + 4] != b"ACI0":
            return ""
        return f"{struct.unpack_from('<Q', d, aci0 + 0x10)[0]:016X}"
    except Exception:
        return ""


class Pkg:
    def __init__(s, path, kind, title_id, version, flags, meta):
        path = Path(path)
        s.kind, s.title_id, s.version, s.flags = kind, title_id, version, flags
        s.root = Dir(path.name or "pkg")
        s.exefs = s.romfs = None
        if (path / "exefs").is_dir():
            s.exefs = scan(path / "exefs", "exefs")
            s.root.dirs.append(s.exefs)
        if (path / "romfs").is_dir():
            s.romfs = scan(path / "romfs", "romfs")
            s.root.dirs.append(s.romfs)
        if (path / "data").is_dir():
            s.root.dirs.append(scan(path / "data", "data"))
        m = b""
        npdm = path / "exefs" / "main.npdm"
        if npdm.is_file():
            m += tlv(0x0001, npdm.read_bytes())
        s.meta, s.meta_off = m + meta, 0


# ----------------------------------------------------------------- builder (high-performance overhaul)
def build_nup(cfg, log=print, progress=lambda p: None, is_cancelled=lambda: False):
    cfg = _normalize_obj(cfg)
    pkgs = plan_packages(cfg, log)
    log("Packages: " + ", ".join("base" if p.kind == 0 else "update" for p in pkgs))

    dirs, files = [], []
    for p in pkgs:
        collect(p.root, dirs, files)
    D, F, P = len(dirs), len(files), len(pkgs)
    total_bytes = max(sum(f.path.stat().st_size for f in files), 1)
    log(f"{D} dirs, {F} files, {total_bytes / 1e9:.2f} GB")

    children = []
    for d in dirs:
        d.first = len(children)
        nodes = sorted(
            [(s.name.encode("utf-8"), s.idx) for s in d.dirs]
            + [(f.name.encode("utf-8"), D + f.idx) for f in d.files]
        )
        children += [i for _, i in nodes]
        d.count = len(nodes)

    pool, pmap = bytearray(), {}

    def intern(s):
        if s not in pmap:
            b = s.encode("utf-8")
            pmap[s] = (len(pool), len(b))
            pool.extend(b)

    for d in dirs:
        intern(d.name)
    for f in files:
        intern(f.name)

    pool_off = 0x30 + P * 0x40 + D * 0x18 + F * 0x50 + 4 * len(children)
    toc_size = align16(pool_off + len(pool))

    pos = align16(0x80 + toc_size)
    for p in pkgs:
        if p.meta:
            p.meta_off = pos
            pos = align16(pos + len(p.meta))
    blob_off = pos

    cctx = None
    zstd_level = int(cfg.get("zstd", 3))
    threads_count = min(os.cpu_count() or 4, 8)
    if zstd_level > 0:
        try:
            import zstandard
            try:
                cctx = zstandard.ZstdCompressor(level=zstd_level, threads=threads_count)
                log(f"Zstandard active: level {zstd_level} ({threads_count} threads)")
            except (TypeError, Exception):
                cctx = zstandard.ZstdCompressor(level=zstd_level)
                log(f"Zstandard active: level {zstd_level} (single thread)")
        except Exception as e:
            log(f"Warning: zstandard unavailable ({e}), proceeding raw")
            cctx = None

    done = 0
    out_target = str(cfg["output"])
    part = out_target + ".part"
    Path(part).parent.mkdir(parents=True, exist_ok=True)

    try:
        with open(part, "wb", buffering=1024 * 1024) as out:
            out.seek(blob_off)
            seen = {}
            for f in files:
                if is_cancelled and is_cancelled():
                    log("Cancellation requested. Aborting packing.")
                    raise InterruptedError("Cancelled by user")

                f.size = f.path.stat().st_size
                if f.size > (128 << 20):
                    log(f"Packing {f.path.name} ({f.size / 1e9:.2f} GB)")
                if f.size == 0:
                    f.sha = hashlib.sha256(b"").digest()
                    continue

                before = pos
                pad = (-pos) % 16
                out.write(b"\0" * pad)
                pos += pad
                start = pos
                h = hashlib.sha256()
                stored_raw = True

                # OPTIMIZATION: Single-pass streaming read & hash & compress
                if cctx:
                    try:
                        compressor = cctx.compressobj()
                        compressed_len = 0
                        with open(f.path, "rb", buffering=1024 * 1024) as fin:
                            while chunk := fin.read(CHUNK):
                                if is_cancelled and is_cancelled():
                                    raise InterruptedError("Cancelled by user")
                                h.update(chunk)
                                c_data = compressor.compress(chunk)
                                if c_data:
                                    out.write(c_data)
                                    compressed_len += len(c_data)
                        c_tail = compressor.flush()
                        if c_tail:
                            out.write(c_tail)
                            compressed_len += len(c_tail)

                        if compressed_len < f.size:
                            stored_raw = False
                            f.codec = 1
                            f.esize = compressed_len
                            done += f.size
                        else:
                            # Not smaller: discard compressed output and store raw
                            out.seek(start)
                            out.truncate()
                            stored_raw = True
                    except InterruptedError:
                        raise
                    except Exception as ce:
                        log(f"Compression fallback on {f.path.name} ({ce})")
                        out.seek(start)
                        out.truncate()
                        stored_raw = True

                if stored_raw:
                    f.codec = 0
                    with open(f.path, "rb", buffering=1024 * 1024) as fin:
                        while chunk := fin.read(CHUNK):
                            if is_cancelled and is_cancelled():
                                raise InterruptedError("Cancelled by user")
                            if not cctx or f.codec == 0:
                                h.update(chunk)
                            out.write(chunk)
                            if not cctx:
                                done += len(chunk)
                                progress(done / total_bytes)
                    f.esize = f.size
                    if cctx:
                        done += f.size

                f.sha = h.digest()
                key = (f.sha, f.size)
                if key in seen:
                    f.eoff, f.esize, f.codec = seen[key]
                    out.seek(before)
                    out.truncate()
                    pos = before
                else:
                    f.eoff = start
                    seen[key] = (start, f.esize, f.codec)
                    pos = start + f.esize
                progress(done / total_bytes)
            file_size = pos

            PKG_S = struct.Struct("<QIIIIBBHIQI20s")  # 0x40
            DIR_S = struct.Struct("<IIIIQ")  # 0x18
            FILE_S = struct.Struct("<IIQQQ32sBBH12s")  # 0x50
            assert (PKG_S.size, DIR_S.size, FILE_S.size) == (0x40, 0x18, 0x50)

            log("Building table of contents…")
            parts = [struct.pack("<IIQQQQQ", 0x01434F54, P, D, F, pool_off, len(pool), 0)]
            for i, p in enumerate(pkgs):
                parts.append(
                    PKG_S.pack(
                        p.title_id,
                        p.root.idx,
                        p.exefs.idx if p.exefs else NO_DIR,
                        p.romfs.idx if p.romfs else NO_DIR,
                        p.version,
                        p.kind,
                        p.flags,
                        0,
                        0,
                        p.meta_off,
                        len(p.meta),
                        b"\0" * 20,
                    )
                )

            for i, d in enumerate(dirs):
                no, nl = pmap[d.name]
                parts.append(DIR_S.pack(no, nl, d.first, d.count, 0))

            for i, f in enumerate(files):
                no, nl = pmap[f.name]
                parts.append(
                    FILE_S.pack(
                        no, nl, f.size, f.eoff, f.esize, f.sha, f.codec, 0, 0, b"\0" * 12
                    )
                )

            parts.append(struct.pack(f"<{len(children)}I", *children))
            parts.append(bytes(pool))
            toc = b"".join(parts).ljust(toc_size, b"\0")
            assert len(toc) == toc_size
            log(f"TOC generated ({len(toc) / 1e6:.1f} MB)")

            uh = hashlib.sha256()
            for p in pkgs:
                uh.update(struct.pack("<QIB", p.title_id, p.version, p.kind))

            sup = struct.pack(
                "<4sHHQQ32sQQII16s32s",
                b"NUP\x01",
                0x80,
                1,
                0x80,
                toc_size,
                hashlib.sha256(toc).digest(),
                blob_off,
                file_size,
                P,
                0,
                uh.digest()[:16],
                b"\0" * 32,
            )
            assert len(sup) == 0x80
            out.seek(0)
            out.write(sup)
            out.write(toc)
            for p in pkgs:
                if p.meta:
                    out.seek(p.meta_off)
                    out.write(p.meta)
            out.truncate(file_size)

        os.replace(part, out_target)
        log(f"Done: {out_target} ({file_size / 1e9:.2f} GB)")

    except InterruptedError:
        if os.path.exists(part):
            try:
                os.remove(part)
            except Exception:
                pass
        raise
    except Exception:
        if os.path.exists(part):
            try:
                os.remove(part)
            except Exception:
                pass
        raise


# ----------------------------------------------------------------- Android Bridge
def auto_detect_title_id(folder_path: str) -> str:
    folder = Path(str(folder_path).strip())
    specs = discover(folder)
    path = specs[0][3] if specs else None
    return guess_title_id(path) if path else ""


def discover_summary(folder_path: str) -> list:
    out = []
    for k, t, v, p in discover(Path(str(folder_path).strip())):
        out.append({
            "kind": KIND_NAMES.get(k, "unknown"),
            "version": v,
            "path": str(p),
        })
    return out


def run_packer_bridge(cfg_input, callback=None):
    def _log(msg):
        if callback and hasattr(callback, "onLog"):
            callback.onLog(str(msg))
        else:
            print(msg)

    def _prog(val):
        if callback and hasattr(callback, "onProgress"):
            callback.onProgress(float(val))

    def _is_canc():
        if callback and hasattr(callback, "isCancelled"):
            try:
                return bool(callback.isCancelled())
            except Exception:
                return False
        return False

    try:
        cfg = _normalize_obj(cfg_input)
        build_nup(cfg, _log, _prog, _is_canc)
        if callback and hasattr(callback, "onSuccess"):
            callback.onSuccess()
    except InterruptedError:
        _log("Packing operation cancelled successfully.")
        if callback and hasattr(callback, "onCancelled"):
            callback.onCancelled()
    except Exception as e:
        tb = traceback.format_exc()
        if callback and hasattr(callback, "onError"):
            callback.onError(str(e), tb)
        else:
            raise
