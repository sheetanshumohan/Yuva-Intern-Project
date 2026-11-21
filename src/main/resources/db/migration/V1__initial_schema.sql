create table user_accounts (
  id uuid primary key, email varchar(160) not null unique, password_hash varchar(255) not null,
  role varchar(12) not null, enabled boolean not null
);
create table suppliers (
  id uuid primary key, name varchar(150) not null unique, contact_email varchar(160) not null,
  origin_region varchar(120) not null, active boolean not null
);
create table production_lines (
  id uuid primary key, code varchar(20) not null unique, name varchar(140) not null,
  capacity_kg_per_day numeric(14,3) not null, active boolean not null,
  constraint chk_line_capacity check (capacity_kg_per_day >= 0)
);
create table raw_material_lots (
  id uuid primary key, lot_code varchar(100) not null unique, supplier_id uuid not null references suppliers(id),
  material_name varchar(140) not null, stock_kg numeric(14,3) not null, expiry_date date, version bigint not null default 0,
  constraint chk_stock check (stock_kg >= 0)
);
create table batches (
  id uuid primary key, batch_number varchar(90) not null, product_name varchar(150) not null,
  production_line_id uuid not null references production_lines(id), supplier_id uuid not null references suppliers(id),
  raw_weight_kg numeric(14,3) not null, output_weight_kg numeric(14,3) not null,
  produced_at timestamp(6) with time zone not null, shift varchar(12) not null,
  moisture_percent numeric(6,2), status varchar(20) not null, machine_code varchar(80) not null,
  notes varchar(1000), version bigint not null default 0,
  constraint chk_batch_weights check (raw_weight_kg > 0 and output_weight_kg >= 0 and output_weight_kg <= raw_weight_kg),
  constraint chk_batch_moisture check (moisture_percent is null or (moisture_percent >= 0 and moisture_percent <= 100))
);
create index idx_batch_number on batches(batch_number);
create index idx_batches_produced on batches(produced_at);
create index idx_batches_line_status on batches(production_line_id, status);
create table quality_checks (
  id uuid primary key, batch_id uuid not null references batches(id), checked_at timestamp(6) with time zone not null,
  moisture_percent numeric(6,2), result varchar(12) not null, notes varchar(1000),
  constraint chk_qc_moisture check (moisture_percent is null or (moisture_percent >= 0 and moisture_percent <= 100))
);
create index idx_quality_batch on quality_checks(batch_id);
create table material_usages (
  id uuid primary key, batch_id uuid not null references batches(id),
  material_lot_id uuid not null references raw_material_lots(id), quantity_kg numeric(14,3) not null,
  constraint chk_usage_qty check (quantity_kg > 0)
);
create index idx_usage_batch on material_usages(batch_id);
create table inventory_movements (
  id uuid primary key, material_lot_id uuid not null references raw_material_lots(id),
  change_kg numeric(14,3) not null, reason varchar(180) not null,
  created_at timestamp(6) with time zone not null,
  constraint chk_nonzero_change check (change_kg <> 0)
);
create index idx_movements_lot_time on inventory_movements(material_lot_id, created_at desc);
