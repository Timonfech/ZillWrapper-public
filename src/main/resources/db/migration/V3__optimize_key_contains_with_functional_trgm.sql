create extension if not exists pg_trgm;

drop index if exists idx_bk_online_trgm;
drop index if exists idx_bk_offline_trgm;

create index if not exists idx_bk_online_lower_trgm
    on base_key_entity using gin (lower(online_key) gin_trgm_ops);

create index if not exists idx_bk_offline_lower_trgm
    on base_key_entity using gin (lower(offline_key) gin_trgm_ops);
