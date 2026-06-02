create table if not exists users (
    id bigserial primary key,
    username varchar(50) not null unique,
    full_name varchar(120),
    role varchar(20) not null,
    is_active boolean not null default true,
    last_login_at timestamp,
    created_at timestamp not null default now()
);

create table if not exists clients (
    id bigserial primary key,
    name varchar(120),
    phone varchar(50),
    locale varchar(20),
    client_type varchar(20)
);

create table if not exists contact_method (
    id bigserial primary key,
    contact_kind varchar(31) not null,
    type varchar(255),
    label varchar(255),
    preferred boolean,
    client_id bigint references clients(id),
    user_id bigint references users(id)
);

create table if not exists email_contact (
    id bigint primary key references contact_method(id) on delete cascade,
    encrypted_value text,
    value_hash varchar(256)
);
create unique index if not exists uk_email_contact_value_hash on email_contact(value_hash);

create table if not exists phone_contact (
    id bigint primary key references contact_method(id) on delete cascade,
    encrypted_value text,
    value_hash varchar(256)
);
create index if not exists idx_phone_value_hash on phone_contact(value_hash);

create table if not exists telegram_contact (
    id bigint primary key references contact_method(id) on delete cascade,
    encrypted_telegram_id text,
    encrypted_username text,
    encrypted_tg_chat_id text,
    value_hash varchar(256)
);
create index if not exists idx_tg_value_hash on telegram_contact(value_hash);

create table if not exists ip_contact (
    id bigint primary key references contact_method(id) on delete cascade,
    encrypted_value text,
    value_hash varchar(256)
);
create index if not exists idx_ip_value_hash on ip_contact(value_hash);

create table if not exists sources (
    id bigserial primary key,
    type varchar(255),
    identifier_name varchar(255),
    constraint uk_source_type_identifier unique (type, identifier_name)
);

create table if not exists products (
    product_id integer primary key,
    brand_id integer not null,
    version integer not null,
    group_id varchar(255),
    regex_pattern varchar(255),
    names jsonb,
    properties jsonb,
    key_types jsonb
);
create unique index if not exists uk_products_brand_product on products(brand_id, product_id);

create table if not exists user_sources (
    id bigserial primary key,
    user_id bigint not null references users(id),
    source_id bigint not null references sources(id),
    source_type varchar(50) not null,
    constraint uk_user_source unique (user_id, source_id)
);

create table if not exists user_source_factors (
    user_source_id bigint not null references user_sources(id) on delete cascade,
    factor_type varchar(255) not null,
    factor_value varchar(255),
    constraint pk_user_source_factors primary key (user_source_id, factor_type),
    constraint uk_source_factor_value unique (factor_type, factor_value)
);

create table if not exists user_product_quotas (
    id bigserial primary key,
    user_id bigint not null references users(id),
    product_id integer references products(product_id),
    max_total_pc integer,
    max_pc_per_license integer,
    max_total_licenses_per_item integer,
    max_period_amount integer,
    max_period_unit varchar(16),
    remaining_quantity integer,
    reserved_quantity integer,
    last_updated_at timestamp
);

create table if not exists user_quota_operations (
    quota_id bigint not null references user_product_quotas(id) on delete cascade,
    operation_type varchar(255),
    constraint pk_user_quota_operations primary key (quota_id, operation_type)
);

create table if not exists orders (
    id bigserial primary key,
    client_id bigint references clients(id),
    created_by bigint references users(id),
    created_at timestamp not null,
    created_at_origin timestamp,
    updated_at_origin timestamp,
    white_admin_id bigint,
    portal_id bigint,
    order_status varchar(255),
    payment_method varchar(255),
    user_comment text,
    client_comment text,
    external_ref varchar(255),
    http_ref text,
    client_type varchar(255),
    legal_entity_info_json text,
    total_amount numeric(19,4),
    currency varchar(255)
);
create unique index if not exists uk_orders_white_admin_id_not_null
    on orders(white_admin_id) where white_admin_id is not null;
