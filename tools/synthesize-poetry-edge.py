#!/usr/bin/env python3
"""Prepare local MP3 speech with Edge's online natural voices, on the PC only.

Requires edge-tts 7.3.1+. The TV never contacts the speech service.
Use separate fresh directories for meaning and optional author supplements.
"""
import argparse
import asyncio
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
from urllib.parse import parse_qs, urlsplit
import edge_tts
import edge_tts.communicate as communicate


async def prepare(args):
    catalog_path = args.catalog.resolve()
    catalog = json.loads(catalog_path.read_text(encoding='utf-8-sig'))
    segments = json.loads(args.segments.read_text(encoding='utf-8-sig')) if args.segments else {}
    destination = args.destination.resolve()
    repository = Path(__file__).resolve().parents[1]
    if destination == repository or repository in destination.parents and 'work' not in destination.relative_to(repository).parts:
        raise ValueError('Speech must stay outside source or inside ignored work/')
    destination.mkdir(parents=True, exist_ok=False)
    config = dict(provider='Microsoft Edge online natural speech', voice=args.voice, rate=args.rate,
        pitch='+0Hz', kind=args.kind, edgeTtsVersion=edge_tts.__version__,
        endpoint=args.endpoint, transport='TLS encrypted WebSocket over HTTPS upgrade',
        catalogSha256=hashlib.sha256(catalog_path.read_bytes()).hexdigest())
    if args.endpoint == 'msedge':
        token = parse_qs(urlsplit(communicate.WSS_URL).query)['TrustedClientToken'][0]
        communicate.WSS_URL = 'wss://api.msedgeservices.com/tts/cognitiveservices/websocket/v1?Ocp-Apim-Subscription-Key=' + token
    (destination / 'voice.json').write_text(json.dumps(config, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    semaphore = asyncio.Semaphore(1)
    results = []
    reuse_config = json.loads((args.reuse / 'voice.json').read_text(encoding='utf8')) if args.reuse else None

    async def one(poem):
        ident = poem['id']
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,60}', ident):
            raise ValueError('Invalid poem ID')
        text = poem['meaning'] if args.kind == 'meaning' else segments[ident].get('authorText', '')
        if not text:
            return
        if len(text) > 2400:
            raise ValueError('Speech text too long')
        async with semaphore:
            target = destination / (ident + '.mp3')
            if args.reuse and all(config[k] == reuse_config[k] for k in ('voice', 'rate', 'pitch', 'kind', 'catalogSha256')):
                prior = args.reuse / (ident + '.json')
                prior_audio = args.reuse / (ident + '.mp3')
                if prior.is_file() and prior_audio.is_file():
                    row = json.loads(prior.read_text(encoding='utf8'))
                    if row['text'] == text and row['sha256'] == hashlib.sha256(prior_audio.read_bytes()).hexdigest():
                        shutil.copyfile(prior_audio, target)
                        shutil.copyfile(prior, destination / prior.name)
                        results.append(row)
                        print(ident, args.kind, 'verified reuse', flush=True)
                        return
            error = None
            await asyncio.sleep(args.interval)
            for attempt in range(3):
                temporary = destination / (ident + f'.attempt{attempt+1}.part.mp3')
                metadata = []
                try:
                    speech = edge_tts.Communicate(text, args.voice, rate=args.rate,
                        proxy=args.proxy, connect_timeout=20, receive_timeout=60)
                    async def receive():
                        with temporary.open('xb') as output:
                            async for chunk in speech.stream():
                                if chunk['type'] == 'audio':
                                    output.write(chunk['data'])
                                else:
                                    metadata.append(chunk)
                    await asyncio.wait_for(receive(), 90)
                    if temporary.stat().st_size < 1000:
                        raise ValueError('Empty or truncated speech output')
                    temporary.rename(target)
                    row = dict(id=ident, text=text, textSha256=hashlib.sha256(text.encode('utf8')).hexdigest(),
                        sha256=hashlib.sha256(target.read_bytes()).hexdigest(), bytes=target.stat().st_size,
                        metadata=metadata, attempts=attempt+1)
                    results.append(row)
                    (destination / (ident + '.json')).write_text(json.dumps(row, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
                    print(ident, args.kind, 'saved', flush=True)
                    return
                except Exception as failure:
                    error = failure
                    (destination / (ident + f'.attempt{attempt+1}.error.txt')).write_text(str(failure), encoding='utf8')
                    if attempt < 2:
                        if getattr(failure, 'status', None) == 429:
                            retry_after = getattr(failure, 'headers', {}).get('Retry-After', '60')
                            delay = max(60, float(retry_after)) if str(retry_after).isdigit() else 60
                        else:
                            delay = 2 ** (attempt+1)
                        await asyncio.sleep(delay)
            raise RuntimeError('Edge speech failed for ' + ident) from error

    await asyncio.sleep(args.initial_delay)
    await asyncio.gather(*(one(poem) for poem in catalog['poems']))
    (destination / 'manifest.json').write_text(json.dumps(sorted(results, key=lambda row: row['id']), ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--destination', type=Path, required=True)
    parser.add_argument('--segments', type=Path)
    parser.add_argument('--kind', choices=('meaning', 'author'), default='meaning')
    parser.add_argument('--voice', default='zh-CN-XiaoxiaoNeural')
    parser.add_argument('--rate', default='-8%')
    parser.add_argument('--proxy', help='Optional HTTP proxy for the PC preparation process only')
    parser.add_argument('--endpoint', choices=('bing', 'msedge'), default='msedge')
    parser.add_argument('--reuse', type=Path, help='Reuse only matching, hash-verified completed clips from a prior directory')
    parser.add_argument('--interval', type=float, default=1, help='Pause between serial speech requests in seconds')
    parser.add_argument('--initial-delay', type=float, default=0, help='Wait for service cooldown before any requests')
    args = parser.parse_args()
    if args.kind == 'author' and not args.segments:
        parser.error('Author supplements require reviewed --segments')
    if sys.platform == 'win32':
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    asyncio.run(prepare(args))


if __name__ == '__main__':
    main()
