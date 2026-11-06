import argparse
import sys
from pathlib import Path
from nuppacker import build_nup, auto_detect_title_id


def main():
    parser = argparse.ArgumentParser(description="NUPPacker CLI (Android Termux / Standalone)")
    parser.add_argument("folder", help="Input dump directory")
    parser.add_argument("-o", "--output", help="Output .nup file path")
    parser.add_argument("-t", "--title-id", help="16-hex digit Title ID")
    parser.add_argument("-n", "--name", default="", help="Title name")
    parser.add_argument("-v", "--version", default="1.0.0", help="Base version")
    parser.add_argument("-u", "--update", help="Optional extra update folder")
    parser.add_argument("-d", "--dlc", help="Optional DLC folder")
    parser.add_argument("-z", "--zstd", type=int, default=0, help="Zstd level (0-22)")
    args = parser.parse_args()

    folder = Path(args.folder)
    out = args.output or str(folder.with_suffix(".nup"))
    tid = args.title_id or auto_detect_title_id(str(folder))
    if not tid:
        print("Error: Could not auto-detect Title ID; please specify --title-id")
        sys.exit(1)

    cfg = {
        "folder": str(folder),
        "update": args.update,
        "dlc": args.dlc,
        "output": out,
        "title_id": tid,
        "title": args.name,
        "version": args.version,
        "zstd": args.zstd,
    }
    build_nup(cfg)


if __name__ == "__main__":
    main()
