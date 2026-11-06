use std::fs;
use std::path::{Path, PathBuf};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

use crate::spec::*;
use crate::tree::{scan_dir, Dir};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PackerConfig {
    pub folder: String,
    pub update: Option<String>,
    pub dlc: Option<String>,
    pub output: String,
    pub title_id: String,
    pub title: Option<String>,
    pub publisher: Option<String>,
    pub version: Option<String>,
    pub update_version: Option<String>,
    pub display_version: Option<String>,
    pub nacp: Option<String>,
    pub icon: Option<String>,
    pub zstd: Option<i32>,
    pub loc_titles: Option<Vec<(String, String)>>,
}

#[derive(Debug, Clone)]
pub struct Pkg {
    pub path: PathBuf,
    pub kind: u8, // 0 = base, 1 = update, 2 = dlc
    pub title_id: u64,
    pub version: u32,
    pub flags: u8,
    pub root: Dir,
    pub exefs_idx: u32,
    pub romfs_idx: u32,
    pub meta: Vec<u8>,
    pub meta_off: u64,
}

pub fn is_pkg_dir(p: &Path) -> bool {
    p.is_dir() && (p.join("exefs").is_dir() || p.join("romfs").is_dir() || p.join("data").is_dir())
}

pub fn parse_ver(s: &str) -> u32 {
    let s = s.trim();
    if s.to_lowercase().starts_with("0x") {
        if let Ok(val) = u32::from_str_radix(&s[2..], 16) {
            return val;
        }
    }
    if !s.contains('.') {
        if let Ok(val) = s.parse::<u32>() {
            return val;
        }
    }
    let parts: Vec<u32> = s.split('.')
        .map(|p| p.trim().parse::<u32>().unwrap_or(0))
        .collect();
    let v0 = parts.get(0).copied().unwrap_or(0) & 0xFFFF;
    let v1 = parts.get(1).copied().unwrap_or(0) & 0xFF;
    let v2 = parts.get(2).copied().unwrap_or(0) & 0xFF;
    (v0 << 16) | (v1 << 8) | v2
}

pub fn ver_str(v: u32) -> String {
    format!("{}.{}.{}", v >> 16, (v >> 8) & 0xFF, v & 0xFF)
}

fn parse_version_name(name: &str) -> Option<u32> {
    let n = name.to_lowercase();
    let n = n.trim_start_matches('v');
    if n.starts_with("0x") {
        u32::from_str_radix(&n[2..], 16).ok()
    } else {
        n.parse::<u32>().ok()
    }
}

pub fn version_dirs(root: &Path) -> Vec<(Option<u32>, PathBuf)> {
    if is_pkg_dir(root) {
        return vec![(None, root.to_path_buf())];
    }
    let mut out = Vec::new();
    if let Ok(entries) = fs::read_dir(root) {
        for entry in entries.filter_map(|e| e.ok()) {
            let path = entry.path();
            if let Some(file_name) = path.file_name().and_then(|f| f.to_str()) {
                if let Some(v) = parse_version_name(file_name) {
                    if is_pkg_dir(&path) {
                        out.push((Some(v), path));
                    }
                }
            }
        }
    }
    out.sort_by_key(|(v, _)| *v);
    out
}

fn is_hex16(s: &str) -> bool {
    s.len() == 16 && s.chars().all(|c| c.is_ascii_hexdigit())
}

pub fn discover_dlc(dlc_path: &Path) -> Vec<(u8, u64, Option<u32>, PathBuf)> {
    let name = dlc_path.file_name().and_then(|f| f.to_str()).unwrap_or("");
    if is_hex16(name) {
        if let Ok(tid) = u64::from_str_radix(name, 16) {
            return version_dirs(dlc_path)
                .into_iter()
                .map(|(v, p)| (2, tid, v, p))
                .collect();
        }
    }

    let mut out = Vec::new();
    if let Ok(entries) = fs::read_dir(dlc_path) {
        for entry in entries.filter_map(|e| e.ok()) {
            let path = entry.path();
            if path.is_dir() {
                if let Some(file_name) = path.file_name().and_then(|f| f.to_str()) {
                    if is_hex16(file_name) {
                        if let Ok(tid) = u64::from_str_radix(file_name, 16) {
                            for (v, p) in version_dirs(&path) {
                                out.push((2, tid, v, p));
                            }
                        }
                    }
                }
            }
        }
    }
    out
}

