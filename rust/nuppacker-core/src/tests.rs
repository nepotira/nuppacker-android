use std::fs;
use std::io::Read;
use std::sync::atomic::AtomicBool;
use std::sync::Arc;
use sha2::{Digest, Sha256};
use tempfile::TempDir;

use crate::discovery::{analyze_folder, parse_ver, ver_str, PackerConfig};
use crate::packer::{build_nup, fast_entropy_probe, is_incompressible_ext};
use crate::spec::*;

#[test]
fn test_align16() {
    assert_eq!(align16(0), 0);
    assert_eq!(align16(1), 16);
    assert_eq!(align16(15), 16);
    assert_eq!(align16(16), 16);
    assert_eq!(align16(17), 32);
    assert_eq!(align16(0x80), 0x80);
}

#[test]
fn test_parse_version() {
    assert_eq!(parse_ver("1.0.0"), 0x00010000);
    assert_eq!(parse_ver("1.2.3"), 0x00010203);
    assert_eq!(parse_ver("0x00010203"), 0x00010203);
    assert_eq!(parse_ver("65536"), 0x00010000);
    assert_eq!(ver_str(0x00010203), "1.2.3");
}

#[test]
fn test_tlv_serialization() {
    let payload = b"Hello NUP";
    let tlv = make_tlv(0x0005, payload);
    assert_eq!(tlv.len(), 8 + payload.len());
    assert_eq!(&tlv[0..2], &0x0005u16.to_le_bytes());
    assert_eq!(&tlv[2..4], &0u16.to_le_bytes());
    assert_eq!(&tlv[4..8], &(payload.len() as u32).to_le_bytes());
    assert_eq!(&tlv[8..], payload);
}

#[test]
fn test_synthetic_nup_build_and_superblock() {
    let temp_dir = TempDir::new().expect("Failed creating temp dir");
    let dump_dir = temp_dir.path().join("game_dump");
    let exefs = dump_dir.join("exefs");
    let romfs = dump_dir.join("romfs");
    fs::create_dir_all(&exefs).unwrap();
    fs::create_dir_all(&romfs).unwrap();

    fs::write(exefs.join("main"), b"DUMMY_EXECUTABLE_CONTENT").unwrap();
    fs::write(romfs.join("data.bin"), b"DUMMY_GAME_RESOURCE_CONTENT_SAMPLE").unwrap();

    let output_nup = temp_dir.path().join("test_game.nup");

    let cfg = PackerConfig {
        folder: dump_dir.to_string_lossy().to_string(),
        update: None,
        dlc: None,
        output: output_nup.to_string_lossy().to_string(),
        title_id: "0100000000010000".to_string(),
        title: Some("Synthetic Test Game".to_string()),
        publisher: Some("Antigravity".to_string()),
        version: Some("1.0.0".to_string()),
        update_version: None,
        display_version: Some("1.0.0".to_string()),
        nacp: None,
        icon: None,
        zstd: Some(3),
        loc_titles: None,
    };

    let cancelled = Arc::new(AtomicBool::new(false));
    let res = build_nup(&cfg, |_| {}, |_| {}, cancelled);
    assert!(res.is_ok(), "Build failed: {:?}", res.err());

    assert!(output_nup.exists(), "Output NUP file was not created");

    let mut file = fs::File::open(&output_nup).unwrap();
    let mut header = vec![0u8; 0x80];
    file.read_exact(&mut header).unwrap();

    // Check Magic
    assert_eq!(&header[0..4], b"NUP\x01");
    // Check Header Size
    assert_eq!(u16::from_le_bytes(header[4..6].try_into().unwrap()), 0x80);
    // Check Header Version
    assert_eq!(u16::from_le_bytes(header[6..8].try_into().unwrap()), 1);
    // Check TOC Offset
    assert_eq!(u64::from_le_bytes(header[8..16].try_into().unwrap()), 0x80);

    let toc_size = u64::from_le_bytes(header[16..24].try_into().unwrap());
    assert!(toc_size > 0 && toc_size % 16 == 0, "TOC size must be aligned to 16 bytes");
}

#[test]
fn test_incompressible_ext() {
    use std::path::Path;
    assert!(is_incompressible_ext(Path::new("video.mp4")));
    assert!(is_incompressible_ext(Path::new("texture.astc")));
    assert!(is_incompressible_ext(Path::new("archive.szs")));
    assert!(is_incompressible_ext(Path::new("audio.opus")));
    assert!(!is_incompressible_ext(Path::new("main.npdm")));
    assert!(!is_incompressible_ext(Path::new("code.bin")));
}

