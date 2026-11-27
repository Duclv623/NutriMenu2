"""Prepare ignored local configuration and persistent JWT keys inside this checkout."""
from pathlib import Path
import argparse
import os

ROOT = Path(__file__).resolve().parents[1]

def read_env(path):
    result = {}
    if path.exists():
        for line in path.read_text('utf-8-sig').splitlines():
            if '=' in line and not line.lstrip().startswith('#'):
                key, value = line.split('=', 1)
                result[key.strip()] = value.strip().strip('"').strip("'")
    return result

def prepare():
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric import rsa
    keys = ROOT / '.runtime/keys'
    keys.mkdir(parents=True, exist_ok=True)
    private, public = keys / 'jwt-private.pem', keys / 'jwt-public.pem'
    if private.exists() != public.exists():
        raise RuntimeError('Only one JWT key exists. Restore the matching pair; keys were not overwritten.')
    if not private.exists():
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        private.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        public.write_bytes(key.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo))
    backend = ROOT / 'BackendNutriMenu/.env'
    if not backend.exists():
        backend.write_text((ROOT / 'BackendNutriMenu/.env.example').read_text('utf-8'), encoding='utf-8')
    config = read_env(backend)
    additions = {'JWT_PRIVATE_KEY_PATH': private.as_posix(), 'JWT_PUBLIC_KEY_PATH': public.as_posix()}
    with backend.open('a', encoding='utf-8') as stream:
        for name, value in additions.items():
            if not config.get(name): stream.write(f'\n{name}={value}\n')
    frontend = ROOT / 'frontend/.env.local'
    if not frontend.exists():
        frontend.write_text('VITE_API_MODE=api\nVITE_API_BASE_URL=/api\nVITE_RAG_BASE_URL=/api/chatbot\nVITE_RAG_ENABLED=true\n', encoding='utf-8')
    (ROOT / '.runtime/tiktoken').mkdir(parents=True, exist_ok=True)
    return read_env(backend)

def create_databases(config):
    import psycopg
    from psycopg import sql
    user = config.get('DB_USERNAME', 'postgres')
    password = config.get('DB_PASSWORD', '123')
    # Add databases only. Existing tables and databases are never dropped or reset.
    with psycopg.connect(host=config.get('DB_HOST', '127.0.0.1'), port=config.get('DB_PORT', '5432'),
                         dbname='postgres', user=user, password=password, autocommit=True, connect_timeout=5) as db:
        for name in ('menufood_users', 'diabetes_food_catalog_db', 'diabetes_meal_optimizer_db', 'nutriguide'):
            if not db.execute('SELECT 1 FROM pg_database WHERE datname=%s', (name,)).fetchone():
                db.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(name)))
                print('Created database:', name)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--create-databases', action='store_true')
    parser.add_argument('--prepare-tokenizer', action='store_true')
    args = parser.parse_args()
    config = prepare()
    if args.create_databases: create_databases(config)
    if args.prepare_tokenizer:
        os.environ['TIKTOKEN_CACHE_DIR'] = str(ROOT / '.runtime/tiktoken')
        import tiktoken
        tiktoken.get_encoding('cl100k_base')
    print('Local configuration and persistent JWT keys are ready.')
