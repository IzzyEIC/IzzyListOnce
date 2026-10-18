// ListOnce — serwer pośredniczący do logowania w eBay (OAuth).
//
// Telefon nie może znać sekretu aplikacji eBay (Cert ID), więc wymiana kodu na tokeny
// i ich odświeżanie dzieje się tutaj. Serwer niczego nie zapisuje: tokeny trafiają
// prosto do aplikacji przez link listonce://ebay?...

const SCOPES = [
  "https://api.ebay.com/oauth/api_scope",
  "https://api.ebay.com/oauth/api_scope/sell.inventory",
  "https://api.ebay.com/oauth/api_scope/sell.account",
  "https://api.ebay.com/oauth/api_scope/sell.fulfillment",
];

const HOSTS = {
  sandbox: { auth: "https://auth.sandbox.ebay.com", api: "https://api.sandbox.ebay.com" },
  production: { auth: "https://auth.ebay.com", api: "https://api.ebay.com" },
};

const APP_LINK = "listonce://ebay";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const hosts = HOSTS[env.EBAY_ENV] ?? HOSTS.sandbox;

    try {
      switch (url.pathname) {
        case "/config":
          return json({ env: env.EBAY_ENV ?? "sandbox" });

        case "/ebay/auth/start": {
          const consent = new URL(`${hosts.auth}/oauth2/authorize`);
          consent.searchParams.set("client_id", env.EBAY_CLIENT_ID);
          consent.searchParams.set("redirect_uri", env.EBAY_RUNAME);
          consent.searchParams.set("response_type", "code");
          consent.searchParams.set("scope", SCOPES.join(" "));
          consent.searchParams.set("prompt", "login");
          return Response.redirect(consent.toString(), 302);
        }

        // Adres ustawiony w eBay jako "auth accepted URL" dla RuName.
        case "/ebay/auth/callback": {
          const code = url.searchParams.get("code");
          if (!code) return toApp({ error: "no_code" });
          const tokens = await tokenRequest(env, hosts, {
            grant_type: "authorization_code",
            code,
            redirect_uri: env.EBAY_RUNAME,
          });
          if (tokens.error) return toApp({ error: tokens.error_description ?? tokens.error });
          return toApp({
            access_token: tokens.access_token,
            expires_in: tokens.expires_in,
            refresh_token: tokens.refresh_token,
            refresh_token_expires_in: tokens.refresh_token_expires_in,
            env: env.EBAY_ENV ?? "sandbox",
          });
        }

        // Adres ustawiony w eBay jako "auth declined URL".
        case "/ebay/auth/declined":
          return toApp({ error: "declined" });

        case "/ebay/token/refresh": {
          if (request.method !== "POST") return json({ error: "POST only" }, 405);
          const { refresh_token } = await request.json();
          if (!refresh_token) return json({ error: "missing refresh_token" }, 400);
          const tokens = await tokenRequest(env, hosts, {
            grant_type: "refresh_token",
            refresh_token,
            scope: SCOPES.join(" "),
          });
          return json(tokens, tokens.error ? 400 : 200);
        }

        default:
          return new Response("ListOnce", { status: 404 });
      }
    } catch (e) {
      return json({ error: String(e) }, 500);
    }
  },
};

async function tokenRequest(env, hosts, params) {
  const basic = btoa(`${env.EBAY_CLIENT_ID}:${env.EBAY_CLIENT_SECRET}`);
  const res = await fetch(`${hosts.api}/identity/v1/oauth2/token`, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      Authorization: `Basic ${basic}`,
    },
    body: new URLSearchParams(params),
  });
  return res.json();
}

// Strona, która od razu otwiera aplikację ListOnce (przeglądarki nie zawsze
// przekierowują 302 na własny schemat, więc dajemy też przycisk).
function toApp(params) {
  const link = `${APP_LINK}?${new URLSearchParams(params)}`;
  const ok = !params.error;
  const html = `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>ListOnce</title>
<body style="font-family:sans-serif;text-align:center;padding:48px 16px">
<h2>${ok ? "Połączono z eBay ✓" : "Nie udało się połączyć z eBay"}</h2>
<p><a href="${link}" style="display:inline-block;padding:14px 28px;background:#00897B;color:#fff;border-radius:8px;text-decoration:none">Wróć do ListOnce</a></p>
<script>location.href=${JSON.stringify(link)}</script>`;
  return new Response(html, { headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" } });
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}
