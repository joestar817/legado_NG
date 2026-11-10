"""Build portable ordinary themes without adding their assets to the APK (Python 3 stdlib)."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

REPO = Path(__file__).resolve().parents[1]
SOURCE = REPO / "themes" / "content"


def package_themes(output: Path) -> list[dict]:
    output.mkdir(parents=True, exist_ok=True)
    catalog = []
    for folder in sorted(SOURCE.iterdir()):
        if not folder.is_dir():
            continue
        manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
        theme = manifest["theme"]
        assert manifest["format"] == "reading-ng-theme" and manifest["version"] == 1
        assert theme["schemaVersion"] == 1 and not theme["id"].startswith("builtin.")
        assert not theme.get("effect") and not theme.get("packageRootPath")
        for key in ("lightBackgroundAsset", "darkBackgroundAsset", "lightDrawerImageAsset", "darkDrawerImageAsset"):
            if path := manifest.get(key):
                assert "\\" not in path and ":" not in path and not Path(path).is_absolute()
                asset = (folder / path).resolve()
                assert asset.is_relative_to(folder.resolve()) and asset.is_file()
        files = sorted(p for p in folder.rglob("*") if p.is_file())
        assert all(p.suffix.lower() not in (".dex", ".so") for p in files)
        version = theme["publication"]["versionName"]
        archive = output / f"{folder.name}-{version}.zip"
        with ZipFile(archive, "w", compression=ZIP_DEFLATED) as zipped:
            for source in files:
                entry = ZipInfo(source.relative_to(folder).as_posix(), date_time=(2026, 1, 1, 0, 0, 0))
                entry.compress_type = ZIP_DEFLATED
                entry.external_attr = 0o100644 << 16
                zipped.writestr(entry, source.read_bytes())
        with ZipFile(archive) as zipped:
            assert zipped.testzip() is None
            for source in files:
                assert zipped.read(source.relative_to(folder).as_posix()) == source.read_bytes()
        catalog.append({"id": theme["id"], "name": theme["name"], "version": version,
                        "file": archive.name, "size": archive.stat().st_size,
                        "sha256": hashlib.sha256(archive.read_bytes()).hexdigest()})
    (output / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return catalog


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=REPO / "build" / "outputs" / "content-themes")
    args = parser.parse_args()
    print(json.dumps(package_themes(args.output.resolve()), ensure_ascii=False, indent=2))
