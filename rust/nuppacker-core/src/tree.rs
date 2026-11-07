use std::fs;
use std::path::{Path, PathBuf};
use unicode_normalization::UnicodeNormalization;

#[derive(Debug, Clone)]
pub struct Dir {
    pub name: String,
    pub dirs: Vec<Dir>,
    pub files: Vec<FileEntry>,
    pub idx: usize,
    pub first: usize,
    pub count: usize,
}

impl Dir {
    pub fn new(name: &str) -> Self {
        Self {
            name: name.to_string(),
            dirs: Vec::new(),
            files: Vec::new(),
            idx: 0,
            first: 0,
            count: 0,
        }
    }
}

#[derive(Debug, Clone)]
pub struct FileEntry {
    pub name: String,
    pub path: PathBuf,
    pub idx: usize,
    pub size: u64,
    pub sha: [u8; 32],
    pub codec: u8,
    pub eoff: u64,
    pub esize: u64,
}

impl FileEntry {
    pub fn new(name: &str, path: PathBuf) -> Self {
        Self {
            name: name.to_string(),
            path,
            idx: 0,
            size: 0,
            sha: [0u8; 32],
            codec: 0,
            eoff: 0,
            esize: 0,
        }
    }
}

/// Recursively scans a filesystem directory into a `Dir` tree.
/// Skips reserved files (`.listing`, `.nup`, `.part`).
/// Normalizes all entry names to NFC per NUP format specification.
pub fn scan_dir(path: &Path, name: &str) -> Result<Dir, std::io::Error> {
    let mut d = Dir::new(name);
    if !path.is_dir() {
        return Ok(d);
    }

    let mut entries: Vec<_> = fs::read_dir(path)?
        .filter_map(|e| e.ok())
        .collect();
    
    // Sort for determinism
    entries.sort_by_key(|e| e.file_name());

    for entry in entries {
        let file_name = entry.file_name();
        let name_str = file_name.to_string_lossy();
        
        // Skip reserved names
        if name_str == ".listing" || name_str.ends_with(".nup") || name_str.ends_with(".part") {
            continue;
        }

        let nfc_name: String = name_str.nfc().collect();
        let entry_path = entry.path();
        let file_type = entry.file_type()?;

        if file_type.is_dir() {
            d.dirs.push(scan_dir(&entry_path, &nfc_name)?);
        } else if file_type.is_file() {
            d.files.push(FileEntry::new(&nfc_name, entry_path));
        }
    }

    Ok(d)
}

/// Helper: assign in-place preorder indices to all Dir and FileEntry nodes
fn assign_indices(
    d: &mut Dir,
    dir_idx: &mut usize,
    file_idx: &mut usize,
) {
    d.idx = *dir_idx;
    *dir_idx += 1;
    for sub in &mut d.dirs {
        assign_indices(sub, dir_idx, file_idx);
    }
    for f in &mut d.files {
        f.idx = *file_idx;
        *file_idx += 1;
    }
}

/// Helper: flatten fully indexed tree into flat vectors
fn gather_flattened(
    d: &Dir,
    dirs: &mut Vec<Dir>,
    files: &mut Vec<FileEntry>,
) {
    dirs.push(d.clone());
    for sub in &d.dirs {
        gather_flattened(sub, dirs, files);
    }
    for f in &d.files {
        files.push(f.clone());
    }
}

/// Flattens the directory tree into indexed vectors of `Dir` and `FileEntry`.
/// In-place two-pass algorithm guarantees that `d.dirs` and `d.files` inside
/// the flattened `dirs` list preserve the true assigned indices of children.
pub fn collect(d: &mut Dir, dirs: &mut Vec<Dir>, files: &mut Vec<FileEntry>) {
    let mut dir_counter = dirs.len();
    let mut file_counter = files.len();
    assign_indices(d, &mut dir_counter, &mut file_counter);
    gather_flattened(d, dirs, files);
}
