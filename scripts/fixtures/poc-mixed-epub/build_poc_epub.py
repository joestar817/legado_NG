#!/usr/bin/env python3
"""Phase 3A PoC fixture: minimal EPUB with mixed Latin/CJK/kana paragraphs."""
import zipfile
from pathlib import Path

OUT = Path(__file__).parent / "poc-mixed-epub.epub"

CH1 = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><meta charset="utf-8"/><title>PoC Mixed</title></head>
<body>
  <p id="p1">Hello World 你好世界 こんにちは</p>
  <p id="p2">A 你好 B 世界 C こんにちは D</p>
  <p id="p3">中文，with ASCII、「引号」与 123 —— dash — test</p>
  <p id="p4" style="font-family: serif;">Serif author font: 你好世界 Hello World</p>
</body>
</html>
"""

CONTENT_OPF = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">urn:uuid:11111111-2222-3333-4444-555555555555</dc:identifier>
    <dc:title>PoC Mixed Script</dc:title>
    <dc:language>en</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine><itemref idref="ch1"/></spine>
</package>
"""

CONTAINER = """<?xml version="1.0" encoding="utf-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("mimetype", "application/epub+zip", compress_type=zipfile.ZIP_STORED)
    z.writestr("META-INF/container.xml", CONTAINER)
    z.writestr("content.opf", CONTENT_OPF)
    z.writestr("ch1.xhtml", CH1)

print(f"wrote {OUT} ({OUT.stat().st_size} bytes)")
