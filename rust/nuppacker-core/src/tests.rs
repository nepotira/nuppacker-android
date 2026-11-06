use std::fs;
use std::io::Read;
use std::sync::atomic::AtomicBool;
use std::sync::Arc;
use tempfile::TempDir;

use crate::discovery::{parse_ver, ver_str, PackerConfig};
use crate::packer::build_nup;
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
