This directory carries the cn-font-split WASM runtime used by docker/java/Dockerfile.

Bundling the file keeps one-click server installation from running an external
curl command for the GitHub Release binary, which can be flagged by host
security products as a suspicious supply-chain download.

Current asset:
- File: libffi-wasm32-wasip1.wasm
- Source: https://github.com/KonghaYao/cn-font-split/releases/download/7.6.8/libffi-wasm32-wasip1.wasm
- SHA256: 05a88dcb9a0b0d1e14daf0f429d9af6e2ac8d94d9e574523a76d3e9f440dccc9
- License: Apache-2.0, same as cn-font-split

When updating CN_FONT_SPLIT_VERSION in Dockerfile, replace this asset and update
CN_FONT_SPLIT_WASM_SHA256 at the same time.
