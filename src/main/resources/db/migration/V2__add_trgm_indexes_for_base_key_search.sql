create extension if not exists pg_trgm;

create index if not exists idx_bk_online_trgm
    on base_key_entity using gin (online_key gin_trgm_ops);

create index if not exists idx_bk_offline_trgm
    on base_key_entity using gin (offline_key gin_trgm_ops);
