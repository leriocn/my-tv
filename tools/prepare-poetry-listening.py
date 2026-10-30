#!/usr/bin/env python3
"""Preserve human title/author/verse, remove textbook introductions, add Edge meanings.

Requires ffmpeg/ffprobe. The library and original recordings remain untouched.
Segments contain reviewed introStart/introEnd, start/end, sourceSha256 and
authorText (empty if the original recording already announces the author).
Edge speech is prepared on the PC by synthesize-poetry-edge.py, never by the TV.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True, encoding='utf8', timeout=120)


def seconds(path):
    return float(run('ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'default=nw=1:nk=1', str(path)).stdout)


def normalized(source, destination):
    result = run('ffmpeg', '-nostdin', '-hide_banner', '-i', str(source), '-af',
        'loudnorm=I=-20:TP=-2:LRA=9:print_format=json', '-f', 'null', '-')
    match = re.search(r'\{\s*"input_i".*?\}', result.stderr, re.S)
    if not match:
        raise ValueError('No loudness measurement for ' + str(source))
    levels = json.loads(match[0])
    if not all(math.isfinite(float(levels[k])) for k in ('input_i', 'input_tp', 'input_lra', 'input_thresh', 'target_offset')):
        raise ValueError('Invalid or silent speech: ' + str(source))
    filters = 'loudnorm=I=-20:TP=-2:LRA=9:linear=true:' + ':'.join(
        key + '=' + levels[value] for key, value in [('measured_I', 'input_i'), ('measured_TP', 'input_tp'),
        ('measured_LRA', 'input_lra'), ('measured_thresh', 'input_thresh'), ('offset', 'target_offset')])
    run('ffmpeg', '-nostdin', '-v', 'error', '-i', str(source), '-af', filters,
        '-ac', '1', '-ar', '24000', '-c:a', 'aac', '-b:a', '64k', str(destination))
    run('ffmpeg', '-nostdin', '-v', 'error', '-i', str(destination), '-f', 'null', '-')
    return levels


def speech_file(directory, ident, text):
    file = directory / (ident + '.mp3')
    metadata = json.loads((directory / (ident + '.json')).read_text(encoding='utf8'))
    if metadata['text'] != text or metadata['sha256'] != digest(file):
        raise ValueError('Speech text or audio changed for ' + ident)
    return file


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--library', type=Path, required=True)
    parser.add_argument('--catalog', type=Path, help='Original catalog used by the saved Edge speech; defaults to library/poems.json')
    parser.add_argument('--meanings', type=Path, required=True)
    parser.add_argument('--authors', type=Path, required=True)
    parser.add_argument('--segments', type=Path, required=True)
    parser.add_argument('--destination', type=Path, required=True)
    args = parser.parse_args()
    source, destination = args.library.resolve(), args.destination.resolve()
    repository = Path(__file__).resolve().parents[1]
    if destination == repository or repository in destination.parents and 'work' not in destination.relative_to(repository).parts:
        parser.error('Prepared media must be outside source or inside ignored work/')
    if destination.exists():
        parser.error('Destination exists; use a fresh directory')
    catalog_file = args.catalog.resolve() if args.catalog else source / 'poems.json'
    catalog = json.loads(catalog_file.read_text(encoding='utf-8-sig'))
    segments = json.loads(args.segments.read_text(encoding='utf-8-sig'))
    voices = {}
    for kind, directory in [('meaning', args.meanings), ('author', args.authors)]:
        voices[kind] = json.loads((directory / 'voice.json').read_text(encoding='utf8'))
        if voices[kind]['catalogSha256'] != digest(catalog_file) or voices[kind]['kind'] != kind:
            raise ValueError('Speech catalog or kind changed; regenerate ' + kind)
    (destination / 'audio').mkdir(parents=True)
    (destination / 'intermediates').mkdir()
    results = []
    trim = 'silenceremove=start_periods=1:start_duration=0.02:start_threshold=-50dB,areverse,silenceremove=start_periods=1:start_duration=0.02:start_threshold=-50dB,areverse'
    for poem in catalog['poems']:
        ident, original = poem['id'], poem['audio']
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,60}', ident) or not re.fullmatch(r'audio/[A-Za-z0-9_-]+\.(m4a|mp3|ogg|opus|wav)', original):
            raise ValueError('Unsafe ID or original audio path')
        original_file = source / original
        segment = segments[ident]
        if digest(original_file) != segment['sourceSha256']:
            raise ValueError('Recording changed: recheck ranges for ' + ident)
        head_start, head_end, start, end = (segment[k] for k in ('introStart', 'introEnd', 'start', 'end'))
        if not 0 <= head_start < head_end <= start < end <= seconds(original_file):
            raise ValueError('Invalid human title/author/verse ranges for ' + ident)
        author_text = segment.get('authorText', '')
        if author_text and author_text != poem['author'] + '。':
            raise ValueError('Author supplement must contain only the missing author name')
        wav = destination / 'intermediates' / (ident + '-human.wav')
        filters = (f'[0:a]asplit=2[head][verse];[head]atrim=start={head_start}:end={head_end},asetpts=PTS-STARTPTS,aresample=24000,'
            f'afade=t=in:d=0.015,afade=t=out:st={head_end-head_start-.025}:d=0.025,apad=pad_dur=0.20[h];'
            f'[verse]atrim=start={start}:end={end},asetpts=PTS-STARTPTS,aresample=24000,'
            f'afade=t=in:d=0.02,afade=t=out:st={end-start-.04}:d=0.04[v];')
        inputs = ['-i', str(original_file)]
        if author_text:
            inputs += ['-i', str(speech_file(args.authors, ident, author_text))]
            filters += f'[1:a]{trim},aresample=24000,apad=pad_dur=0.25[a];[h][a][v]concat=n=3:v=0:a=1[out]'
        else:
            filters += '[h][v]concat=n=2:v=0:a=1[out]'
        run('ffmpeg', '-nostdin', '-v', 'error', *inputs, '-filter_complex', filters,
            '-map', '[out]', '-ac', '1', '-ar', '24000', '-c:a', 'pcm_s16le', str(wav))
        listening = destination / 'audio' / (ident + '-human.m4a')
        human_levels = normalized(wav, listening)
        raw_meaning = speech_file(args.meanings, ident, poem['meaning'])
        meaning_wav = destination / 'intermediates' / (ident + '-meaning.wav')
        run('ffmpeg', '-nostdin', '-v', 'error', '-i', str(raw_meaning), '-af', trim + ',apad=pad_dur=0.10',
            '-ac', '1', '-ar', '24000', '-c:a', 'pcm_s16le', str(meaning_wav))
        meaning = destination / 'audio' / (ident + '-edge-meaning.m4a')
        meaning_levels = normalized(meaning_wav, meaning)
        poem['listeningAudio'] = 'audio/' + listening.name
        poem['meaningAudio'] = 'audio/' + meaning.name
        for part, file, levels in [('listening', listening, human_levels), ('meaning', meaning, meaning_levels)]:
            results.append(dict(id=ident, part=part, file='audio/' + file.name, sha256=digest(file), bytes=file.stat().st_size,
                seconds=seconds(file), original=original, originalSha256=segment['sourceSha256'], ranges=segment,
                humanTitlePreserved=part == 'listening', humanAuthorPreserved=part == 'listening' and not author_text,
                authorSupplement=author_text if part == 'listening' else '',
                edgeVoice=voices['author' if part == 'listening' else 'meaning'], inputLoudness=levels,
                textSha256=hashlib.sha256((poem['title']+'。'+poem['author']+'。' if part == 'listening' else poem['meaning']).encode('utf8')).hexdigest()))
        print(ident, 'human + Edge meaning verified', flush=True)
    for filename, data in [('poems.json', catalog), ('listening-sources.json', results), ('listening-segments.json', segments)]:
        (destination / filename).write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


if __name__ == '__main__':
    main()
