#!/usr/bin/env python3
"""Prepare the owner's offline poetry library; keep recordings outside Git.

Uses the public CNR article's published player URL, then ffmpeg for local AAC.
Requires Python 3.10+ and ffmpeg/ffprobe on PATH. Existing files are preserved.
"""
import argparse
import concurrent.futures
import hashlib
import html
import json
from pathlib import Path
import re
import shutil
import subprocess
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


def cnr_url(url):
    parts = urlsplit(url)
    if parts.scheme != "https" or not (parts.hostname == "cnr.cn" or (parts.hostname or "").endswith(".cnr.cn")):
        raise ValueError("Expected a published HTTPS CNR resource")
    return url


def prepare(poem, destination):
    path = destination / poem["audio"]
    if not re.fullmatch(r"audio/[A-Za-z0-9_-]+\.m4a", poem["audio"]):
        raise ValueError("Unsafe audio path")
    if not path.exists():
        request = Request(cnr_url(poem["audioSource"]), headers={"User-Agent": "MyTV personal poetry preparation"})
        with urlopen(request, timeout=30) as response:
            page = response.read(2 * 1024 * 1024).decode("gb18030", errors="replace")
        media = re.findall(r'''(?:src|data-url)=["']([^"']+\.(?:m4a|mp3|m3u8)[^"']*)''', page)
        if not media:
            raise RuntimeError("Article has no published audio player")
        url = cnr_url(html.unescape(media[0]).replace("http://", "https://", 1))
        temporary = path.with_suffix(".part.m4a")
        result = subprocess.run(["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                                 "-rw_timeout", "20000000", "-i", url, "-vn", "-ac", "1", "-ar", "24000",
                                 "-c:a", "aac", "-b:a", "64k", str(temporary)], capture_output=True, timeout=120)
        if result.returncode or not temporary.is_file() or temporary.stat().st_size < 3000:
            raise RuntimeError("Audio download or conversion failed; existing files preserved")
        temporary.replace(path)
    probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", str(path)],
                           capture_output=True, text=True, timeout=20, check=True)
    seconds = float(probe.stdout)
    if not 5 < seconds < 300:
        raise RuntimeError("Unexpected recording duration")
    return {"id": poem["id"], "title": poem["title"], "narrator": poem["narrator"],
            "source": poem["audioSource"], "file": poem["audio"], "seconds": seconds,
            "bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, required=True)
    args = parser.parse_args()
    repository = Path(__file__).resolve().parents[1]
    destination = args.destination.resolve()
    if destination == repository or repository in destination.parents and "work" not in destination.relative_to(repository).parts:
        parser.error("Recordings must be outside tracked source directories (or inside ignored work/)")
    for tool in ("ffmpeg", "ffprobe"):
        if not shutil.which(tool):
            parser.error(tool + " is required")
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "audio").mkdir(exist_ok=True)
    catalog = destination / "poems.json"
    if not catalog.exists():
        shutil.copyfile(repository / "launcher/app/src/main/assets/poetry/poems.json", catalog)
    poems = json.loads(catalog.read_text(encoding="utf-8-sig"))["poems"]
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
        for result in executor.map(lambda poem: prepare(poem, destination), poems):
            results.append(result)
            print(result["id"], "verified", flush=True)
    (destination / "sources.json").write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    shutil.copyfile(repository / "docs/POETRY_LIBRARY.zh-CN.md", destination / "使用说明.txt")
    print("Prepared", len(results), "recordings")


if __name__ == "__main__":
    main()
