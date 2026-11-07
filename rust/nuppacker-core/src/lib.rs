pub mod spec;
pub mod tree;
pub mod discovery;
pub mod packer;

#[cfg(test)]
mod tests;

use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use jni::objects::{JClass, JObject, JString, JValue};
use jni::sys::{jint, jstring};
use jni::JNIEnv;

use crate::discovery::{analyze_folder, discover, guess_title_id, PackerConfig};
use crate::packer::build_nup;

// Active cancellation flag bridge between JNI cancelBuild and the active build thread
static ACTIVE_CANCEL: Mutex<Option<Arc<AtomicBool>>> = Mutex::new(None);

#[no_mangle]
pub extern "system" fn Java_com_nepo_nuppacker_RustBridge_cancelBuild(
    _env: JNIEnv,
    _class: JClass,
) {
    if let Ok(guard) = ACTIVE_CANCEL.lock() {
        if let Some(ref flag) = *guard {
            flag.store(true, Ordering::SeqCst);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_nepo_nuppacker_RustBridge_autoDetectTitleId(
    mut env: JNIEnv,
    _class: JClass,
    folder_path: JString,
) -> jstring {
    let folder_str: String = match env.get_string(&folder_path) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };
    let tid = guess_title_id(Path::new(folder_str.trim()));
    env.new_string(tid).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_nepo_nuppacker_RustBridge_discoverSummary(
    mut env: JNIEnv,
    _class: JClass,
    folder_path: JString,
) -> jstring {
    let folder_str: String = match env.get_string(&folder_path) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("[]").unwrap().into_raw(),
    };
    let specs = discover(Path::new(folder_str.trim()));
    let items: Vec<serde_json::Value> = specs
        .into_iter()
        .map(|(kind, ptid, ver, path)| {
            serde_json::json!({
                "kind": if kind == 0 { "base" } else if kind == 1 { "update" } else { "dlc" },
                "title_id": ptid.map(|t| format!("{:016X}", t)).unwrap_or_default(),
                "version": ver.unwrap_or(0),
                "path": path.to_string_lossy().to_string(),
            })
        })
        .collect();

    let json_str = serde_json::to_string(&items).unwrap_or_else(|_| "[]".to_string());
    env.new_string(json_str).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_nepo_nuppacker_RustBridge_analyzeFolder(
    mut env: JNIEnv,
    _class: JClass,
    folder_path: JString,
) -> jstring {
    let folder_str: String = match env.get_string(&folder_path) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("{}").unwrap().into_raw(),
    };
    let path = Path::new(folder_str.trim());
    let analysis = analyze_folder(path);
    let json_str = serde_json::to_string(&analysis).unwrap_or_else(|_| "{}".to_string());
    env.new_string(json_str).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_nepo_nuppacker_RustBridge_startBuild(
    mut env: JNIEnv,
    _class: JClass,
    json_config: JString,
    callback: JObject,
) -> jint {
    let cancel_flag = Arc::new(AtomicBool::new(false));
    if let Ok(mut guard) = ACTIVE_CANCEL.lock() {
        *guard = Some(Arc::clone(&cancel_flag));
    }

    let config_str: String = match env.get_string(&json_config) {
        Ok(s) => s.into(),
        Err(e) => {
            notify_error(&mut env, &callback, &format!("Failed reading JSON config: {}", e));
            return -1;
        }
    };

    let cfg: PackerConfig = match serde_json::from_str(&config_str) {
        Ok(c) => c,
        Err(e) => {
            notify_error(&mut env, &callback, &format!("Failed parsing JSON config: {}", e));
            return -2;
        }
    };

    let jvm = match env.get_java_vm() {
        Ok(vm) => Arc::new(vm),
        Err(e) => {
            notify_error(&mut env, &callback, &format!("Failed acquiring Java VM: {}", e));
            return -3;
        }
    };

    let callback_global = match env.new_global_ref(&callback) {
        Ok(r) => r,
        Err(e) => {
            notify_error(&mut env, &callback, &format!("Failed creating global callback ref: {}", e));
            return -4;
        }
    };

    let log_vm = Arc::clone(&jvm);
    let log_cb = callback_global.clone();
    let log_fn = move |msg: &str| {
        if let Ok(mut env) = log_vm.attach_current_thread() {
            if let Ok(jmsg) = env.new_string(msg) {
                let _ = env.call_method(
                    &log_cb,
                    "onLog",
                    "(Ljava/lang/String;)V",
                    &[JValue::Object(&jmsg)],
                );
            }
        }
    };

    let prog_vm = Arc::clone(&jvm);
    let prog_cb = callback_global.clone();
    let prog_fn = move |ratio: f32| {
        if let Ok(mut env) = prog_vm.attach_current_thread() {
            let _ = env.call_method(
                &prog_cb,
                "onProgress",
                "(F)V",
                &[JValue::Float(ratio)],
            );
        }
    };

    let cancel_check = cancel_flag.clone();
    let result = std::thread::spawn(move || {
        build_nup(&cfg, log_fn, prog_fn, cancel_check)
    }).join();

    if let Ok(mut guard) = ACTIVE_CANCEL.lock() {
        *guard = None;
    }

    match result {
        Ok(Ok(out_path)) => {
            if let Ok(mut env) = jvm.attach_current_thread() {
                if let Ok(jpath) = env.new_string(&out_path) {
                    let _ = env.call_method(
                        &callback_global,
                        "onSuccess",
                        "(Ljava/lang/String;)V",
                        &[JValue::Object(&jpath)],
                    );
                }
            }
            0
        }
        Ok(Err(err)) => {
            if err.contains("Cancelled by user") {
                if let Ok(mut env) = jvm.attach_current_thread() {
                    let _ = env.call_method(&callback_global, "onCancelled", "()V", &[]);
                }
                1
            } else {
                if let Ok(mut env) = jvm.attach_current_thread() {
                    notify_error(&mut env, &callback_global, &err);
                }
                -5
            }
        }
        Err(_) => {
            if let Ok(mut env) = jvm.attach_current_thread() {
                notify_error(&mut env, &callback_global, "Rust thread panicked during build.");
            }
            -6
        }
    }
}

fn notify_error(env: &mut JNIEnv, callback: &JObject, msg: &str) {
    if let Ok(jmsg) = env.new_string(msg) {
        let _ = env.call_method(
            callback,
            "onError",
            "(Ljava/lang/String;)V",
            &[JValue::Object(&jmsg)],
        );
    }
}
