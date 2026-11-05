// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! Functions that read and watch the system properties that steer the animation. An empty value
//! counts as unset, since setting a property to the empty string clears it.

use std::fmt::Display;
use std::str::FromStr;
use std::sync::mpsc::SyncSender;
use std::thread;

use rustutils::android::system_properties::{read, read_bool, PropertyWatcher};

use crate::error::Error;

/// The value of the property name as an integer, or None when it is unset.
pub fn int(name: &'static str) -> Result<Option<i32>, Error> {
    text(name)?
        .map(|value| {
            value.trim().parse().map_err(|cause| Error::PropertyNotNumber { name, value, cause })
        })
        .transpose()
}

/// The value of the property name as a boolean, or default when it is unset or not a boolean.
pub fn flag(name: &'static str, default: bool) -> Result<bool, Error> {
    read_bool(name, default).map_err(|cause| Error::PropertyRead { name, cause })
}

/// Watches the property name on a thread of its own and sends each value it reads as T through
/// changes, wrapped by change: the value it has now, then each value it changes to. Values that
/// are unset or that T does not recognize are left out, and the latter are logged. A failure to
/// read or wait is sent as the last message of the thread. The thread ends once the receiver of
/// changes is gone and the property changes again, or with the process: a wait for a property
/// cannot be cancelled. Fails when the thread cannot start.
pub fn watch<T, C>(
    name: &'static str,
    changes: SyncSender<Result<C, Error>>,
    change: fn(T) -> C,
) -> Result<(), Error>
where
    T: FromStr + 'static,
    T::Err: Display,
    C: Send + 'static,
{
    let _ends_with_the_process = thread::Builder::new()
        .spawn(move || {
            if let Err(error) = send_changes(name, &changes, change) {
                // A failed send means that nothing reads the changes anymore.
                let _receiver_gone = changes.send(Err(error));
            }
        })
        .map_err(|cause| Error::PropertyWatchThread { name, cause })?;
    Ok(())
}

/// Sends the values of the property name through changes as `watch` describes, until the receiver
/// is gone or reading or waiting fails.
fn send_changes<T, C>(
    name: &'static str,
    changes: &SyncSender<Result<C, Error>>,
    change: fn(T) -> C,
) -> Result<(), Error>
where
    T: FromStr,
    T::Err: Display,
{
    let mut watcher =
        PropertyWatcher::new(name).map_err(|cause| Error::PropertyRead { name, cause })?;
    loop {
        let value = watcher.read_string().map_err(|cause| Error::PropertyRead { name, cause })?;
        let known =
            value.filter(|value| !value.is_empty()).and_then(|value| parse_known(name, &value));
        if let Some(value) = known {
            if changes.send(Ok(change(value))).is_err() {
                return Ok(());
            }
        }
        watcher.wait(None).map_err(|cause| Error::PropertyWait { name, cause })?;
    }
}

/// The value of the property name, or None when it is unset.
pub fn text(name: &'static str) -> Result<Option<String>, Error> {
    let value = read(name).map_err(|cause| Error::PropertyRead { name, cause })?;
    Ok(value.filter(|value| !value.is_empty()))
}

/// value of the property name read as T, or None, logged, when T does not recognize it.
fn parse_known<T: FromStr>(name: &str, value: &str) -> Option<T>
where
    T::Err: Display,
{
    value
        .parse()
        .inspect_err(|error| log::warn!("{name} is {value:?}, which is unknown here: {error}"))
        .ok()
}
