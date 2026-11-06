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
)


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
        # struct.pack("<HHI", t, 0, len(payload)) + payload
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

    def test_discover_and_build_nup(self):
        base_dir = Path(self.test_dir) / "game_dump" / "base"
        (base_dir / "exefs").mkdir(parents=True)
        (base_dir / "romfs").mkdir(parents=True)

        # Create dummy file in romfs
        hello_file = base_dir / "romfs" / "hello.txt"
        hello_file.write_text("Hello Switch 2 NUP world!")

        # Discover
        specs = discover(Path(self.test_dir) / "game_dump")
        self.assertEqual(len(specs), 1)
        self.assertEqual(specs[0][0], 0)  # kind == base

        out_nup = Path(self.test_dir) / "output.nup"
        cfg = {
            "folder": str(Path(self.test_dir) / "game_dump"),
            "update": "",
            "dlc": "",
            "output": str(out_nup),
            "title_id": "0100000000010000",
            "title": "Test Game",
            "version": "1.0.0",
            "update_version": "1.0.0",
            "display_version": "1.0.0",
            "icon": "",
            "zstd": 0,
            "loc_titles": [("en", "Test Game")],
        }

        logs = []
        progress_calls = []
        build_nup(cfg, log=logs.append, progress=progress_calls.append)

        self.assertTrue(out_nup.is_file())
        self.assertGreater(out_nup.stat().st_size, 0x80)

        # Verify Superblock
        with open(out_nup, "rb") as f:
            header = f.read(0x80)
            magic, hdr_size, hdr_ver = struct.unpack_from("<4sHH", header, 0)
            self.assertEqual(magic, b"NUP\x01")
            self.assertEqual(hdr_size, 0x80)
            self.assertEqual(hdr_ver, 1)


if __name__ == "__main__":
    unittest.main()
