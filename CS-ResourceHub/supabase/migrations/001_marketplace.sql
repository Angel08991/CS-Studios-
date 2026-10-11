-- CS ResourceHub marketplace schema. Run in Supabase SQL Editor or via `supabase db push`.
create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null default 'Creador' check (char_length(username) between 1 and 40),
  avatar_url text,
  role text not null default 'user' check (role in ('user','moderator','admin')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.resources (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles(id) on delete cascade,
  title text not null check (char_length(title) between 3 and 80),
  description text not null check (char_length(description) between 10 and 4000),
  category text not null check (category in ('Minecraft','Discord','Mapas y builds','Configuraciones','Gráficos','Otros')),
  version text not null default '1.0.0',
  requirements text not null default '',
  license_name text not null default 'Licencia del autor' check (char_length(license_name) <= 80),
  tags text[] not null default '{}',
  currency text not null default 'EUR' check (currency in ('EUR','USD')),
  price_cents integer not null default 0 check (price_cents = 0 or price_cents between 100 and 50000),
  file_path text,
  cover_url text,
  status text not null default 'pending' check (status in ('pending','approved','rejected','removed')),
  moderation_note text,
  download_count bigint not null default 0 check (download_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.favorites (
  user_id uuid not null references public.profiles(id) on delete cascade,
  resource_id uuid not null references public.resources(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, resource_id)
);

create table if not exists public.reviews (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  resource_id uuid not null references public.resources(id) on delete cascade,
  rating integer not null check (rating between 1 and 5),
  comment text not null default '' check (char_length(comment) <= 800),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (resource_id, user_id)
);

create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  resource_id uuid not null references public.resources(id) on delete cascade,
  reporter_id uuid not null references public.profiles(id) on delete cascade,
  reason text not null check (char_length(reason) between 3 and 120),
  details text not null default '' check (char_length(details) <= 1000),
  status text not null default 'open' check (status in ('open','reviewed','dismissed')),
  moderator_id uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  reviewed_at timestamptz,
  unique (resource_id, reporter_id, reason)
);

create table if not exists public.purchases (
  id uuid primary key default gen_random_uuid(),
  buyer_id uuid not null references public.profiles(id) on delete cascade,
  resource_id uuid not null references public.resources(id) on delete cascade,
  stripe_session_id text unique,
  amount_cents integer not null check (amount_cents >= 0),
  currency text not null default 'EUR',
  status text not null default 'pending' check (status in ('pending','paid','refunded','failed')),
  created_at timestamptz not null default now(),
  paid_at timestamptz,
  unique (buyer_id, resource_id)
);

create table if not exists public.seller_accounts (
  owner_id uuid primary key references public.profiles(id) on delete cascade,
  country text not null check (country ~ '^[A-Z]{2}$'),
  stripe_account_id text not null unique,
  charges_enabled boolean not null default false,
  payouts_enabled boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists resources_status_created_idx on public.resources(status, created_at desc);
create index if not exists resources_owner_created_idx on public.resources(owner_id, created_at desc);
create index if not exists favorites_resource_idx on public.favorites(resource_id);
create index if not exists reviews_resource_idx on public.reviews(resource_id, created_at desc);
create index if not exists reports_status_idx on public.reports(status, created_at desc);
create index if not exists purchases_buyer_status_idx on public.purchases(buyer_id, status);

create or replace function public.is_staff()
returns boolean language sql stable security definer set search_path = public
as $$ select exists (select 1 from public.profiles p where p.id = auth.uid() and p.role in ('moderator','admin')); $$;

create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = public
as $$ select exists (select 1 from public.profiles p where p.id = auth.uid() and p.role = 'admin'); $$;

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public
as $$
begin
  insert into public.profiles(id, username) values (
    new.id,
    left(coalesce(nullif(new.raw_user_meta_data->>'username',''), split_part(new.email,'@',1), 'Creador'),40)
  ) on conflict (id) do nothing;
  return new;
end;
$$;
drop trigger if exists on_auth_user_created_resourcehub on auth.users;
create trigger on_auth_user_created_resourcehub after insert on auth.users for each row execute procedure public.handle_new_user();

create or replace function public.guard_profile_changes()
returns trigger language plpgsql security definer set search_path = public
as $$
begin
  if auth.uid() is not null and not public.is_admin() and new.role is distinct from old.role then
    raise exception 'No tienes permiso para cambiar tu rol.' using errcode = '42501';
  end if;
  if auth.uid() is not null and new.id is distinct from old.id then
    raise exception 'No puedes cambiar el propietario del perfil.' using errcode = '42501';
  end if;
  new.updated_at = now();
  return new;
end;
$$;
drop trigger if exists guard_profile_changes_trigger on public.profiles;
create trigger guard_profile_changes_trigger before update on public.profiles for each row execute procedure public.guard_profile_changes();

create or replace function public.guard_resource_changes()
returns trigger language plpgsql security definer set search_path = public
as $$
begin
  if auth.uid() is null or public.is_admin() or current_setting('app.resourcehub_download', true) = 'on' then
    new.updated_at = now();
    return new;
  end if;
  if tg_op = 'INSERT' then
    if new.owner_id is distinct from auth.uid() then raise exception 'Propietario no válido.' using errcode = '42501'; end if;
    new.status := 'pending'; new.moderation_note := null; new.download_count := 0;
    new.created_at := now(); new.updated_at := now();
    return new;
  end if;
  if new.owner_id is distinct from old.owner_id then raise exception 'No puedes cambiar el propietario.' using errcode = '42501'; end if;
  if new.id is distinct from old.id then raise exception 'No puedes cambiar el identificador del recurso.' using errcode = '42501'; end if;
  if new.file_path is not null and left(new.file_path, length(auth.uid()::text || '/' || new.id::text || '/')) <> (auth.uid()::text || '/' || new.id::text || '/') then raise exception 'La ruta del archivo debe pertenecer a este recurso.' using errcode = '42501'; end if;
  if new.status is distinct from old.status or new.moderation_note is distinct from old.moderation_note then
    new.status := old.status;
    new.moderation_note := old.moderation_note;
  end if;
  if old.status = 'approved' and (
    new.title is distinct from old.title or new.description is distinct from old.description or
    new.category is distinct from old.category or new.file_path is distinct from old.file_path or
    new.price_cents is distinct from old.price_cents or new.version is distinct from old.version or
    new.requirements is distinct from old.requirements or new.license_name is distinct from old.license_name or new.tags is distinct from old.tags or new.cover_url is distinct from old.cover_url
  ) then
    new.status := 'pending'; new.moderation_note := null;
  end if;
  new.download_count := old.download_count;
  new.created_at := old.created_at;
  new.updated_at := now();
  return new;
end;
$$;
drop trigger if exists guard_resource_changes_trigger on public.resources;
create trigger guard_resource_changes_trigger before insert or update on public.resources for each row execute procedure public.guard_resource_changes();

create or replace function public.set_updated_at()
returns trigger language plpgsql as $$ begin new.updated_at = now(); return new; end; $$;
drop trigger if exists reviews_updated_at on public.reviews;
create trigger reviews_updated_at before update on public.reviews for each row execute procedure public.set_updated_at();

alter table public.profiles enable row level security;
alter table public.resources enable row level security;
alter table public.favorites enable row level security;
alter table public.reviews enable row level security;
alter table public.reports enable row level security;
alter table public.purchases enable row level security;
alter table public.seller_accounts enable row level security;

grant usage on schema public to anon, authenticated;
grant select on public.resources to anon, authenticated;
grant insert, update, delete on public.resources to authenticated;
grant select on public.profiles to anon, authenticated;
grant update (username, avatar_url) on public.profiles to authenticated;
grant select, insert, delete on public.favorites to authenticated;
grant select, insert, update on public.reviews to authenticated;
grant select, insert, update on public.reports to authenticated;
grant select on public.purchases to authenticated;
grant select on public.seller_accounts to authenticated;

drop policy if exists "Profiles are public" on public.profiles;
create policy "Profiles are public" on public.profiles for select to anon, authenticated using (true);
drop policy if exists "Users update their profile" on public.profiles;
create policy "Users update their profile" on public.profiles for update to authenticated using (id = auth.uid() or public.is_admin()) with check (id = auth.uid() or public.is_admin());

drop policy if exists "Public can view approved resources" on public.resources;
create policy "Public can view approved resources" on public.resources for select to anon, authenticated using (status = 'approved' or owner_id = auth.uid() or public.is_staff());
drop policy if exists "Users submit their own resources" on public.resources;
create policy "Users submit their own resources" on public.resources for insert to authenticated with check (
  owner_id = auth.uid() and (
    price_cents = 0 or exists (
      select 1 from public.seller_accounts s
      where s.owner_id = auth.uid() and s.charges_enabled and s.payouts_enabled
    )
  )
);
drop policy if exists "Owners and staff update resources" on public.resources;
create policy "Owners and staff update resources" on public.resources for update to authenticated
using (owner_id = auth.uid() or public.is_admin())
with check (
  (owner_id = auth.uid() or public.is_admin()) and (
    price_cents = 0 or exists (
      select 1 from public.seller_accounts s
      where s.owner_id = public.resources.owner_id and s.charges_enabled and s.payouts_enabled
    )
  )
);
drop policy if exists "Owners delete pending resources" on public.resources;
create policy "Owners delete pending resources" on public.resources for delete to authenticated using ((owner_id = auth.uid() and status in ('pending','rejected')) or public.is_admin());

drop policy if exists "Users see their favorites" on public.favorites;
create policy "Users see their favorites" on public.favorites for select to authenticated using (user_id = auth.uid());
drop policy if exists "Users add their favorites" on public.favorites;
create policy "Users add their favorites" on public.favorites for insert to authenticated with check (user_id = auth.uid() and exists(select 1 from public.resources r where r.id = resource_id and r.status='approved'));
drop policy if exists "Users remove their favorites" on public.favorites;
create policy "Users remove their favorites" on public.favorites for delete to authenticated using (user_id = auth.uid());

drop policy if exists "Public can see reviews on approved resources" on public.reviews;
create policy "Public can see reviews on approved resources" on public.reviews for select to anon, authenticated using (exists(select 1 from public.resources r where r.id=resource_id and r.status='approved') or user_id=auth.uid() or public.is_staff());
drop policy if exists "Signed-in users review approved resources" on public.reviews;
create policy "Signed-in users review approved resources" on public.reviews for insert to authenticated with check (user_id=auth.uid() and exists(select 1 from public.resources r where r.id=resource_id and r.status='approved' and r.owner_id<>auth.uid() and (r.price_cents=0 or exists(select 1 from public.purchases p where p.resource_id=r.id and p.buyer_id=auth.uid() and p.status='paid'))));
drop policy if exists "Review authors update their review" on public.reviews;
create policy "Review authors update their review" on public.reviews for update to authenticated
using (user_id=auth.uid())
with check (
  user_id=auth.uid() and exists(
    select 1 from public.resources r
    where r.id=resource_id and r.status='approved' and r.owner_id<>auth.uid()
      and (r.price_cents=0 or exists(
        select 1 from public.purchases p
        where p.resource_id=r.id and p.buyer_id=auth.uid() and p.status='paid'
      ))
  )
);

drop policy if exists "Users create reports" on public.reports;
create policy "Users create reports" on public.reports for insert to authenticated with check (reporter_id=auth.uid() and exists(select 1 from public.resources r where r.id=resource_id and r.status='approved'));
drop policy if exists "Users see own reports and staff see all" on public.reports;
create policy "Users see own reports and staff see all" on public.reports for select to authenticated using (reporter_id=auth.uid() or public.is_staff());
drop policy if exists "Staff update reports" on public.reports;
create policy "Staff update reports" on public.reports for update to authenticated using (public.is_staff()) with check (public.is_staff());

drop policy if exists "Sellers see their own payout status" on public.seller_accounts;
create policy "Sellers see their own payout status" on public.seller_accounts for select to authenticated using (owner_id=auth.uid() or public.is_admin());

drop policy if exists "Buyers see their purchases" on public.purchases;
create policy "Buyers see their purchases" on public.purchases for select to authenticated using (buyer_id=auth.uid() or public.is_admin());

-- Private resource files; the service-role webhook never exposes its key to the app.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values ('resource-files','resource-files',false,104857600,array['application/zip','application/x-zip-compressed','application/java-archive','application/octet-stream','application/json','text/plain','text/yaml','application/x-yaml','application/x-tar','application/gzip','application/x-schematic','application/x-minecraft-pack'])
on conflict (id) do update set public=false, file_size_limit=104857600;
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values ('resource-covers','resource-covers',true,5242880,array['image/png','image/jpeg','image/webp'])
on conflict (id) do update set public=true,file_size_limit=5242880;

drop policy if exists "Owners upload files for their own pending resources" on storage.objects;
create policy "Owners upload files for their own pending resources" on storage.objects for insert to authenticated with check (
  bucket_id='resource-files' and (storage.foldername(name))[1]=auth.uid()::text and exists(select 1 from public.resources r where r.owner_id=auth.uid() and r.status in ('pending','rejected') and r.id::text=(storage.foldername(name))[2])
);
drop policy if exists "Owners and eligible downloaders read resource files" on storage.objects;
create policy "Owners and eligible downloaders read resource files" on storage.objects for select to authenticated using (
  bucket_id='resource-files' and exists(select 1 from public.resources r where r.file_path=name and (storage.foldername(name))[1]=r.owner_id::text and (storage.foldername(name))[2]=r.id::text and (r.owner_id=auth.uid() or (r.status='approved' and (r.price_cents=0 or exists(select 1 from public.purchases p where p.resource_id=r.id and p.buyer_id=auth.uid() and p.status='paid')))))
);
drop policy if exists "Owners replace their pending files" on storage.objects;
create policy "Owners replace their pending files" on storage.objects for update to authenticated using (bucket_id='resource-files' and (storage.foldername(name))[1]=auth.uid()::text and exists(select 1 from public.resources r where r.owner_id=auth.uid() and r.id::text=(storage.foldername(name))[2] and r.status in ('pending','rejected'))) with check (bucket_id='resource-files' and (storage.foldername(name))[1]=auth.uid()::text and exists(select 1 from public.resources r where r.owner_id=auth.uid() and r.id::text=(storage.foldername(name))[2] and r.status in ('pending','rejected')));
drop policy if exists "Owners delete their files" on storage.objects;
create policy "Owners delete their files" on storage.objects for delete to authenticated using (bucket_id='resource-files' and (storage.foldername(name))[1]=auth.uid()::text and (storage.foldername(name))[2] in (select r.id::text from public.resources r where r.owner_id=auth.uid() and r.status in ('pending','rejected')));
drop policy if exists "Public read approved cover images" on storage.objects;
create policy "Public read approved cover images" on storage.objects for select to anon, authenticated using (bucket_id='resource-covers');
drop policy if exists "Owners upload cover images" on storage.objects;
create policy "Owners upload cover images" on storage.objects for insert to authenticated with check (bucket_id='resource-covers' and (storage.foldername(name))[1]=auth.uid()::text and exists(select 1 from public.resources r where r.owner_id=auth.uid() and r.id::text=(storage.foldername(name))[2]));
drop policy if exists "Owners manage their cover images" on storage.objects;
create policy "Owners manage their cover images" on storage.objects for delete to authenticated using (bucket_id='resource-covers' and (storage.foldername(name))[1]=auth.uid()::text);

create or replace function public.record_resource_download(p_resource_id uuid)
returns void language plpgsql security definer set search_path = public
as $$
declare r public.resources%rowtype;
begin
  if auth.uid() is null then raise exception 'Debes iniciar sesión.' using errcode='42501'; end if;
  select * into r from public.resources where id=p_resource_id and status='approved';
  if not found then raise exception 'Recurso no disponible.'; end if;
  if r.price_cents>0 and r.owner_id<>auth.uid() and not exists(select 1 from public.purchases p where p.resource_id=r.id and p.buyer_id=auth.uid() and p.status='paid') then raise exception 'Necesitas comprar este recurso.' using errcode='42501'; end if;
  perform set_config('app.resourcehub_download','on',true);
  update public.resources set download_count=download_count+1 where id=p_resource_id;
  perform set_config('app.resourcehub_download','off',true);
end;
$$;
revoke all on function public.record_resource_download(uuid) from public, anon;
grant execute on function public.record_resource_download(uuid) to authenticated;

create or replace function public.moderate_resource(p_resource_id uuid,p_status text,p_note text default null)
returns void language plpgsql security definer set search_path = public
as $$
begin
  if not public.is_admin() then raise exception 'Solo un administrador puede moderar recursos.' using errcode='42501'; end if;
  if p_status not in ('approved','rejected','removed') then raise exception 'Estado de moderación no válido.'; end if;
  update public.resources set status=p_status,moderation_note=left(coalesce(p_note,''),1000),updated_at=now() where id=p_resource_id;
  if not found then raise exception 'Recurso no encontrado.'; end if;
end;
$$;
revoke all on function public.moderate_resource(uuid,text,text) from public, anon;
grant execute on function public.moderate_resource(uuid,text,text) to authenticated;

-- IMPORTANT: after registering your account, promote that account from the Supabase SQL Editor:
-- update public.profiles set role='admin' where id=(select id from auth.users where email='YOUR_EMAIL@example.com');
-- Never allow ordinary app users to update their own role.
