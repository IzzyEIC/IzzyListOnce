# ListOnce

Aplikacja Android: wpisujesz ogłoszenie **raz**, a ListOnce przeprowadza Cię przez wystawienie go
na wszystkich wybranych portalach z używanymi rzeczami w UK.

## Portale

| Grupa | Portale |
|---|---|
| Sprzedaż | eBay, Vinted, Facebook Marketplace, Gumtree, Preloved, Shpock |
| Lokalne | Lokalne grupy na Facebooku (linki dodajesz w Ustawieniach), Nextdoor |
| Za darmo (opcjonalnie) | Freegle, Freecycle, Olio |

## Jak to działa (tryb półautomatyczny)

Żaden z tych portali (poza eBay) nie ma publicznego API dla prywatnych sprzedawców, a boty
wypełniające formularze łamią regulaminy (np. Vinted banuje za to konta). Dlatego ListOnce:

1. Zbiera ogłoszenie raz: zdjęcia, tytuł, opis, cenę, stan, markę/rozmiar, dostawę i okolicę.
2. Pozwala zaznaczyć, na które portale ma trafić.
3. Dla każdego portalu daje kartę z przyciskami:
   - **Otwórz** aplikację portalu (albo stronę „nowe ogłoszenie”, jeśli aplikacji nie ma),
   - **Kopiuj** tytuł / opis / cenę, dopasowane do portalu (np. tytuł obcięty do 80 znaków na eBay),
     albo cały post dla grup FB, Nextdoor i portali z darmowymi rzeczami,
   - **Wyślij zdjęcia**, czyli udostępnienie zdjęć prosto do aplikacji portalu.
4. Zapisuje zdjęcia w albumie `Pictures/ListOnce`, żeby były na górze galerii w każdym portalu.
5. Śledzi status na każdym portalu: wystawione / sprzedane / usunięte. Gdy coś się sprzeda,
   pokazuje, z których portali trzeba jeszcze zdjąć ogłoszenie.
6. Przycisk „Nie sprzedało się? Oddaj za darmo” dodaje Freegle, Freecycle i Olio.

## Budowanie

- **Bez instalowania czegokolwiek:** każdy push na `main` buduje APK w GitHub Actions
  (zakładka *Actions* → ostatni build → artefakt `ListOnce-debug-apk`).
- **Lokalnie:** otwórz folder w Android Studio i uruchom `app`.

Wymagania: Android 10+ (minSdk 29).

## Dalsze plany

- eBay: pełna automatyzacja przez oficjalne Sell/Inventory API (wymaga konta eBay Developer).
- Ewentualnie Depop, Etsy, Vestiaire Collective. Dodanie portalu to jeden wpis w `Platform.kt`.