#[test]
fn test_entropy_probe() {
    // Low entropy (uniform zeros)
    let uniform = vec![0u8; 1024];
    assert!(fast_entropy_probe(&uniform) < 0.1);

    // High entropy (simulated random bytes)
    let mut random = Vec::with_capacity(256 * 10);
    for _ in 0..10 {
        for b in 0..=255u8 {
            random.push(b);
        }
    }
    assert!(fast_entropy_probe(&random) > 7.9);
}

#[test]
fn test_analyze_folder() {
    let temp_dir = TempDir::new().unwrap();
    let folder = temp_dir.path().join("dump");
    let exefs = folder.join("exefs");
    fs::create_dir_all(&exefs).unwrap();
    fs::write(exefs.join("main"), b"TEST_BINARY").unwrap();

    let analysis = analyze_folder(&folder);
    assert_eq!(analysis.file_count, 1);
    assert_eq!(analysis.total_bytes, 11);
    assert_eq!(analysis.packages.len(), 1);
    assert_eq!(analysis.packages[0].kind, "base");
}

#[test]
fn test_small_file_raw_fallback_and_compression() {
    let temp_dir = TempDir::new().unwrap();
    let dump = temp_dir.path().join("dump");
    let exefs = dump.join("exefs");
    let romfs = dump.join("romfs");
    fs::create_dir_all(&exefs).unwrap();
    fs::create_dir_all(&romfs).unwrap();

    fs::write(exefs.join("main"), b"MAIN_EXEC").unwrap();
    let mut incompressible = Vec::new();
    for i in 0..1024 {
        incompressible.push(((i * 37 + 11) % 256) as u8);
    }
    fs::write(romfs.join("raw.bin"), &incompressible).unwrap();

    let out_nup = temp_dir.path().join("out.nup");
    let cfg = PackerConfig {
        folder: dump.to_string_lossy().to_string(),
        update: None,
        dlc: None,
        output: out_nup.to_string_lossy().to_string(),
        title_id: "0100000000010000".to_string(),
        title: Some("Test".to_string()),
        publisher: None,
        version: Some("1.0.0".to_string()),
        update_version: None,
        display_version: None,
        nacp: None,
        icon: None,
        zstd: Some(3),
        loc_titles: None,
    };

    let cancelled = Arc::new(AtomicBool::new(false));
    let res = build_nup(&cfg, |_| {}, |_| {}, cancelled);
    assert!(res.is_ok(), "Build failed: {:?}", res.err());
    assert!(out_nup.exists());
}

#[test]
fn test_cancellation_deletes_part_file() {
    let temp_dir = TempDir::new().unwrap();
    let dump = temp_dir.path().join("dump");
    let exefs = dump.join("exefs");
    let romfs = dump.join("romfs");
    fs::create_dir_all(&exefs).unwrap();
    fs::create_dir_all(&romfs).unwrap();

    fs::write(exefs.join("main"), b"MAIN_EXEC").unwrap();
    fs::write(romfs.join("data.bin"), vec![0xAA; 1024 * 1024]).unwrap();

    let out_nup = temp_dir.path().join("cancelled_game.nup");
    let part_path = temp_dir.path().join("cancelled_game.nup.part");

    let cfg = PackerConfig {
        folder: dump.to_string_lossy().to_string(),
        update: None,
        dlc: None,
        output: out_nup.to_string_lossy().to_string(),
        title_id: "0100000000010000".to_string(),
        title: Some("CancelTest".to_string()),
        publisher: None,
        version: Some("1.0.0".to_string()),
        update_version: None,
        display_version: None,
        nacp: None,
        icon: None,
        zstd: Some(3),
        loc_titles: None,
    };

    let cancelled = Arc::new(AtomicBool::new(true)); // Pre-cancelled
    let res = build_nup(&cfg, |_| {}, |_| {}, cancelled);
    assert!(res.is_err(), "Expected build to fail due to cancellation");
    assert!(!out_nup.exists(), "Final .nup should not exist when cancelled");
    assert!(!part_path.exists(), ".part file must be deleted when cancelled");
}

