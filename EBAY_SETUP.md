# Podłączenie eBay — krok po kroku

Raz trzeba założyć dwa darmowe konta i skopiować kilka wartości. Najpierw robimy wszystko w
**Sandbox** (testowy eBay — nic nie wystawia się naprawdę), potem przełączamy na produkcję.

## 1. Konto eBay Developer
1. Wejdź na https://developer.ebay.com → **Join** → zaloguj się swoim kontem eBay.
   Zatwierdzenie konta zwykle trwa do 1 dnia roboczego.
2. **Application Keys** → wpisz nazwę aplikacji `ListOnce` → utwórz klucze **Sandbox**.
   Zanotuj: **App ID (Client ID)** i **Cert ID (Client Secret)**. Cert ID jest tajny.
3. Przy kluczach produkcyjnych eBay zapyta o *Marketplace Account Deletion*. Wybierz opcję
   zwolnienia (ListOnce nie przechowuje danych innych użytkowników eBay).
4. **User Tokens** → **Get a Token from eBay via Your Application** → **Add eBay Redirect URL**:
   - *Your auth accepted URL*: `https://<adres-serwera>/ebay/auth/callback`
   - *Your auth declined URL*: `https://<adres-serwera>/ebay/auth/declined`
   - *Your privacy policy URL*: link do repo (np. https://github.com/IzzyEIC/IzzyListOnce)
   - Zapisz i zanotuj **RuName** (wygląda jak `Izzy-ListOnce-SBX-abc123-xyz`).
   - Adres serwera poznasz w kroku 2. Ten punkt możesz uzupełnić po nim.
5. Do testów potrzebny jest użytkownik Sandbox: **Sandbox User** → utwórz testowego sprzedawcę.

## 2. Serwer ListOnce (Cloudflare, darmowy)
1. Załóż konto: https://dash.cloudflare.com/sign-up
2. Claude wdroży serwer z folderu `worker/`. Ty logujesz się raz w przeglądarce (`wrangler login`).
3. Do `worker/wrangler.toml` trafiają App ID i RuName. Cert ID ustawiamy jako sekret, więc nie ma go w repo.
4. Adres serwera wygląda tak: `https://listonce-ebay.<twoja-nazwa>.workers.dev`

## 3. W aplikacji
1. **Ustawienia → eBay** → wklej adres serwera → **Połącz z eBay** → zaloguj się (w Sandbox:
   testowym użytkownikiem) → **Agree**. Aplikacja wróci sama.
2. Wpisz kod pocztowy → **Przygotuj konto**. Aplikacja włączy Business Policies, ustawi lokalizację
   i użyje Twoich zasad wysyłki/płatności/zwrotów (a jeśli ich nie masz — utworzy proste:
   Royal Mail 2nd Class £3.50, płatność natychmiastowa, bez zwrotów).
   Zasady możesz potem zmienić w eBay Seller Hub.
3. Przy ogłoszeniu, na karcie eBay: **Wystaw automatycznie na eBay**.
   W Sandbox eBay nie podpowiada kategorii — wpisz w wyszukiwarkę sam numer kategorii
   (np. `11450` — Clothes, Shoes & Accessories → Women's Clothing jest dobrym testem).

## 4. Przejście na prawdziwy eBay
W `worker/wrangler.toml`: `EBAY_ENV = "production"`, klucze produkcyjne, RuName produkcyjny.
Wdrożyć ponownie, a w aplikacji: **Rozłącz eBay** → **Połącz z eBay** → **Przygotuj konto**.
