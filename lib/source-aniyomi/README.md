# Aniyomi source adapter

Maps anime catalogs, episode updates, hosters and stream variants to neutral source/video contracts. Shared ABI and installation live in extension-compat/source-runtime. Per-request filters and per-anime update locks isolate concurrent work.

Persists the anime fetch type and season number alongside the extension memo; episode-only items do not probe the season API. Previously saved raw memo remains readable. Resolves legacy page URLs and lazy videos, preserves stream headers, subtitle/audio tracks and preferred quality, and keeps usable variants when another hoster or variant fails. Cancellation still propagates.

The exact Anikoto `demuxer-lavf-o=force_mpegts=1` / `force_mpegts=1` demuxer hints are accepted for native Media3 handling. Unsupported local-server and other MPV/FFmpeg-only requests fail explicitly instead of forwarding a dead URL to NG. Missing extractors in an extension (such as the inspected VidPlay path) remain extension limitations. No arbitrary extension APK/site or device playback compatibility is asserted by fixture tests.

See [the compatibility investigation](../../docs/ANIYOMI_COMPATIBILITY_FIXES.md) for the inspected extension versions, regression coverage and validation limits.
