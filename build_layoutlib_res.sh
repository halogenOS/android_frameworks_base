#!/bin/bash
# SPDX-FileCopyrightText: The halogenOS Project
# SPDX-License-Identifier: Apache-2.0
#
# Builds res.zip of layoutlib: the resource files below data/, plus each
# 9-patch image of data/res compiled by aapt2 as <name>.compiled.9.png.
#
# Usage: build_layoutlib_res.sh <aapt2> <soong_zip> <file list> <work dir> <output zip>
# The file list holds one "path in the archive,source path" line per file.
set -euo pipefail
shopt -s nullglob

readonly aapt2=$1 soong_zip=$2 file_list=$3 work=$4 out=$5

sed 's|^\([^,]*\),\(.*\)$|-e \1 -f \2|' "$file_list" > "$work/sources.rsp"
"$soong_zip" -o "$work/sources.zip" "@$work/sources.rsp"
unzip -q -d "$work" "$work/sources.zip"

mkdir "$work/compiled"
"$aapt2" compile "$work"/data/res/*/*.9.png -o "$work/compiled"
cat > "$work/AndroidManifest.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.google.android.layoutlib" />
EOF
"$aapt2" link -R "$work"/compiled/* -o "$work/compiled.apk" --manifest "$work/AndroidManifest.xml"
unzip -q -d "$work/compiled_apk" "$work/compiled.apk"

# Puts each compiled image beside its source image. aapt2 adds -v4 to the
# folder names.
(
    cd "$work/compiled_apk/res"
    for d in *-v4*; do mv "$d" "${d/-v4/}"; done
    for f in */*.9.png; do mv "$f" "${f/.9.png/.compiled.9.png}"; done
)
cp -r "$work/compiled_apk/res" "$work/data"
"$soong_zip" -C "$work/data" -D "$work/data" -o "$out"
