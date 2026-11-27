"""Validate the packaged NIN catalog, immutable local photos and RAG source hashes."""
from pathlib import Path
import argparse
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def verify(legacy_root=None):
    folder = ROOT / 'BackendNutriMenu/foodcatalogservice/src/main/resources/catalog/nin-bulk-20261126'
    catalog_path = folder / 'nin-source-import.json'
    catalog = json.loads(catalog_path.read_text('utf-8'))
    readiness = json.loads((folder / 'integration-readiness.json').read_text('utf-8'))
    dishes = catalog['dishes']
    codes = {dish['code'] for dish in dishes}
    assert len(codes) == len(dishes) == readiness['sourceCount']
    assert all(code.startswith('NIN_BULK_') for code in codes)
    text = (ROOT / 'frontend/src/data/ninImages.ts').read_text('utf-8')
    images = json.loads(text.split('export const ninImages:', 1)[1].split(' = ',1)[1].strip().removesuffix(';'))
    assert codes <= images.keys(), 'A catalog dish has no image mapping'
    for code, asset in images.items():
        file = ROOT / 'frontend/public' / asset['localAssetPath'].lstrip('/')
        assert file.is_relative_to(ROOT / 'frontend/public') and file.is_file(), f'Missing image: {code}'
        assert digest(file) == asset['sha256'], f'Image hash mismatch: {code}'
    manifest = json.loads((ROOT / 'ai-service/config/sources.json').read_text('utf-8'))
    verified_sources = 0
    for source in manifest['sources']:
        if source.get('enabled') is False: continue
        if source.get('sha256'):
            assert digest(ROOT / 'ai-service/data' / source['filename']) == source['sha256'], source['id']
        if source.get('original_raw_file'):
            original = ROOT / 'ai-service/data/collected' / source.get('original_collection','rag_supplements_2026-10-08') / source['original_raw_file']
            assert digest(original) == source['original_sha256'], source['id']
        verified_sources += 1
    if legacy_root:
        original = Path(legacy_root) / 'foodcatalogservice/src/main/resources/catalog/nin-bulk-20261126/nin-source-import.json'
        assert digest(original) == digest(catalog_path), 'Catalog changed during port'
    result = {'dataset':catalog['datasetId'],'dishes':len(dishes),'generalEligible':readiness['generalEligibleCount'],
              'medicalEligible':readiness['medicalEligibleCount'],'imageMappings':len(images),
              'uniqueImageFiles':len({a['localAssetPath'] for a in images.values()}),
              'ragSourcesVerified':verified_sources,'catalogSha256':digest(catalog_path)}
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return result

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--legacy-root', type=Path)
    args = parser.parse_args()
    verify(args.legacy_root)