create unique index if not exists uk_orders_portal_id_not_null
    on orders(portal_id) where portal_id is not null;

create table if not exists order_items (
    id bigserial primary key,
    order_id bigint not null references orders(id) on delete cascade,
    product_brand_id integer,
    product_id integer,
    pc_per_license integer,
    lic_count integer,
    period_amount integer,
    period_unit varchar(16),
    server_number integer,
    processing_status varchar(255) not null,
    key_types jsonb,
    output_types jsonb
);
create index if not exists idx_order_items_order_id on order_items(order_id);


create table if not exists order_delivery_targets (
    id bigserial primary key,
    order_id bigint not null references orders(id) on delete cascade,
    contact_id bigint not null references contact_method(id),
    output_format varchar(255) not null,
    enabled boolean not null default true
);
create index if not exists idx_order_delivery_targets_order_id on order_delivery_targets(order_id);

create table if not exists order_source_contexts (
    id bigserial primary key,
    order_id bigint not null references orders(id) on delete cascade,
    source_id bigint not null,
    operation_type varchar(255) not null,
    operation_id numeric(38,0),
    user_id bigint,
    captured_at timestamp,
    context_data jsonb
);

create table if not exists base_key_entity (
    id bigserial primary key,
    type varchar(31),
    online_key text,
    offline_key text,
    meta_data jsonb,
    reserved_servers integer,
    company varchar(255)
);
create index if not exists idx_base_key_online on base_key_entity(online_key);
create index if not exists idx_base_key_offline on base_key_entity(offline_key);

create table if not exists licenses (
    id bigserial primary key,
    external_id bigint,
    order_id bigint references orders(id),
    order_item_id bigint references order_items(id),
    client_id bigint references clients(id),
    key_id bigint unique references base_key_entity(id),
    period_amount integer,
    period_unit varchar(16),
    brand_id integer,
    product_id integer,
    devices integer,
    created_at timestamp,
    created_at_origin timestamp,
    expires_at timestamp,
    status integer,
    description varchar(255),
    source_id bigint,
    version_no bigint not null default 1
);
create unique index if not exists uk_licenses_external_id on licenses(external_id);
create index if not exists idx_licenses_order_id on licenses(order_id);
create index if not exists idx_licenses_order_item_id on licenses(order_item_id);

create table if not exists license_versions (
    id bigserial primary key,
    license_id bigint not null references licenses(id) on delete cascade,
    version_no bigint not null,
    changed_at timestamp not null default now(),
    change_source varchar(64) not null default 'SYSTEM',
    changed_by varchar(128),
    external_id bigint,
    order_id bigint,
    order_item_id bigint,
    client_id bigint,
    period_amount integer,
    period_unit varchar(16),
    devices integer,
    created_at timestamp,
    created_at_origin timestamp,
    expires_at timestamp,
    status integer,
    description varchar(255),
    source_id bigint
);
create unique index if not exists uk_license_versions_license_ver on license_versions(license_id, version_no);
create index if not exists idx_license_versions_license_id on license_versions(license_id);
create index if not exists idx_license_versions_changed_at on license_versions(changed_at desc);

create table if not exists license_activation_entity (
    id bigserial primary key,
    activation_id varchar(255),
    product_id varchar(255),
    external_license_id varchar(255),
    hardware varchar(255),
    profile_id varchar(255),
    created timestamp,
    last_request timestamp,
    last_ip varchar(255),
    last_success varchar(255),
    status varchar(255),
    dino_key_id bigint references base_key_entity(id) on delete cascade
);

create table if not exists white_admin_activation_entity (
    id bigserial primary key,
    pcid varchar(255),
    first_activation timestamp,
    last_activation timestamp,
    computers_activated integer,
    white_admin_key_id bigint references base_key_entity(id) on delete cascade
);

