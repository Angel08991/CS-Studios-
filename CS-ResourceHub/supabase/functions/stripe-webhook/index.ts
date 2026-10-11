import Stripe from "npm:stripe@17.7.0";
import { createClient } from "npm:@supabase/supabase-js@2";

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const stripeKey = Deno.env.get("STRIPE_SECRET_KEY");
  const webhookSecret = Deno.env.get("STRIPE_WEBHOOK_SECRET");
  const url = Deno.env.get("SUPABASE_URL");
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!stripeKey || !webhookSecret || !url || !serviceRole) return new Response("Webhook not configured", { status: 503 });
  const signature = req.headers.get("stripe-signature");
  if (!signature) return new Response("Missing signature", { status: 400 });
  const stripe = new Stripe(stripeKey, { httpClient: Stripe.createFetchHttpClient() });
  let event: Stripe.Event;
  try {
    const payload = await req.text();
    event = await stripe.webhooks.constructEventAsync(payload, signature, webhookSecret, undefined, Stripe.createSubtleCryptoProvider());
  } catch (err) {
    console.error("Invalid Stripe webhook signature", err);
    return new Response("Invalid signature", { status: 400 });
  }
  const admin = createClient(url, serviceRole, { auth: { persistSession: false } });
  if (event.type === "account.updated") {
    const account = event.data.object as Stripe.Account;
    const ownerId = account.metadata?.supabase_user_id;
    if (ownerId) {
      const { error } = await admin.from("seller_accounts").update({ charges_enabled: account.charges_enabled, payouts_enabled: account.payouts_enabled, updated_at: new Date().toISOString() }).eq("owner_id", ownerId).eq("stripe_account_id", account.id);
      if (error) { console.error("Could not sync seller account", error); return new Response("Database update failed", { status: 500 }); }
    }
  } else if (["checkout.session.completed", "checkout.session.async_payment_succeeded"].includes(event.type)) {
    const checkout = event.data.object as Stripe.Checkout.Session;
    if (checkout.payment_status === "paid" || event.type === "checkout.session.async_payment_succeeded") {
      const buyerId = checkout.metadata?.buyer_id;
      const resourceId = checkout.metadata?.resource_id;
      const amountMetadata = Number(checkout.metadata?.amount_cents || -1);
      if (!buyerId || !resourceId || amountMetadata < 0 || checkout.amount_total !== amountMetadata) return new Response("Invalid purchase metadata", { status: 400 });
      const { error } = await admin.from("purchases").upsert({ buyer_id: buyerId, resource_id: resourceId, stripe_session_id: checkout.id, amount_cents: checkout.amount_total ?? amountMetadata, currency: (checkout.currency || "eur").toUpperCase(), status: "paid", paid_at: new Date().toISOString() }, { onConflict: "buyer_id,resource_id" });
      if (error) {
        console.error("Could not mark purchase paid", error);
        return new Response("Database update failed", { status: 500 });
      }
    }
  } else if (event.type === "checkout.session.expired") {
    const checkout = event.data.object as Stripe.Checkout.Session;
    await admin.from("purchases").update({ status: "failed" }).eq("stripe_session_id", checkout.id).eq("status", "pending");
  } else if (event.type === "charge.refunded") {
    const charge = event.data.object as Stripe.Charge;
    const paymentIntentId = typeof charge.payment_intent === "string" ? charge.payment_intent : null;
    if (paymentIntentId) {
      // Mark the purchase refunded only when a server-side lookup confirms its session.
      const sessions = await stripe.checkout.sessions.list({ payment_intent: paymentIntentId, limit: 1 });
      const sessionId = sessions.data[0]?.id;
      if (sessionId) await admin.from("purchases").update({ status: "refunded" }).eq("stripe_session_id", sessionId);
    }
  }
  return new Response(JSON.stringify({ received: true }), { status: 200, headers: { "Content-Type": "application/json" } });
});
