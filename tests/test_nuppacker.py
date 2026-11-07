import hashlib
import json
import os
import shutil
import struct
import tempfile
import unittest
from pathlib import Path

from nuppacker import (
    parse_ver,
    tlv,
    loc_block,
    discover,
    build_nup,
    auto_detect_title_id,
    discover_summary,
    run_packer_bridge,
    _normalize_obj,
)


class MockMapEntry:
    def __init__(self, key, value):
        self._key = key
        self._value = value

    def getKey(self):
        return self._key

    def getValue(self):
        return self._value


class MockLinkedHashMap:
    """
    Simulates Chaquopy's Java Map proxy.
    Throws TypeError when accessed via subscript obj['key'].
    """
    def __init__(self, d):
        self._entries = [MockMapEntry(k, v) for k, v in d.items()]

    def entrySet(self):
        return self._entries

    def __getitem__(self, key):
        raise TypeError("'LinkedHashMap' object is not subscriptable")


class TestNuppacker(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_parse_ver(self):
        self.assertEqual(parse_ver("1.0.0"), 0x00010000)
        self.assertEqual(parse_ver("1.2.3"), 0x00010203)
        self.assertEqual(parse_ver("0x10000"), 0x10000)
        self.assertEqual(parse_ver("65536"), 65536)

    def test_tlv(self):
        data = tlv(0x0005, b"Super Mario")
        t, reserved, length = struct.unpack_from("<HHI", data, 0)
        self.assertEqual(t, 0x0005)
        self.assertEqual(reserved, 0)
        self.assertEqual(length, len(b"Super Mario"))
        self.assertEqual(data[8:], b"Super Mario")

    def test_loc_block(self):
        block = loc_block([("pt-br", b"Jogo Legal"), ("en-us", b"Cool Game")])
        self.assertTrue(len(block) > 0)
        self.assertIn(b"pt-br", block)
        self.assertIn(b"Jogo Legal", block)

    def test_normalize_obj_with_linked_hash_map(self):
        raw_dict = {
            "title_id": "0400C3F00006E000",
            "folder": "/sdcard/game",
            "zstd": 3,
            "loc_titles": [["pt-br", "Jogo"]],
        }
        mock_map = MockLinkedHashMap(raw_dict)

        # Confirm that mock_map is not subscriptable
        with self.assertRaises(TypeError):
            _ = mock_map["title_id"]

        # Normalize should convert it to a regular python dict
        normalized = _normalize_obj(mock_map)
        self.assertIsInstance(normalized, dict)
        self.assertEqual(normalized["title_id"], "0400C3F00006E000")
        self.assertEqual(normalized["zstd"], 3)

    def test_normalize_obj_with_json_string(self):
        raw_dict = {
            "title_id": "0400C3F00006E000",
            "folder": "/sdcard/game",
            "zstd": 3,
        }
        json_str = json.dumps(raw_dict)
        normalized = _normalize_obj(json_str)
        self.assertIsInstance(normalized, dict)
        self.assertEqual(normalized["title_id"], "0400C3F00006E000")

    def test_run_packer_bridge_with_mock_linked_hash_map_and_spaces_in_path(self):
        # Create dump structure with spaces and brackets like user log:
        # /Mario Kart World + Emulator/MKW [ROM]/
        base_dir = Path(self.test_dir) / "Mario Kart World + Emulator" / "MKW [ROM]" / "base"
        (base_dir / "exefs").mkdir(parents=True)
        (base_dir / "romfs").mkdir(parents=True)

        # Create two identical files to test deduplication
        f1 = base_dir / "romfs" / "file1.bin"
        f2 = base_dir / "romfs" / "file2.bin"
        f1.write_bytes(b"SAME CONTENT" * 100)
        f2.write_bytes(b"SAME CONTENT" * 100)

        out_nup = Path(self.test_dir) / "output dir" / "nested" / "base.nup"
        cfg_dict = {
            "folder": str(base_dir.parent),
            "output": str(out_nup),
            "title_id": "0x0400c3f00006e000",  # Test with 0x and lowercase
            "title": "Mario Kart World",
            "version": "1.0.0",
            "update_version": "1.0.1",
            "display_version": "1.0.0",
            "zstd": 3,  # Recommended level
            "loc_titles": [["pt-br", "Mario Kart Mundo"]],
        }

        # Wrap in MockLinkedHashMap to prove bridge handles Java proxies without subscriptable error
        mock_map = MockLinkedHashMap(cfg_dict)

        logs = []
        progress_values = []
        success_called = [False]

        class DummyCallback:
            def onLog(self, msg):
                logs.append(msg)

            def onProgress(self, p):
                progress_values.append(p)

            def onSuccess(self):
                success_called[0] = True

            def onError(self, err, tb):
                raise RuntimeError(f"onError called: {err}\n{tb}")

        run_packer_bridge(mock_map, DummyCallback())

        self.assertTrue(success_called[0])
        self.assertTrue(out_nup.is_file())
        self.assertGreater(out_nup.stat().st_size, 0x80)
        self.assertTrue(len(progress_values) > 0)

        # Verify Superblock magic
        with open(out_nup, "rb") as f:
            header = f.read(0x80)
            magic, hdr_size, hdr_ver = struct.unpack_from("<4sHH", header, 0)
            self.assertEqual(magic, b"NUP\x01")
            self.assertEqual(hdr_size, 0x80)
            self.assertEqual(hdr_ver, 1)

    def test_toc_parsing_and_navigation(self):
        base_dir = Path(self.test_dir) / "navdump" / "base"
        (base_dir / "exefs").mkdir(parents=True)
        (base_dir / "romfs" / "ui").mkdir(parents=True)
        (base_dir / "exefs" / "main").write_bytes(b"MAIN_EXEC")
        (base_dir / "exefs" / "main.npdm").write_bytes(b"META_HEADER")
        (base_dir / "romfs" / "ui" / "icon.png").write_bytes(b"PNG_DATA")

        out_nup = Path(self.test_dir) / "nav.nup"
        cfg_dict = {
            "folder": str(base_dir.parent),
            "output": str(out_nup),
            "title_id": "0100000000010000",
            "zstd": 0,
        }
        build_nup(cfg_dict)

        with open(out_nup, "rb") as f:
            sb = f.read(0x80)
            magic, hdr_sz, hdr_v, toc_off, toc_sz, toc_sha, blob_off, file_sz, p_cnt, res, uuid, pad = struct.unpack(
                "<4sHHQQ32sQQII16s32s", sb
            )
            self.assertEqual(magic, b"NUP\x01")
            self.assertEqual(hdr_sz, 0x80)
            self.assertEqual(hdr_v, 1)

            f.seek(toc_off)
            toc = f.read(toc_sz)
            self.assertEqual(hashlib.sha256(toc).digest(), toc_sha)

            t_magic, P, D, F, pool_off, pool_len, t_res = struct.unpack_from("<IIQQQQQ", toc, 0)
            self.assertEqual(t_magic, 0x01434F54)
            self.assertEqual((P, D, F), (1, 4, 3))

            off = 0x30
            PKG_S = struct.Struct("<QIIIIBBHIQI20s")
            self.assertEqual(PKG_S.size, 0x40)
            tid, root, exefs, romfs, ver, kind, flags, r1, r2, m_off, m_len, p_pad = PKG_S.unpack_from(toc, off)
            self.assertEqual(root, 0)
            self.assertEqual(exefs, 1)
            self.assertEqual(romfs, 2)
            self.assertEqual(r1, 0)
            self.assertEqual(r2, 0)
            off += P * 0x40

            DIR_S = struct.Struct("<IIIIQ")
            self.assertEqual(DIR_S.size, 0x18)
            dirs = []
            for _ in range(D):
                no, nl, first, count, r = DIR_S.unpack_from(toc, off)
                dirs.append((no, nl, first, count))
                off += 0x18

            FILE_S = struct.Struct("<IIQQQ32sBBH12s")
            self.assertEqual(FILE_S.size, 0x50)
            files = []
            for _ in range(F):
                no, nl, sz, eoff, esz, sha, codec, flags, r, f_pad = FILE_S.unpack_from(toc, off)
                files.append((no, nl, sz, eoff, esz, sha, codec))
                off += 0x50

            total_children = sum(c for _, _, _, c in dirs)
            children = struct.unpack_from(f"<{total_children}I", toc, off)
            self.assertEqual(off + total_children * 4, pool_off)

            pool = toc[pool_off : pool_off + pool_len]
            def get_str(no, nl):
                return pool[no : no + nl].decode("utf-8")

            # Check navigation
            root_no, root_nl, r_first, r_count = dirs[0]
            self.assertEqual(get_str(root_no, root_nl), "base")
            root_children = children[r_first : r_first + r_count]
            self.assertIn(1, root_children) # exefs
            self.assertIn(2, root_children) # romfs
            self.assertNotIn(0, root_children) # no circular reference

            exefs_no, exefs_nl, e_first, e_count = dirs[1]
            self.assertEqual(get_str(exefs_no, exefs_nl), "exefs")
            exefs_children = children[e_first : e_first + e_count]
            exefs_file_names = [get_str(files[c - D][0], files[c - D][1]) for c in exefs_children if c >= D]
            self.assertIn("main", exefs_file_names)
            self.assertIn("main.npdm", exefs_file_names)

    def test_cancellation(self):
        base_dir = Path(self.test_dir) / "canceldump" / "base"
        (base_dir / "exefs").mkdir(parents=True)
        (base_dir / "romfs").mkdir(parents=True)
        (base_dir / "romfs" / "big.bin").write_bytes(b"DATA" * 500)

        out_nup = Path(self.test_dir) / "cancel_out.nup"
        cfg_dict = {
            "folder": str(base_dir.parent),
            "output": str(out_nup),
            "title_id": "0100000000010000",
            "zstd": 0,
        }

        cancelled_called = [False]

        class CancelCallback:
            def onLog(self, msg): pass
            def onProgress(self, p): pass
            def onSuccess(self): pass
            def onError(self, err, tb): pass
            def onCancelled(self):
                cancelled_called[0] = True
            def isCancelled(self):
                return True

        run_packer_bridge(cfg_dict, CancelCallback())
        self.assertTrue(cancelled_called[0])
        self.assertFalse(out_nup.is_file())
        self.assertFalse(Path(str(out_nup) + ".part").is_file())


if __name__ == "__main__":
    unittest.main()
