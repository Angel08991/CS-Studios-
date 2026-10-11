import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type", "Access-Control-Allow-Methods": "POST, OPTIONS" };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  try {
    const url = Deno.env.get("SUPABASE_URL"), anon = Deno.env.get("SUPABASE_ANON_KEY"), serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY"), stripeKey = Deno.env.get("STRIPE_SECRET_KEY"), appUrl = Deno.env.get("RESOURCEHUB_PUBLIC_URL");
    if (!url || !anon || !serviceKey || !stripeKey || !appUrl) return json({ error: "Configura los secretos de Supabase, Stripe y RESOURCEHUB_PUBLIC_URL." }, 503);
    const authorization = req.headers.get("Authorization");
    if (!authorization) return json({ error: "Debes iniciar sesión." }, 401);
    const userClient = createClient(url, anon, { global: { headers: { Authorization: authorization } }, auth: { persistSession: false } });
    const { data: authData, error: authError } = await userClient.auth.getUser();
    const user = authData.user;
    if (authError || !user) return json({ error: "Sesión no válida." }, 401);
    const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
    const body = await req.json().catch(() => ({}));
    const { data: existing, error: existingError } = await admin.from("seller_accounts").select("owner_id,country,stripe_account_id,charges_enabled,payouts_enabled").eq("owner_id", user.id).maybeSingle();
    if (existingError) throw existingError;

    if (body.action === "status") {
      if (!existing) return json({ connected: false, charges_enabled: false, payouts_enabled: false });
      const resp = await fetch(`https://api.stripe.com/v1/accounts/${existing.stripe_account_id}`, { headers: { Authorization: `Bearer ${stripeKey}` } });
      const acct = await resp.json();
      if (!resp.ok) return json({ error: acct?.error?.message || "No se pudo consultar Stripe." }, 502);
      const update = await admin.from("seller_accounts").update({ charges_enabled: !!acct.charges_enabled, payouts_enabled: !!acct.payouts_enabled, updated_at: new Date().toISOString() }).eq("owner_id", user.id);
      if (update.error) throw update.error;
      return json({ connected: true, country: existing.country, charges_enabled: !!acct.charges_enabled, payouts_enabled: !!acct.payouts_enabled, ready: !!acct.charges_enabled && !!acct.payouts_enabled });
    }

    if (!/^[A-Z]{2}$/.test(String(body.country || ""))) return json({ error: "Selecciona un país válido para la cuenta de pagos." }, 400);
    let accountId = existing?.stripe_account_id;
    if (existing && existing.country !== body.country) return json({ error: "Ya hay una cuenta de pagos creada en otro país. Contacta con soporte para cambiarla." }, 409);
    if (!accountId) {
      const form = new URLSearchParams();
      form.set("type", "express");
      form.set("country", body.country);
      if (user.email) form.set("email", user.email);
      form.set("capabilities[card_payments][requested]", "true");
      form.set("capabilities[transfers][requested]", "true");
      form.set("metadata[supabase_user_id]", user.id);
      const create = await fetch("https://api.stripe.com/v1/accounts", { method: "POST", headers: { Authorization: `Bearer ${stripeKey}`, "Content-Type": "application/x-www-form-urlencoded" }, body: form });
      const account = await create.json();
      if (!create.ok) return json({ error: account?.error?.message || "Stripe no pudo crear la cuenta del vendedor." }, 502);
      accountId = account.id;
      const { error } = await admin.from("seller_accounts").insert({ owner_id: user.id, country: body.country, stripe_account_id: accountId, charges_enabled: !!account.charges_enabled, payouts_enabled: !!account.payouts_enabled });
      if (error) throw error;
    }
    const base = appUrl.replace(/\/$/, "");
    const linkForm = new URLSearchParams();
    linkForm.set("account", accountId!);
    linkForm.set("type", "account_onboarding");
    linkForm.set("refresh_url", `${base}/?seller=refresh`);
    linkForm.set("return_url", `${base}/?seller=return`);
    const linkResponse = await fetch("https://api.stripe.com/v1/account_links", { method: "POST", headers: { Authorization: `Bearer ${stripeKey}`, "Content-Type": "application/x-www-form-urlencoded" }, body: linkForm });
    const link = await linkResponse.json();
    if (!linkResponse.ok) return json({ error: link?.error?.message || "Stripe no pudo crear el enlace de verificación." }, 502);
    return json({ url: link.url, expires_at: link.expires_at });
  } catch (error) {
    console.error(error);
    return json({ error: error instanceof Error ? error.message : "Error interno" }, 500);
  }
});
