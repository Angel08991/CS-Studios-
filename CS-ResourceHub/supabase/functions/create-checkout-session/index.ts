import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const stripeKey = Deno.env.get("STRIPE_SECRET_KEY");
    const appUrl = Deno.env.get("RESOURCEHUB_PUBLIC_URL");
    if (!supabaseUrl || !anonKey || !serviceKey || !stripeKey || !appUrl) return json({ error: "Pago no configurado. Define las variables secretas de Supabase, Stripe y RESOURCEHUB_PUBLIC_URL." }, 503);
    const authorization = req.headers.get("Authorization");
    if (!authorization) return json({ error: "Debes iniciar sesión." }, 401);
    const userClient = createClient(supabaseUrl, anonKey, { global: { headers: { Authorization: authorization } }, auth: { persistSession: false } });
    const { data: userData, error: userError } = await userClient.auth.getUser();
    if (userError || !userData.user) return json({ error: "Sesión no válida." }, 401);
    const { resource_id } = await req.json();
    if (typeof resource_id !== "string") return json({ error: "resource_id inválido." }, 400);
    const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });
    const { data: resource, error: resourceError } = await admin.from("resources").select("id,title,owner_id,status,price_cents,currency").eq("id", resource_id).single();
    if (resourceError || !resource || resource.status !== "approved") return json({ error: "El recurso no está disponible." }, 404);
    if (resource.owner_id === userData.user.id) return json({ error: "No puedes comprar tu propio recurso." }, 400);
    if (!resource.price_cents || resource.price_cents < 100) return json({ error: "Este recurso no tiene un precio premium válido." }, 400);
    const { data: oldPurchase } = await admin.from("purchases").select("status").eq("buyer_id", userData.user.id).eq("resource_id", resource.id).maybeSingle();
    if (oldPurchase?.status === "paid") return json({ error: "Ya tienes este recurso." }, 409);
    const { data: seller, error: sellerError } = await admin.from("seller_accounts").select("stripe_account_id,charges_enabled,payouts_enabled").eq("owner_id", resource.owner_id).maybeSingle();
    if (sellerError || !seller?.stripe_account_id) return json({ error: "El creador aún no ha activado su cuenta de pagos." }, 409);
    const accountResponse = await fetch(`https://api.stripe.com/v1/accounts/${seller.stripe_account_id}`, { headers: { Authorization: `Bearer ${stripeKey}` } });
    const account = await accountResponse.json();
    if (!accountResponse.ok) return json({ error: account?.error?.message || "No se pudo comprobar la cuenta de pagos del creador." }, 502);
    if (!account.charges_enabled || !account.payouts_enabled) return json({ error: "El creador debe completar la verificación de Stripe antes de vender." }, 409);

    // Create/update a pending order before calling Stripe. This avoids losing a very fast
    // webhook if Stripe delivers it before the function stores the Checkout Session ID.
    const { error: pendingError } = await admin.from("purchases").upsert({ buyer_id: userData.user.id, resource_id: resource.id, stripe_session_id: null, amount_cents: resource.price_cents, currency: resource.currency || "EUR", status: "pending", paid_at: null }, { onConflict: "buyer_id,resource_id" });
    if (pendingError) return json({ error: "No se pudo preparar la compra." }, 500);
    const successUrl = `${appUrl.replace(/\/$/, "")}/?payment=success`;
    const cancelUrl = `${appUrl.replace(/\/$/, "")}/?payment=cancel`;
    const form = new URLSearchParams();
    form.set("mode", "payment");
    form.set("success_url", successUrl);
    form.set("cancel_url", cancelUrl);
    form.set("client_reference_id", `${userData.user.id}:${resource.id}`);
    form.set("customer_email", userData.user.email ?? "");
    form.set("line_items[0][quantity]", "1");
    form.set("line_items[0][price_data][currency]", String(resource.currency || "EUR").toLowerCase());
    form.set("line_items[0][price_data][unit_amount]", String(resource.price_cents));
    form.set("line_items[0][price_data][product_data][name]", String(resource.title).slice(0, 120));
    form.set("line_items[0][price_data][product_data][description]", "Descarga digital · CS ResourceHub");
    form.set("metadata[buyer_id]", userData.user.id);
    form.set("metadata[resource_id]", resource.id);
    form.set("metadata[amount_cents]", String(resource.price_cents));
    form.set("payment_intent_data[metadata][buyer_id]", userData.user.id);
    form.set("payment_intent_data[metadata][resource_id]", resource.id);
    form.set("payment_intent_data[transfer_data][destination]", seller.stripe_account_id);
    // Marketplace commission: 10%. Review this fee and all legal/tax duties before a public launch.
    form.set("payment_intent_data[application_fee_amount]", String(Math.floor(resource.price_cents * 0.10)));
    const stripeResponse = await fetch("https://api.stripe.com/v1/checkout/sessions", {
      method: "POST",
      headers: { Authorization: `Bearer ${stripeKey}`, "Content-Type": "application/x-www-form-urlencoded" },
      body: form,
    });
    const checkout = await stripeResponse.json();
    if (!stripeResponse.ok) {
      await admin.from("purchases").update({ status: "failed" }).eq("buyer_id", userData.user.id).eq("resource_id", resource.id).eq("status", "pending");
      return json({ error: checkout?.error?.message || "Stripe no pudo crear el pago." }, 502);
    }
    const { error: purchaseError } = await admin.from("purchases").update({ stripe_session_id: checkout.id }).eq("buyer_id", userData.user.id).eq("resource_id", resource.id);
    if (purchaseError) return json({ error: "No se pudo vincular la sesión de pago." }, 500);
    return json({ url: checkout.url });
  } catch (error) {
    console.error(error);
    return json({ error: error instanceof Error ? error.message : "Error interno" }, 500);
  }
});
