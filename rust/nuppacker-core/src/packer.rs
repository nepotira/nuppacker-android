use std::collections::HashMap;
use std::fs::{self, File, OpenOptions};
use std::io::{Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use sha2::{Digest, Sha256};

use crate::discovery::{plan_packages, PackerConfig, Pkg};
use crate::spec::*;
use crate::tree::{collect, Dir, FileEntry};

pub fn build_nup<L, P>(
    cfg: &PackerConfig,
    log: L,
    progress: P,
    cancelled: Arc<AtomicBool>,
) -> Result<String, String>
where
    L: Fn(&str),
    P: Fn(f32),
{
    let mut pkgs = plan_packages(cfg, &log)?;
    log(&format!(
        "Packages: {}",
        pkgs.iter()
            .map(|p| if p.kind == 0 { "base" } else if p.kind == 1 { "update" } else { "dlc" })
            .collect::<Vec<_>>()
            .join(", ")
    ));

    let mut dirs: Vec<Dir> = Vec::new();
    let mut files: Vec<FileEntry> = Vec::new();

    for p in &mut pkgs {
        collect(&mut p.root, &mut dirs, &mut files);
        for sub in &p.root.dirs {
            if sub.name == "exefs" {
                p.exefs_idx = sub.idx as u32;
            } else if sub.name == "romfs" {
                p.romfs_idx = sub.idx as u32;
            }
        }
    }

    let d_count = dirs.len();
    let f_count = files.len();
    let p_count = pkgs.len();

    let mut total_bytes: u64 = 0;
    for f in &files {
        if let Ok(meta) = fs::metadata(&f.path) {
            total_bytes += meta.len();
        }
    }
    if total_bytes == 0 {
        total_bytes = 1;
    }

    log(&format!(
        "{} dirs, {} files, {:.2} GB uncompressed",
        d_count,
        f_count,
        (total_bytes as f64) / 1e9
    ));

    let mut children: Vec<u32> = Vec::new();
    for d in &mut dirs {
        d.first = children.len();
        let mut nodes: Vec<(Vec<u8>, u32)> = Vec::new();
        for sub in &d.dirs {
            nodes.push((sub.name.as_bytes().to_vec(), sub.idx as u32));
        }
        for f in &d.files {
            nodes.push((f.name.as_bytes().to_vec(), (d_count + f.idx) as u32));
        }
        nodes.sort_by(|a, b| a.0.cmp(&b.0));
        for (_, idx) in &nodes {
            children.push(*idx);
        }
        d.count = nodes.len();
    }

    let mut pool: Vec<u8> = Vec::new();
    let mut pmap: HashMap<String, (u32, u32)> = HashMap::new();

    let mut intern = |s: &str| {
        if !pmap.contains_key(s) {
            let b = s.as_bytes();
            let off = pool.len() as u32;
            let len = b.len() as u32;
            pmap.insert(s.to_string(), (off, len));
            pool.extend_from_slice(b);
        }
    };

    for d in &dirs {
        intern(&d.name);
    }
    for f in &files {
        intern(&f.name);
    }

    let pool_off = (0x30 + p_count * PKG_RECORD_SIZE + d_count * DIR_RECORD_SIZE + f_count * FILE_RECORD_SIZE + 4 * children.len()) as u64;
    let toc_size = align16(pool_off + pool.len() as u64);

    let mut pos = align16(SUPERBLOCK_SIZE as u64 + toc_size);
    for p in &mut pkgs {
        if !p.meta.is_empty() {
            p.meta_off = pos;
            pos = align16(pos + p.meta.len() as u64);
        }
    }
    let blob_off = pos;

    let out_target = Path::new(cfg.output.trim());
    if let Some(parent) = out_target.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("Failed to create output dir: {}", e))?;
    }
    let part_path = PathBuf::from(format!("{}.part", cfg.output.trim()));

    let zstd_level = cfg.zstd.unwrap_or(3);

    let build_res = (|| -> Result<u64, String> {
        let mut out = OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .truncate(true)
            .open(&part_path)
            .map_err(|e| format!("Failed to create output part file: {}", e))?;

        out.seek(SeekFrom::Start(blob_off))
            .map_err(|e| format!("Failed to seek blob offset: {}", e))?;

        let mut done: u64 = 0;
        let mut seen: HashMap<([u8; 32], u64), (u64, u64, u8)> = HashMap::new();
        let mut read_buf = vec![0u8; CHUNK_SIZE];

        for f in &mut files {
            if cancelled.load(Ordering::Relaxed) {
                return Err("Cancelled by user".into());
            }

            f.size = fs::metadata(&f.path).map(|m| m.len()).unwrap_or(0);
            if f.size > (128 << 20) {
                log(&format!("Packing {} ({:.2} GB)", f.name, (f.size as f64) / 1e9));
            }
            if f.size == 0 {
                f.sha = Sha256::digest(b"").into();
                continue;
            }

            let before = pos;
            let pad = ((!pos + 1) & 15) as usize % 16;
            if pad > 0 {
                out.write_all(&vec![0u8; pad])
                    .map_err(|e| format!("Failed writing padding: {}", e))?;
                pos += pad as u64;
            }
            let start = pos;
            let mut hasher = Sha256::new();
            let mut stored_raw = true;

            if zstd_level > 0 {
                let compress_res = (|| -> Result<(usize, [u8; 32]), std::io::Error> {
                    let mut fin = File::open(&f.path)?;
                    let mut encoder = zstd::stream::write::Encoder::new(Vec::new(), zstd_level)?;
                    // Enable multithreaded zstd compression if available
                    let _ = encoder.include_magicbytes(true);

                    let mut compressed_len = 0usize;
                    let mut f_hasher = Sha256::new();

                    loop {
                        if cancelled.load(Ordering::Relaxed) {
                            return Err(std::io::Error::new(std::io::ErrorKind::Interrupted, "Cancelled"));
                        }
                        let n = fin.read(&mut read_buf)?;
                        if n == 0 {
                            break;
                        }
                        f_hasher.update(&read_buf[..n]);
                        
                        // Stream into encoder buffer
                        encoder.write_all(&read_buf[..n])?;
                    }

                    let compressed_data = encoder.finish()?;
                    compressed_len = compressed_data.len();

                    if (compressed_len as u64) < f.size {
                        out.write_all(&compressed_data)?;
                        Ok((compressed_len, f_hasher.finalize().into()))
                    } else {
                        // Compressed size is larger or equal; fall back to raw
                        Ok((0, f_hasher.finalize().into()))
                    }
                })();

                match compress_res {
                    Ok((c_len, hash)) => {
                        hasher = Sha256::new(); // hash already captured
                        f.sha = hash;
                        if c_len > 0 {
                            stored_raw = false;
                            f.codec = 1;
                            f.esize = c_len as u64;
                            done += f.size;
                        }
                    }
                    Err(e) if e.kind() == std::io::ErrorKind::Interrupted => {
                        return Err("Cancelled by user".into());
                    }
                    Err(_) => {
                        // Compression fallback to raw
                        out.seek(SeekFrom::Start(start)).map_err(|e| e.to_string())?;
                        stored_raw = true;
                    }
                }
            }

            if stored_raw {
                f.codec = 0;
                let mut fin = File::open(&f.path).map_err(|e| format!("Failed to open {}: {}", f.name, e))?;
                let mut f_hasher = Sha256::new();
                loop {
                    if cancelled.load(Ordering::Relaxed) {
                        return Err("Cancelled by user".into());
                    }
                    let n = fin.read(&mut read_buf).map_err(|e| e.to_string())?;
                    if n == 0 {
                        break;
                    }
                    f_hasher.update(&read_buf[..n]);
                    out.write_all(&read_buf[..n]).map_err(|e| e.to_string())?;
                }
                f.sha = f_hasher.finalize().into();
                f.esize = f.size;
                done += f.size;
            }

            let key = (f.sha, f.size);
            if let Some(&(prev_off, prev_esize, prev_codec)) = seen.get(&key) {
                f.eoff = prev_off;
                f.esize = prev_esize;
                f.codec = prev_codec;
                out.seek(SeekFrom::Start(before)).map_err(|e| e.to_string())?;
                out.set_len(before).map_err(|e| e.to_string())?;
                pos = before;
            } else {
                f.eoff = start;
                seen.insert(key, (start, f.esize, f.codec));
                pos = start + f.esize;
            }

            progress((done as f32) / (total_bytes as f32));
        }

        let file_size = pos;

        log("Building table of contents…");
        let mut toc_bytes = Vec::with_capacity(toc_size as usize);

        // 1. TOC Header (0x30 bytes)
        toc_bytes.extend_from_slice(&TOC_MAGIC.to_le_bytes());
        toc_bytes.extend_from_slice(&(p_count as u32).to_le_bytes());
        toc_bytes.extend_from_slice(&(d_count as u64).to_le_bytes());
        toc_bytes.extend_from_slice(&(f_count as u64).to_le_bytes());
        toc_bytes.extend_from_slice(&pool_off.to_le_bytes());
        toc_bytes.extend_from_slice(&(pool.len() as u64).to_le_bytes());
        toc_bytes.extend_from_slice(&0u64.to_le_bytes()); // reserved

        // 2. PKG Records (0x40 bytes each)
        for p in &pkgs {
            toc_bytes.extend_from_slice(&p.title_id.to_le_bytes());
            toc_bytes.extend_from_slice(&(p.root.idx as u32).to_le_bytes());
            toc_bytes.extend_from_slice(&p.exefs_idx.to_le_bytes());
            toc_bytes.extend_from_slice(&p.romfs_idx.to_le_bytes());
            toc_bytes.extend_from_slice(&p.version.to_le_bytes());
            toc_bytes.push(p.kind);
            toc_bytes.push(p.flags);
            toc_bytes.extend_from_slice(&0u16.to_le_bytes());
            toc_bytes.extend_from_slice(&0u64.to_le_bytes());
            toc_bytes.extend_from_slice(&p.meta_off.to_le_bytes());
            toc_bytes.extend_from_slice(&(p.meta.len() as u32).to_le_bytes());
            toc_bytes.extend_from_slice(&[0u8; 20]);
        }

        // 3. DIR Records (0x18 bytes each)
        for d in &dirs {
            let (no, nl) = pmap[&d.name];
            toc_bytes.extend_from_slice(&no.to_le_bytes());
            toc_bytes.extend_from_slice(&nl.to_le_bytes());
            toc_bytes.extend_from_slice(&(d.first as u32).to_le_bytes());
            toc_bytes.extend_from_slice(&(d.count as u32).to_le_bytes());
            toc_bytes.extend_from_slice(&0u64.to_le_bytes());
        }

        // 4. FILE Records (0x50 bytes each)
        for f in &files {
            let (no, nl) = pmap[&f.name];
            toc_bytes.extend_from_slice(&no.to_le_bytes());
            toc_bytes.extend_from_slice(&nl.to_le_bytes());
            toc_bytes.extend_from_slice(&f.size.to_le_bytes());
            toc_bytes.extend_from_slice(&f.eoff.to_le_bytes());
            toc_bytes.extend_from_slice(&f.esize.to_le_bytes());
            toc_bytes.extend_from_slice(&f.sha);
            toc_bytes.push(f.codec);
            toc_bytes.push(0u8); // flags
            toc_bytes.extend_from_slice(&0u16.to_le_bytes());
            toc_bytes.extend_from_slice(&[0u8; 12]);
        }

        // 5. Children Array (uint32 each)
        for c in &children {
            toc_bytes.extend_from_slice(&c.to_le_bytes());
        }

        // 6. Name string pool
        toc_bytes.extend_from_slice(&pool);

        // Pad TOC to toc_size
        while toc_bytes.len() < toc_size as usize {
            toc_bytes.push(0u8);
        }

        let toc_sha256 = Sha256::digest(&toc_bytes);
        log(&format!("TOC generated ({:.1} MB)", (toc_bytes.len() as f64) / 1e6));

        // UUID hash
        let mut uuid_hasher = Sha256::new();
        for p in &pkgs {
            uuid_hasher.update(&p.title_id.to_le_bytes());
            uuid_hasher.update(&p.version.to_le_bytes());
            uuid_hasher.update(&[p.kind]);
        }
        let uuid_full = uuid_hasher.finalize();
        let uuid_16 = &uuid_full[..16];

        // 7. Superblock (0x80 bytes)
        let mut superblock = Vec::with_capacity(SUPERBLOCK_SIZE);
        superblock.extend_from_slice(SUPERBLOCK_MAGIC);
        superblock.extend_from_slice(&(SUPERBLOCK_SIZE as u16).to_le_bytes());
        superblock.extend_from_slice(&1u16.to_le_bytes()); // header version
        superblock.extend_from_slice(&(SUPERBLOCK_SIZE as u64).to_le_bytes()); // toc_offset
        superblock.extend_from_slice(&toc_size.to_le_bytes());
        superblock.extend_from_slice(&toc_sha256);
        superblock.extend_from_slice(&blob_off.to_le_bytes());
        superblock.extend_from_slice(&file_size.to_le_bytes());
        superblock.extend_from_slice(&(p_count as u32).to_le_bytes());
        superblock.extend_from_slice(&0u32.to_le_bytes()); // reserved
        superblock.extend_from_slice(uuid_16);
        superblock.extend_from_slice(&[0u8; 32]); // reserved

        assert_eq!(superblock.len(), SUPERBLOCK_SIZE);

        out.seek(SeekFrom::Start(0)).map_err(|e| e.to_string())?;
        out.write_all(&superblock).map_err(|e| e.to_string())?;
        out.write_all(&toc_bytes).map_err(|e| e.to_string())?;

        for p in &pkgs {
            if !p.meta.is_empty() {
                out.seek(SeekFrom::Start(p.meta_off)).map_err(|e| e.to_string())?;
                out.write_all(&p.meta).map_err(|e| e.to_string())?;
            }
        }

        out.set_len(file_size).map_err(|e| e.to_string())?;
        out.flush().map_err(|e| e.to_string())?;

        Ok(file_size)
    })();

    match build_res {
        Ok(file_size) => {
            fs::rename(&part_path, out_target)
                .map_err(|e| format!("Failed to move .part to destination .nup: {}", e))?;
            log(&format!(
                "Done: {} ({:.2} GB)",
                out_target.display(),
                (file_size as f64) / 1e9
            ));
            Ok(out_target.to_string_lossy().to_string())
        }
        Err(e) => {
            if part_path.exists() {
                let _ = fs::remove_file(&part_path);
            }
            Err(e)
        }
    }
}