pub fn discover(folder: &Path) -> Vec<(u8, Option<u64>, Option<u32>, PathBuf)> {
    if is_pkg_dir(folder) {
        return vec![(0, None, None, folder.to_path_buf())];
    }
    let mut specs = Vec::new();
    if is_pkg_dir(&folder.join("base")) {
        specs.push((0, None, None, folder.join("base")));
    }
    if folder.join("updates").is_dir() {
        for (v, p) in version_dirs(&folder.join("updates")) {
            specs.push((1, None, v, p));
        }
    }
    for n in &["dlc", "addon"] {
        if folder.join(n).is_dir() {
            for (kind, tid, v, p) in discover_dlc(&folder.join(n)) {
                specs.push((kind, Some(tid), v, p));
            }
        }
    }
    if specs.is_empty() {
        let v_dirs = version_dirs(folder);
        if !v_dirs.is_empty() {
            for (v, p) in v_dirs {
                specs.push((1, None, v, p));
            }
        } else {
            for (kind, tid, v, p) in discover_dlc(folder) {
                specs.push((kind, Some(tid), v, p));
            }
        }
    }
    specs
}

pub fn guess_title_id(folder: &Path) -> String {
    let mut npdm = folder.join("exefs").join("main.npdm");
    if !npdm.is_file() {
        npdm = folder.join("main.npdm");
    }
    if !npdm.is_file() {
        return String::new();
    }
    let d = match fs::read(&npdm) {
        Ok(bytes) => bytes,
        Err(_) => return String::new(),
    };
    if d.len() < 0x80 || &d[0..4] != b"META" {
        return String::new();
    }
    let aci0_offset = u32::from_le_bytes(d[0x70..0x74].try_into().unwrap_or([0; 4])) as usize;
    if aci0_offset + 0x18 > d.len() || &d[aci0_offset..aci0_offset + 4] != b"ACI0" {
        return String::new();
    }
    let tid = u64::from_le_bytes(d[aci0_offset + 0x10..aci0_offset + 0x18].try_into().unwrap_or([0; 8]));
    format!("{:016X}", tid)
}

fn sha256_file(path: &Path) -> Result<[u8; 32], std::io::Error> {
    use std::io::Read;
    let mut f = fs::File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buf = vec![0u8; 1024 * 1024];
    loop {
        let n = f.read(&mut buf)?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hasher.finalize().into())
}

pub fn app_meta(cfg: &PackerConfig, base_dir: Option<&Path>) -> Vec<u8> {
    let mut m = Vec::new();

    if let Some(nacp_str) = &cfg.nacp {
        let nacp_path = Path::new(nacp_str.trim());
        if nacp_path.is_file() {
            if let Ok(bytes) = fs::read(nacp_path) {
                m.extend_from_slice(&make_tlv(TLV_NACP, &bytes));
            }
        }
    }

    if let Some(icon_str) = &cfg.icon {
        let icon_path = Path::new(icon_str.trim());
        if icon_path.is_file() {
            if let Ok(bytes) = fs::read(icon_path) {
                m.extend_from_slice(&make_tlv(TLV_ICON, &bytes));
            }
        }
    }

    if let Some(title) = &cfg.title {
        if !title.is_empty() {
            m.extend_from_slice(&make_tlv(TLV_TITLE, title.as_bytes()));
        }
    }

    if let Some(publisher) = &cfg.publisher {
        if !publisher.is_empty() {
            m.extend_from_slice(&make_tlv(TLV_PUBLISHER, publisher.as_bytes()));
        }
    }

    if let Some(dir) = base_dir {
        let main_path = dir.join("exefs").join("main");
        if main_path.is_file() {
            if let Ok(sha) = sha256_file(&main_path) {
                m.extend_from_slice(&make_tlv(TLV_MAIN_SHA256, &sha));
            }
        }
    }

    let folder_name = Path::new(&cfg.folder)
        .file_name()
        .and_then(|f| f.to_str())
        .unwrap_or("game");
    m.extend_from_slice(&make_tlv(TLV_FOLDER_NAME, folder_name.as_bytes()));

    if let Some(locs) = &cfg.loc_titles {
        let mut items = Vec::new();
        for (lang, name) in locs {
            if !lang.trim().is_empty() && !name.trim().is_empty() {
                items.push((lang.trim().to_string(), name.trim().as_bytes().to_vec()));
            }
        }
        if !items.is_empty() {
            let loc_payload = make_loc_block(&items);
            m.extend_from_slice(&make_tlv(TLV_LOCALIZED_TITLES, &loc_payload));
        }
    }

    m
}