create table if not exists license_subscription_entity (
    id bigserial primary key,
    license_id bigint not null references licenses(id),
    order_id bigint references orders(id),
    source_id bigint,
    initiator_user_id bigint,
    status varchar(32),
    detailed boolean,
    notify_client boolean,
    warning_lead_amount integer,
    warning_lead_unit varchar(16),
    check_interval_minutes integer,
    activated_at timestamp,
    expected_expiration timestamp,
    next_check_at timestamp,
    notified_at timestamp,
    last_error text,
    updated_at timestamp
);

create table if not exists subscription_warning_delivery (
    id bigserial primary key,
    subscription_id bigint not null references license_subscription_entity(id) on delete cascade,
    license_id bigint not null references licenses(id) on delete cascade,
    order_id bigint references orders(id) on delete set null,
    source_id bigint references sources(id) on delete set null,
    window_at timestamp not null,
    status varchar(32) not null,
    attempt_count integer not null default 0,
    next_attempt_at timestamp not null,
    sent_at timestamp,
    last_error text,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    version bigint
);
create unique index if not exists uk_subscription_warning_delivery_window
    on subscription_warning_delivery(subscription_id, window_at);
create index if not exists idx_sub_warning_delivery_due
    on subscription_warning_delivery(status, next_attempt_at);

create table if not exists operation_execution (
    id bigserial primary key,
    source_id bigint,
    operation_type varchar(255),
    execution_kind varchar(255),
    entity_type_enum varchar(255),
    entity_id bigint,
    handleruuid varchar(255),
    handler_name varchar(255),
    status varchar(255),
    attempt integer not null,
    recoverable boolean not null,
    cancelable boolean not null,
    interaction_enabled boolean not null default false,
    error_message varchar(255),
    question_type varchar(255),
    question_json jsonb,
    execution_plan_json jsonb,
    sequence_no integer,
    non_blocking boolean not null default false,
    resume_at timestamp,
    initiator_user_id bigint references users(id),
    parent_id bigint references operation_execution(id),
    state_version bigint,
    created_at timestamp,
    updated_at timestamp,
    completed_at timestamp
);
create index if not exists idx_operation_execution_parent_id on operation_execution(parent_id);
create index if not exists idx_operation_execution_status on operation_execution(status);
create index if not exists idx_operation_execution_operation_type on operation_execution(operation_type);

create table if not exists pending_tasks (
    task_id varchar(64) primary key,
    task_type varchar(64) not null,
    status varchar(32) not null,
    source_id bigint not null references sources(id),
    initiator_user_id bigint not null references users(id),
    source_actor_id varchar(128),
    payload_type varchar(255) not null,
    payload_json jsonb not null,
    created_at timestamp,
    updated_at timestamp,
    expires_at timestamp,
    error_message text,
    version bigint
);
create index if not exists idx_pending_tasks_status_expires on pending_tasks(status, expires_at);
create index if not exists idx_pending_tasks_source_id on pending_tasks(source_id);
create index if not exists idx_pending_tasks_initiator_user_id on pending_tasks(initiator_user_id);

create table if not exists telegram_operation_bindings (
    id bigserial primary key,
    operation_id bigint references operation_execution(id) on delete cascade,
    chat_id bigint,
    control_message_id integer,
    question_queue_json jsonb,
    interaction_delivery_status varchar(32),
    active_preview_id varchar(255),
    preview_message_id integer,
    source_message_id integer,
    source_message_hash varchar(128),
    preview_payload_json jsonb,
    preview_created_at timestamp,
    preview_expires_at timestamp,
    preview_status varchar(255),
    locale_tag varchar(32),
    final_notified_at timestamp,
    final_notification_kind varchar(64),
    version bigint
);
create unique index if not exists idx_telegram_bindings_operation_id on telegram_operation_bindings(operation_id) where operation_id is not null;
create index if not exists idx_telegram_bindings_chat_id on telegram_operation_bindings(chat_id);
create index if not exists idx_telegram_bindings_active_preview on telegram_operation_bindings(active_preview_id);
create index if not exists idx_tob_chat_source_message on telegram_operation_bindings(chat_id, source_message_id);

