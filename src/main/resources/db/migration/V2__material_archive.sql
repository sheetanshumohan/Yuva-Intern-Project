-- Keep the stock ledger immutable: deleting a lot means archiving it for future operations.
alter table raw_material_lots add column archived boolean not null default false;
create index idx_raw_material_archived on raw_material_lots(archived);