pub fn plan_packages<F>(cfg: &PackerConfig, log: &F) -> Result<Vec<Pkg>, String>
where
    F: Fn(&str),
{
    let raw_tid = cfg.title_id.trim().trim_start_matches("0x").trim_start_matches("0X");
    if raw_tid.is_empty() {
        return Err("Title ID is required.".into());
    }
    let tid = u64::from_str_radix(raw_tid, 16)
        .map_err(|_| format!("Invalid Title ID '{}': must be a 16-hex-digit number.", raw_tid))?;

    let folder_path = Path::new(cfg.folder.trim());
    let mut specs = discover(folder_path);

    if let Some(up_str) = &cfg.update {
        let p = Path::new(up_str.trim());
        if p.is_dir() {
            for (v, sub_p) in version_dirs(p) {
                specs.push((1, None, v, sub_p));
            }
        }
    }

    if let Some(dlc_str) = &cfg.dlc {
        let p = Path::new(dlc_str.trim());
        if p.is_dir() {
            for (kind, dtid, v, sub_p) in discover_dlc(p) {
                specs.push((kind, Some(dtid), v, sub_p));
            }
        }
    }

    if specs.is_empty() {
        return Err("No packages found: expected base/, updates/<version>/, dlc/<titleid>/ (or a folder containing exefs/ or romfs/).".into());
    }

    let bases: Vec<_> = specs.iter().filter(|s| s.0 == 0).collect();
    if bases.len() > 1 {
        return Err("More than one base package detected.".into());
    }
    let has_base = !bases.is_empty();

    let mut norm = Vec::new();
    for (kind, ptid, ver, path) in specs {
        let (resolved_tid, resolved_ver) = if kind == 0 {
            (tid, parse_ver(cfg.version.as_deref().unwrap_or("1.0.0")))
        } else if kind == 1 {
            (
                tid | 0x800,
                ver.unwrap_or_else(|| parse_ver(cfg.update_version.as_deref().unwrap_or("1.0.1"))),
            )
        } else {
            let t = ptid.ok_or_else(|| format!("{}: DLC needs a 16-hex-digit title ID folder name.", path.display()))?;
            (t, ver.unwrap_or(0))
        };
        norm.push((kind, resolved_tid, resolved_ver, path));
    }

    norm.sort_by_key(|s| (s.0, s.2));

    let updates: Vec<_> = norm.iter().filter(|s| s.0 == 1).cloned().collect();
    let best_update = updates.last().cloned();
    let disp_target = best_update.clone().or_else(|| norm.first().cloned());

    let mut pkgs = Vec::new();
    let kind_names = ["base", "update", "dlc"];

    for (kind, ptid, ver, path) in norm {
        let mut meta = Vec::new();
        let is_best_update = best_update.as_ref().map_or(false, |b| b.3 == path);
        if kind == 0 || (!has_base && is_best_update) {
            meta.extend_from_slice(&app_meta(cfg, Some(&path)));
        }
        if let Some(disp_ver) = &cfg.display_version {
            if disp_target.as_ref().map_or(false, |dt| dt.3 == path) {
                meta.extend_from_slice(&make_tlv(TLV_DISPLAY_VERSION, disp_ver.as_bytes()));
            }
        }

        let mut root = Dir::new(path.file_name().and_then(|f| f.to_str()).unwrap_or("pkg"));
        let mut exefs_idx = NO_DIR;
        let mut romfs_idx = NO_DIR;

        let exefs_path = path.join("exefs");
        if exefs_path.is_dir() {
            let exefs_dir = scan_dir(&exefs_path, "exefs").map_err(|e| e.to_string())?;
            root.dirs.push(exefs_dir);
        }

        let romfs_path = path.join("romfs");
        if romfs_path.is_dir() {
            let romfs_dir = scan_dir(&romfs_path, "romfs").map_err(|e| e.to_string())?;
            root.dirs.push(romfs_dir);
        }

        let data_path = path.join("data");
        if data_path.is_dir() {
            let data_dir = scan_dir(&data_path, "data").map_err(|e| e.to_string())?;
            root.dirs.push(data_dir);
        }

        let mut npdm_meta = Vec::new();
        let npdm_file = path.join("exefs").join("main.npdm");
        if npdm_file.is_file() {
            if let Ok(bytes) = fs::read(&npdm_file) {
                npdm_meta.extend_from_slice(&make_tlv(TLV_NPDM, &bytes));
            }
        }
        npdm_meta.extend_from_slice(&meta);

        let flags = if kind == 1 && !has_base { 1 } else { 0 };

        log(&format!(
            "  {:6} {:016X}  v{} ({})  {}",
            kind_names.get(kind as usize).unwrap_or(&"unknown"),
            ptid,
            ver,
            ver_str(ver),
            path.display()
        ));

        pkgs.push(Pkg {
            path,
            kind,
            title_id: ptid,
            version: ver,
            flags,
            root,
            exefs_idx,
            romfs_idx,
            meta: npdm_meta,
            meta_off: 0,
        });
    }

    Ok(pkgs)
}
