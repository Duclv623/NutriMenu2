"""Copy the old PostgreSQL identity/state bridge into Java User Service, without changing the source.

Default is a read-only audit. --apply performs one target transaction and refuses
conflicting mappings or state; it never overwrites a new account's activity.
"""
import argparse
import json
from pathlib import Path
import sys
from urllib.parse import quote
import psycopg
from psycopg import sql
from bootstrap_runtime import ROOT, read_env

def migrate(apply=False):
    cfg = read_env(ROOT / 'BackendNutriMenu/.env')
    source_dsn = cfg.get('LEGACY_GATEWAY_POSTGRES_DSN')
    if not source_dsn: raise RuntimeError('Set LEGACY_GATEWAY_POSTGRES_DSN in BackendNutriMenu/.env.')
    schema = cfg.get('LEGACY_GATEWAY_SCHEMA', 'gateway')
    target_dsn = cfg.get('USER_POSTGRES_DSN') or (
        'postgresql://' + quote(cfg.get('DB_USERNAME', 'postgres'), safe='') + ':' + quote(cfg.get('DB_PASSWORD', '123'), safe='')
        + '@' + cfg.get('DB_HOST', '127.0.0.1') + ':' + cfg.get('DB_PORT', '5432') + '/menufood_users')
    with psycopg.connect(source_dsn, connect_timeout=5) as source:
        source.execute('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY')
        identities = source.execute(sql.SQL('SELECT account_id,service_user_id FROM {}.team_identities').format(sql.Identifier(schema))).fetchall()
        state_rows = source.execute(sql.SQL('SELECT account_id,state_key,value FROM {}.team_ui_state').format(sql.Identifier(schema))).fetchall()
    states = {}
    for account, name, value in state_rows:
        if name != 'profileMeta': states.setdefault(account, {})[name] = value
    with psycopg.connect(target_dsn, connect_timeout=5) as target:
        if apply: target.execute('LOCK TABLE public.workspace_accounts IN EXCLUSIVE MODE')
        known = {row[0] for row in target.execute('SELECT id FROM public.users')}
        existing = {row[0]: (row[1], json.loads(row[2])) for row in target.execute('SELECT account_id,service_user_id,state FROM public.workspace_accounts')}
        used_ids = {value[0]: key for key,value in existing.items()}
        additions = []
        skipped = 0
        for account, service_id in identities:
            if account not in known:
                skipped += 1
                continue
            state = states.get(account, {})
            if service_id in used_ids and used_ids[service_id] != account:
                raise RuntimeError('Numeric service identity conflicts with another account; no changes committed.')
            if account in existing:
                previous_id, previous_state = existing[account]
                if previous_id != service_id or any(k in previous_state and previous_state[k] != v for k,v in state.items()):
                    raise RuntimeError('Target has different identity/activity. Resolve the conflict before retrying; no changes committed.')
                state = {**state, **previous_state}
            additions.append((account, service_id, state))
        print(json.dumps({'sourceAccounts':len(identities),'matchingJavaAccounts':len(additions),
                          'missingJavaUsers':skipped,'apply':apply,'sourceChanged':False}))
        if apply:
            for account, service_id, state in additions:
                target.execute('INSERT INTO public.workspace_accounts(account_id,service_user_id,state) VALUES (%s,%s,%s) '
                               'ON CONFLICT(account_id) DO UPDATE SET state=EXCLUDED.state',
                               (account,service_id,json.dumps(state,ensure_ascii=False)))
            target.execute("SELECT setval(pg_get_serial_sequence('public.workspace_accounts','service_user_id'), "
                           "GREATEST(COALESCE((SELECT MAX(service_user_id) FROM public.workspace_accounts),2000000000), "
                           "COALESCE(pg_sequence_last_value(pg_get_serial_sequence('public.workspace_accounts','service_user_id')::regclass),2000000000),2000000000),true)")
        else:
            target.rollback()

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    try: migrate(args.apply)
    except (psycopg.Error, RuntimeError) as error:
        detail = str(error) if isinstance(error, RuntimeError) else 'Database connection/query failed; credentials were not logged.'
        print('Migration stopped:', type(error).__name__, detail, file=sys.stderr)
        raise SystemExit(1)
