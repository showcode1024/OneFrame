"""Validate a version tag and name the APK built by this GitHub workflow."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil


def validate_tag(root: Path, tag: str) -> str:
    if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", tag):
        raise ValueError("A version tag such as v0.1.2-alpha is required")
    source = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    match = re.search(r'versionName\s*=\s*"([^"]+)"', source)
    if not match or tag != "v" + match.group(1):
        raise ValueError("The version tag must match app versionName")
    if not (root / "docs/releases" / f"{tag}.md").is_file():
        raise ValueError("Commit release notes for this tag before publishing")
    return match.group(1)


def prepare_assets(root: Path, version: str) -> Path:
    release_dir = root / "app/build/outputs/apk/release"
    metadata = json.loads((release_dir / "output-metadata.json").read_text(encoding="utf-8"))
    elements = metadata["elements"]
    if len(elements) != 1 or elements[0]["versionName"] != version:
        raise ValueError("Expected one universal APK with the requested version")
    relative = Path(elements[0]["outputFile"])
    if relative.name != str(relative) or "unsigned" in relative.name:
        raise ValueError("Expected a signed APK inside the release output directory")
    source = release_dir / relative
    destination = root / "dist" / f"OneFrame-{version}.apk"
    destination.parent.mkdir(exist_ok=True)
    shutil.copy2(source, destination)
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    destination.with_suffix(".apk.sha256").write_text(
        f"{digest}  {destination.name}\n", encoding="utf-8"
    )
    return destination


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    version = validate_tag(root, os.environ.get("RELEASE_TAG", ""))
    if args.validate_only:
        print(f"Validated {version}")
    else:
        print(prepare_assets(root, version))


if __name__ == "__main__":
    main()
