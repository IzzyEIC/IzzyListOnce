# Plan rozwoju ListOnce

Rynek: **UK**. Tryb bazowy: półautomatyczny (aplikacja przygotowuje treść, użytkowniczka publikuje).

## ✅ Etap 1 — Wersja półautomatyczna (zrobione, v1.x)
- Jedno ogłoszenie → wybór portali: eBay, Vinted, FB Marketplace, lokalne grupy FB, Gumtree,
  Preloved, Shpock, Nextdoor + darmowe (Freegle, Freecycle, Olio) jako opcja.
- Otwieranie portalu, kopiowanie tekstów dopasowanych do portalu, zdjęcia do albumu „ListOnce”.
- Statusy per portal i przypomnienie o usunięciu ogłoszeń po sprzedaży.
- Podpisane APK w GitHub Releases.

## 🔨 Etap 2 — eBay: wystawianie bezpośrednio z aplikacji (w trakcie)
- Serwer pośredniczący (Cloudflare Worker) — logowanie do eBay (OAuth), sekret aplikacji poza telefonem.
- „Połącz z eBay” w Ustawieniach; konfiguracja: zasady (wysyłka/płatność/zwroty), lokalizacja.
- Wystawienie jednym przyciskiem: zdjęcia → kategoria (podpowiedzi eBay) → stan (dozwolone w kategorii)
  → wymagane cechy (marka, rozmiar…) → cena → publikacja → link do aukcji.
- Sprzedaż na innym portalu → automatyczne zakończenie oferty na eBay.
- Sprzedaż na eBay → automatyczne wykrycie i przypomnienie o pozostałych portalach.
- Testy w eBay Sandbox, potem produkcja.

**Wymaga od właścicielki:** konto eBay Developer (App ID, Cert ID, RuName), konto Cloudflare,
włączone Business Policies na koncie sprzedawcy.

## ⏳ Etap 3 — Vinted: szybsze „przekazanie” (bez automatyzacji)
Research (18.10.2026):
- Oficjalne API istnieje tylko dla **Vinted Pro** (firmy, wybrani partnerzy z listy) — prywatna
  sprzedawczyni się nie kwalifikuje.
- Nieoficjalne biblioteki/MCP: prawie wszystkie tylko do wyszukiwania; jedyne „wystawiające” działają
  na skopiowanych ciasteczkach i omijają ochronę anty-botową (DataDome) — łamią regulamin.
- Regulamin UK (od 08.10.2026) zakazuje zewnętrznych narzędzi/botów; od lipca 2026 Vinted blokuje konta
  na 24 h za „automated activity”. Crosslist zamknął Vinted dla nowych klientów.
- Decyzja: **żadnej automatyzacji Vinted** (ryzyko utraty konta). Zamiast tego ulepszony ekran
  przekazania: zdjęcia na górze galerii, kopiowanie pól po kolei (pływający przycisk / powiadomienie
  „następne pole”), podpowiedź ścieżki kategorii Vinted, wklejenie linku do ogłoszenia po publikacji.
- Do sprawdzenia na telefonie: czy aplikacja Vinted przyjmuje udostępnione zdjęcia (share).

## 🔚 Etap ostatni — AI wypełnia ogłoszenie i podpowiada cenę
- „Wypełnij AI”: rozpoznanie przedmiotu ze zdjęć (opcjonalnie kod kreskowy / słowo podpowiedzi).
- Wyszukanie w sieci tego samego produktu, a jeśli brak — podobnych (oznaczone jako „podobne”).
- Wypełnienie wszystkich pól: tytuł (EN, słowa kluczowe), opis, kategoria, marka, rozmiar, kolor, stan.
- Panel cen:
  - **nowy** → ceny w sklepach (Argos, Amazon UK, John Lewis…) z linkami + sugestia,
  - **używany** → znalezione oferty (zakres, typowa cena, linki; rozróżnienie wystawione vs sprzedane)
    + cena nowego dla porównania.
  - Cenę ostatecznie wpisuje użytkowniczka.
- Technicznie: Claude API (wizja + wyszukiwanie w sieci) przez ten sam serwer co eBay;
  skaner kodów kreskowych (Google ML Kit). Szacunkowo ~20–30 p za ogłoszenie.
- Ograniczenie: ceny „sprzedanych” są trudno dostępne (API eBay dla sprzedanych tylko dla wybranych firm).
