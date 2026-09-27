"""Package portable source without local SDK paths, logs, or build caches."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
base = Path(__file__).resolve().parent.parent
out = base / 'dist' / 'album-send-source.zip'
out.parent.mkdir(exist_ok=True)
with ZipFile(out, 'w', ZIP_DEFLATED) as archive:
    for path in sorted(base.rglob('*')):
        rel = path.relative_to(base)
        if not path.is_file() or any(p in {'.gradle', 'build', 'dist', '.git'} for p in rel.parts):
            continue
        if path.name == 'local.properties' or path.suffix == '.log':
            continue
        archive.write(path, Path('album-send') / rel)
print(out)
