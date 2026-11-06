/// NUP format binary specification definitions.
/// Conforms to NUP v1-draft and PastaLaPate/NUPPacker reference layout.

pub const NO_DIR: u32 = 0xFFFFFFFF;
pub const CHUNK_SIZE: usize = 4 * 1024 * 1024; // 4MB streaming buffer

pub const SUPERBLOCK_MAGIC: &[u8; 4] = b"NUP\x01";
pub const SUPERBLOCK_SIZE: usize = 0x80;
pub const TOC_MAGIC: u32 = 0x01434F54; // "TOC\x01"

pub const PKG_RECORD_SIZE: usize = 0x40; // 64 bytes
pub const DIR_RECORD_SIZE: usize = 0x18; // 24 bytes
pub const FILE_RECORD_SIZE: usize = 0x50; // 80 bytes

pub const TLV_NPDM: u16 = 0x0001;
pub const TLV_NACP: u16 = 0x0002;
pub const TLV_ICON: u16 = 0x0003;
pub const TLV_DISPLAY_VERSION: u16 = 0x0004;
pub const TLV_TITLE: u16 = 0x0005;
pub const TLV_PUBLISHER: u16 = 0x0006;
pub const TLV_MAIN_SHA256: u16 = 0x0007;
pub const TLV_FOLDER_NAME: u16 = 0x0008;
pub const TLV_LOCALIZED_TITLES: u16 = 0x0009;

#[inline]
pub fn align16(n: u64) -> u64 {
    (n + 15) & !15
}

/// Helper to serialize a TLV (Type-Length-Value) metadata entry.
/// Layout: uint16 type, uint16 reserved (0), uint32 payload_len, payload
pub fn make_tlv(t: u16, payload: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(8 + payload.len());
    out.extend_from_slice(&t.to_le_bytes());
    out.extend_from_slice(&0u16.to_le_bytes());
    out.extend_from_slice(&(payload.len() as u32).to_le_bytes());
    out.extend_from_slice(payload);
    out
}

/// Helper to serialize localized titles block.
/// Layout: [len(lang)][lang_bytes][uint32 title_len][title_bytes]
pub fn make_loc_block(items: &[(String, Vec<u8>)]) -> Vec<u8> {
    let mut seen = std::collections::HashSet::new();
    let mut out = Vec::new();
    for (lang, data) in items {
        let l = lang.trim().to_lowercase();
        if l.is_empty() || !seen.insert(l.clone()) {
            continue;
        }
        let lb = l.as_bytes();
        out.push(lb.len() as u8);
        out.extend_from_slice(lb);
        out.extend_from_slice(&(data.len() as u32).to_le_bytes());
        out.extend_from_slice(data);
    }
    out
}