create table if not exists enrichment_scheduler_settings (
    job_name varchar(64) primary key,
    enabled boolean not null default false,
    delay_minutes integer not null,
    updated_at timestamp not null default now(),
    updated_by varchar(128)
);

insert into enrichment_scheduler_settings(job_name, enabled, delay_minutes, updated_at, updated_by)
values ('ORDER_PAYED', false, 3, now(), 'system')
on conflict (job_name) do nothing;

create or replace function enforce_license_immutables_and_snapshot()
returns trigger as $fn$
begin
    if old.brand_id is distinct from new.brand_id then
        raise exception 'Immutable field violation: licenses.brand_id cannot be changed';
    end if;
    if old.product_id is distinct from new.product_id then
        raise exception 'Immutable field violation: licenses.product_id cannot be changed';
    end if;

    if old.external_id is distinct from new.external_id
        or old.order_id is distinct from new.order_id
        or old.order_item_id is distinct from new.order_item_id
        or old.client_id is distinct from new.client_id
        or old.period_amount is distinct from new.period_amount
        or old.period_unit is distinct from new.period_unit
        or old.devices is distinct from new.devices
        or old.created_at is distinct from new.created_at
        or old.created_at_origin is distinct from new.created_at_origin
        or old.expires_at is distinct from new.expires_at
        or old.status is distinct from new.status
        or old.description is distinct from new.description
        or old.source_id is distinct from new.source_id then

        insert into license_versions (
            license_id, version_no, changed_at, change_source, changed_by,
            external_id, order_id, order_item_id, client_id, period_amount, period_unit, devices,
            created_at, created_at_origin, expires_at, status, description, source_id
        ) values (
            old.id,
            old.version_no,
            now(),
            coalesce(nullif(current_setting('app.change_source', true), ''), 'SYSTEM'),
            nullif(current_setting('app.changed_by', true), ''),
            old.external_id, old.order_id, old.order_item_id, old.client_id, old.period_amount, old.period_unit, old.devices,
            old.created_at, old.created_at_origin, old.expires_at, old.status, old.description, old.source_id
        );

        new.version_no := coalesce(old.version_no, 1) + 1;
    end if;

    return new;
end;
$fn$ language plpgsql;

drop trigger if exists trg_license_snapshot on licenses;
create trigger trg_license_snapshot before update on licenses for each row execute function enforce_license_immutables_and_snapshot();

create or replace function enforce_key_immutables()
returns trigger as $fn$
begin
    if old.online_key is distinct from new.online_key then
        raise exception 'Immutable field violation: base_key_entity.online_key cannot be changed';
    end if;
    if old.offline_key is distinct from new.offline_key then
        raise exception 'Immutable field violation: base_key_entity.offline_key cannot be changed';
    end if;
    return new;
end;
$fn$ language plpgsql;

drop trigger if exists trg_key_immutable on base_key_entity;
create trigger trg_key_immutable before update on base_key_entity for each row execute function enforce_key_immutables();

alter table user_product_quotas
    add constraint ck_user_product_quotas_max_period_amount_positive
        check (max_period_amount is null or max_period_amount > 0);
alter table user_product_quotas
    add constraint ck_user_product_quotas_max_period_unit
        check (max_period_unit is null or max_period_unit in ('DAY', 'MONTH', 'YEAR'));

alter table order_items
    add constraint ck_order_items_period_amount_positive
        check (period_amount is null or period_amount > 0);
alter table order_items
    add constraint ck_order_items_period_unit
        check (period_unit is null or period_unit in ('DAY', 'MONTH', 'YEAR'));

alter table licenses
    add constraint ck_licenses_period_amount_positive
        check (period_amount is null or period_amount > 0);
alter table licenses
    add constraint ck_licenses_period_unit
        check (period_unit is null or period_unit in ('DAY', 'MONTH', 'YEAR'));