#[test]
fn test_toc_parsing_and_navigation() {
    let temp_dir = TempDir::new().expect("Failed creating temp dir");
    let dump_dir = temp_dir.path().join("game").join("base");
    let exefs = dump_dir.join("exefs");
    let romfs = dump_dir.join("romfs");
    let ui_dir = romfs.join("ui");
    fs::create_dir_all(&exefs).unwrap();
    fs::create_dir_all(&ui_dir).unwrap();

    fs::write(exefs.join("main"), b"MAIN_BINARY_CONTENT").unwrap();
    fs::write(exefs.join("main.npdm"), b"META_DUMMY_HEADER").unwrap();
    fs::write(romfs.join("data.bin"), b"ROMFS_ROOT_DATA").unwrap();
    fs::write(ui_dir.join("icon.png"), b"UI_ICON_PNG").unwrap();

    let output_nup = temp_dir.path().join("nav_test.nup");

    let cfg = PackerConfig {
        folder: dump_dir.parent().unwrap().to_string_lossy().to_string(),
        update: None,
        dlc: None,
        output: output_nup.to_string_lossy().to_string(),
        title_id: "0100000000010000".to_string(),
        title: Some("TOC Nav Game".to_string()),
        publisher: Some("Antigravity".to_string()),
        version: Some("1.0.0".to_string()),
        update_version: None,
        display_version: Some("1.0.0".to_string()),
        nacp: None,
        icon: None,
        zstd: Some(0),
        loc_titles: None,
    };

    let cancelled = Arc::new(AtomicBool::new(false));
    let res = build_nup(&cfg, |_| {}, |_| {}, cancelled);
    assert!(res.is_ok(), "Build failed: {:?}", res.err());

    let mut file = fs::File::open(&output_nup).unwrap();
    let mut sb = vec![0u8; 0x80];
    file.read_exact(&mut sb).unwrap();

    assert_eq!(&sb[0..4], b"NUP\x01");
    let toc_offset = u64::from_le_bytes(sb[8..16].try_into().unwrap());
    let toc_size = u64::from_le_bytes(sb[16..24].try_into().unwrap());
    let expected_toc_sha = &sb[24..56];

    assert_eq!(toc_offset, 0x80);

    let mut toc_bytes = vec![0u8; toc_size as usize];
    file.read_exact(&mut toc_bytes).unwrap();

    let actual_toc_sha = Sha256::digest(&toc_bytes);
    assert_eq!(expected_toc_sha, actual_toc_sha.as_slice(), "TOC SHA-256 integrity check failed!");

    // Parse TOC Header (0x30 bytes)
    let t_magic = u32::from_le_bytes(toc_bytes[0..4].try_into().unwrap());
    let p_count = u32::from_le_bytes(toc_bytes[4..8].try_into().unwrap());
    let d_count = u64::from_le_bytes(toc_bytes[8..16].try_into().unwrap()) as usize;
    let f_count = u64::from_le_bytes(toc_bytes[16..24].try_into().unwrap()) as usize;
    let pool_off = u64::from_le_bytes(toc_bytes[24..32].try_into().unwrap()) as usize;
    let pool_len = u64::from_le_bytes(toc_bytes[32..40].try_into().unwrap()) as usize;

    assert_eq!(t_magic, 0x01434F54);
    assert_eq!(p_count, 1);
    assert_eq!(d_count, 4); // base, exefs, romfs, ui
    assert_eq!(f_count, 4); // main, main.npdm, data.bin, icon.png

    // Parse PKG record (0x40 bytes)
    let pkg_off = 0x30;
    let title_id = u64::from_le_bytes(toc_bytes[pkg_off..pkg_off + 8].try_into().unwrap());
    let root_idx = u32::from_le_bytes(toc_bytes[pkg_off + 8..pkg_off + 12].try_into().unwrap());
    let exefs_idx = u32::from_le_bytes(toc_bytes[pkg_off + 12..pkg_off + 16].try_into().unwrap());
    let romfs_idx = u32::from_le_bytes(toc_bytes[pkg_off + 16..pkg_off + 20].try_into().unwrap());
    let res1 = u16::from_le_bytes(toc_bytes[pkg_off + 26..pkg_off + 28].try_into().unwrap());
    let res2 = u32::from_le_bytes(toc_bytes[pkg_off + 28..pkg_off + 32].try_into().unwrap());

    assert_eq!(title_id, 0x0100000000010000);
    assert_eq!(root_idx, 0);
    assert_eq!(exefs_idx, 1);
    assert_eq!(romfs_idx, 2);
    assert_eq!(res1, 0);
    assert_eq!(res2, 0);

    // Parse DIR records (0x18 bytes each)
    let dir_start = 0x30 + (p_count as usize) * 0x40;
    assert_eq!(dir_start, 0x70, "DIR table must start at offset 0x70 for 1 package");

    let mut dirs: Vec<(usize, usize, usize, usize)> = Vec::new();
    for i in 0..d_count {
        let off = dir_start + i * 0x18;
        let no = u32::from_le_bytes(toc_bytes[off..off + 4].try_into().unwrap()) as usize;
        let nl = u32::from_le_bytes(toc_bytes[off + 4..off + 8].try_into().unwrap()) as usize;
        let first = u32::from_le_bytes(toc_bytes[off + 8..off + 12].try_into().unwrap()) as usize;
        let count = u32::from_le_bytes(toc_bytes[off + 12..off + 16].try_into().unwrap()) as usize;
        dirs.push((no, nl, first, count));
    }

    // Parse FILE records (0x50 bytes each)
    let file_start = dir_start + d_count * 0x18;
    let mut files: Vec<(usize, usize, u64, u64)> = Vec::new();
    for i in 0..f_count {
        let off = file_start + i * 0x50;
        let no = u32::from_le_bytes(toc_bytes[off..off + 4].try_into().unwrap()) as usize;
        let nl = u32::from_le_bytes(toc_bytes[off + 4..off + 8].try_into().unwrap()) as usize;
        let size = u64::from_le_bytes(toc_bytes[off + 8..off + 16].try_into().unwrap());
        let eoff = u64::from_le_bytes(toc_bytes[off + 16..off + 24].try_into().unwrap());
        files.push((no, nl, size, eoff));
    }

    // Children array
    let children_start = file_start + f_count * 0x50;
    let total_children: usize = dirs.iter().map(|d| d.3).sum();
    let children_end = children_start + total_children * 4;

    assert_eq!(children_end, pool_off, "Children table must end exactly at pool_off!");

    let mut children = Vec::new();
    for i in 0..total_children {
        let off = children_start + i * 4;
        children.push(u32::from_le_bytes(toc_bytes[off..off + 4].try_into().unwrap()) as usize);
    }

    let string_pool = &toc_bytes[pool_off..pool_off + pool_len];
    let get_str = |no: usize, nl: usize| -> String {
        String::from_utf8_lossy(&string_pool[no..no + nl]).to_string()
    };

    // Verify Directory Names
    assert_eq!(get_str(dirs[0].0, dirs[0].1), "base");
    assert_eq!(get_str(dirs[1].0, dirs[1].1), "exefs");
    assert_eq!(get_str(dirs[2].0, dirs[2].1), "romfs");
    assert_eq!(get_str(dirs[3].0, dirs[3].1), "ui");

    // Verify Navigation: root (Dir 0) must contain exefs (Dir 1) and romfs (Dir 2)
    let (root_no, root_nl, r_first, r_count) = dirs[0];
    let root_children = &children[r_first..r_first + r_count];
    assert!(root_children.contains(&1), "Root dir must contain child Dir 1 (exefs)");
    assert!(root_children.contains(&2), "Root dir must contain child Dir 2 (romfs)");
    assert!(!root_children.contains(&0), "Root dir must NEVER contain itself as child (circular reference bug)");

    // Dir 1 (exefs) must contain file entries main and main.npdm
    let (_, _, e_first, e_count) = dirs[1];
    let exefs_children = &children[e_first..e_first + e_count];
    let exefs_file_indices: Vec<usize> = exefs_children.iter().filter(|&&c| c >= d_count).map(|&c| c - d_count).collect();
    let exefs_file_names: Vec<String> = exefs_file_indices.iter().map(|&fi| get_str(files[fi].0, files[fi].1)).collect();
    assert!(exefs_file_names.contains(&"main".to_string()), "exefs must contain main");
    assert!(exefs_file_names.contains(&"main.npdm".to_string()), "exefs must contain main.npdm");

    // Dir 2 (romfs) must contain child Dir 3 (ui) and file data.bin
    let (_, _, rom_first, rom_count) = dirs[2];
    let romfs_children = &children[rom_first..rom_first + rom_count];
    assert!(romfs_children.contains(&3), "romfs must contain child Dir 3 (ui)");

    // Dir 3 (ui) must contain icon.png
    let (_, _, ui_first, ui_count) = dirs[3];
    let ui_children = &children[ui_first..ui_first + ui_count];
    let ui_file_indices: Vec<usize> = ui_children.iter().filter(|&&c| c >= d_count).map(|&c| c - d_count).collect();
    let ui_file_names: Vec<String> = ui_file_indices.iter().map(|&fi| get_str(files[fi].0, files[fi].1)).collect();
    assert!(ui_file_names.contains(&"icon.png".to_string()), "ui must contain icon.png");
}
