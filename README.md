# EDU: mikroserwisy zamówień w Kubernetes (minikube)

Raport z realizacji projektu szkoleniowego.

## Streszczenie

Celem projektu było przeniesienie systemu mikroserwisowego do obsługi zamówień
(`order-service` + `product-service` + PostgreSQL + RabbitMQ) z Docker Compose
do lokalnego klastra Kubernetes (minikube) oraz potwierdzenie, że po migracji
system działa tak samo jak przed nią.

W ramach projektu:

- przygotowałem obrazy kontenerów dla dwóch serwisów Spring Boot (Java 21),
- opisałem całe środowisko w manifestach Kubernetes (`kubernetes/`),
- wdrożyłem 4 komponenty do klastra minikube (23.09.2026),
- zweryfikowałem działanie API, komunikacji przez RabbitMQ i trwałości danych,
- przetestowałem skalowanie bezstanowego serwisu i balansowanie ruchu przez Service,
- przygotowałem wariant manifestów pod OpenShift (`openshift/`, szczegóły w `openshift/README.md`).

**Wynik:** środowisko działa w klastrze od 23.09.2026. Przy kontroli końcowej
05.10.2026 (po ponownym uruchomieniu węzła minikube) wszystkie pody miały
status `1/1 Running`, oba PVC `Bound`, a API zwracało poprawne odpowiedzi
na danych zapisanych 23.09.2026, czyli dane przetrwały restart klastra.

## Spis treści

1. [Środowisko](#1-środowisko)
2. [Architektura](#2-architektura)
3. [Zrealizowane wdrożenie](#3-zrealizowane-wdrożenie)
4. [Przebieg prac](#4-przebieg-prac)
5. [Wyniki weryfikacji](#5-wyniki-weryfikacji)
6. [Skalowanie i balansowanie ruchu](#6-skalowanie-i-balansowanie-ruchu)
7. [Wnioski](#7-wnioski)
8. [Ograniczenia i rozbieżności](#8-ograniczenia-i-rozbieżności)
- [Załącznik A. Punkt wyjścia: Docker Compose](#załącznik-a-punkt-wyjścia-docker-compose)
- [Załącznik B. Zaimplementowane API](#załącznik-b-zaimplementowane-api)
- [Załącznik C. Struktura projektu](#załącznik-c-struktura-projektu)

---

## 1. Środowisko

| Parametr | Wartość |
|---|---|
| Klaster | minikube, 1 węzeł `minikube` (control-plane) |
| Kubernetes | server `v1.37.0` / client `v1.30.5` |
| Węzeł | `192.168.49.2`, Debian GNU/Linux 12 (bookworm), kernel `6.10.14-linuxkit`, runtime `containerd://2.3.4` |
| Namespace | `default` (utworzony również `ecommerce`, ostatecznie niewykorzystany, pusty) |
| Storage | StorageClass `standard` (provisioner minikube), PVC po 1Gi |
| Sieć podów | `10.244.0.0/16` (adresy przydzielane dynamicznie) |
| Aplikacje | Java 21, Spring Boot 3.3.5, Maven |

## 2. Architektura

Migracja nie zmieniła architektury aplikacji, zmieniła się tylko platforma
uruchomieniowa:

```
klient (curl / Postman)
    |
    v
order-service (8080) -----> product-service (8081)
    |                                |
    +------> RabbitMQ (5672)         |
    |                                v
    +------> PostgreSQL (5432) <-----+
```

- `product-service` zarządza katalogiem produktów i stanami magazynowymi.
- `order-service` tworzy zamówienia, pobiera dane produktów z `product-service`
  przez HTTP, rezerwuje i zwraca stany oraz publikuje zdarzenia
  `OrderCreatedEvent` / `OrderCancelledEvent` do RabbitMQ.
- Oba serwisy korzystają ze wspólnej bazy `ordersdb` (tabele `products`,
  `orders`, `order_items`).

W klastrze serwisy odnajdują się po nazwach DNS obiektów Service
(`postgres`, `rabbitmq`, `product-service`), tak samo jak wcześniej
po nazwach serwisów w `docker-compose.yml`. Dzięki temu kod aplikacji
nie wymagał zmian, a konfiguracja sprowadziła się do zmiennych środowiskowych.

## 3. Zrealizowane wdrożenie

Każdy komponent opisałem osobnym manifestem w katalogu `kubernetes/`
(całość spina `kustomization.yaml`):

| Manifest | Deployment (obraz) | Service | Dodatkowo |
|---|---|---|---|
| `postgres.yaml` | `postgres` (`postgres:16-alpine`) | `postgres:5432` (ClusterIP) | PVC `postgres-pvc` 1Gi → `/var/lib/postgresql/data`; sondy `pg_isready` |
| `rabbitmq.yaml` | `rabbitmq` (`rabbitmq:3-management-alpine`) | `rabbitmq` 5672/15672 (ClusterIP) | PVC `rabbitmq-pvc` 1Gi → `/var/lib/rabbitmq`; sondy `rabbitmq-diagnostics -q ping` |
| `product-service.yaml` | `k8c-edu-product` (`product-service:1.0`, port 8081) | `product-service:8081` | env `DB_*`; sondy `/actuator/health` |
| `order-service.yaml` | `k8c-edu-order` (`order-service:1.0`, port 8080) | `order-service:8080` | env `DB_*`, `RABBITMQ_*`, `PRODUCT_SERVICE_URL=http://product-service:8081`, nazwy exchange/kolejek/kluczy; sondy `/actuator/health` |

Najważniejsze decyzje:

- **Obrazy lokalne.** Obrazy `order-service:1.0` i `product-service:1.0`
  zbudowałem bezpośrednio w minikube (`minikube image build`), bez zewnętrznego
  rejestru, dlatego w manifestach jest `imagePullPolicy: IfNotPresent`.
  Dockerfile są wieloetapowe (Maven → JRE 21), a aplikacja działa jako
  użytkownik nieuprzywilejowany `appuser`.
- **Kolejność wdrożenia.** Najpierw infrastruktura (PostgreSQL, RabbitMQ),
  potem serwisy aplikacyjne. Znaczniki czasu w klastrze potwierdzają tę
  kolejność: `postgres` 10:58:36Z, `rabbitmq` 10:58:52Z,
  `k8c-edu-product` 12:54:46Z, `k8c-edu-order` 12:54:53Z (23.09.2026).
- **Typy Service.** PostgreSQL i RabbitMQ zostały jako `ClusterIP` (dostępne
  tylko wewnątrz klastra). Serwisy aplikacyjne wdrożyłem jako `ClusterIP`,
  a po weryfikacji wewnątrz klastra (23.09.2026, ok. 13:08Z) przełączyłem
  je poleceniem `kubectl patch` na `NodePort`, żeby były osiągalne z hosta
  (`order-service` → `31849`, `product-service` → `30879`). Zmiana nie została
  przeniesiona do plików w repozytorium (patrz rozdział 8).
- **Sondy.** Readiness i liveness skonfigurowałem z zapasem czasu na start
  aplikacji Spring Boot (`initialDelaySeconds` 60/90 s), żeby kubelet nie
  restartował kontenera, zanim aplikacja zdąży się podnieść.
- **Konfiguracja.** Połączenia i nazwy obiektów RabbitMQ przekazywane są przez
  zmienne środowiskowe: exchange `orders.exchange`, kolejki
  `orders.created.queue` / `orders.cancelled.queue`, klucze `order.created` /
  `order.cancelled`. Baza `ordersdb` z `ddl-auto=update`.

## 4. Przebieg prac

1. **Przygotowanie klastra.** Uruchomiłem minikube i sprawdziłem, że węzeł
   ma status `Ready`.
2. **Budowa obrazów.** Zbudowałem obrazy obu serwisów wewnątrz minikube
   (`minikube image build -t product-service:1.0 ./product-service`
   oraz analogicznie dla `order-service`), żeby kubelet widział je bez rejestru.
3. **Manifesty.** Przeniosłem konfigurację z `docker-compose.yml` do czterech
   manifestów: Deployment + Service dla każdego komponentu, a dla komponentów
   stanowych dodatkowo PVC. Healthchecki z Compose zamieniłem na sondy
   readiness/liveness.
4. **Wdrożenie.** Zaaplikowałem manifesty (`kubectl apply`) w kolejności:
   infrastruktura, potem aplikacje.
5. **Kontrola stanu.** Sprawdziłem pody, serwisy, deploymenty i wolumeny
   (`kubectl get pods/svc/deploy/pvc,pv`), a przy problemach ze startem
   analizowałem `describe pod` i `logs`.
6. **Udostępnienie na zewnątrz.** Przełączyłem serwisy aplikacyjne na
   `NodePort` (`kubectl patch`).
7. **Test end-to-end.** Uruchomiłem skrypt `scripts/test-api.sh` na serwisach
   w klastrze (23.09.2026, ok. 13:24Z). Skrypt sprawdza health obu serwisów,
   tworzy produkty i zamówienie, przeprowadza zamówienie przez statusy
   `CONFIRMED` → `COMPLETED`, anuluje drugie zamówienie ze sprawdzeniem zwrotu
   stanu magazynowego i weryfikuje przypadki negatywne (400/404).
8. **Skalowanie.** Przetestowałem skalowanie `k8c-edu-product` 1 → 3 → 1
   (rozdział 6).
9. **Kontrola końcowa.** 05.10.2026 zebrałem aktualny stan klastra
   i ponownie zweryfikowałem API (rozdział 5).

Problemy napotkane po drodze:

- Kontenery `order-service` i `rabbitmq` wielokrotnie restartowały się,
  zanim osiągnęły gotowość. Ślad tego widać w kolumnie `RESTARTS`
  (rozdział 5.1). `order-service` zależy od bazy i brokera, więc do czasu
  ich gotowości jego health-check zwraca `503`. Restarty były diagnozowane
  przez `kubectl describe pod` (zdarzenia sond) i `kubectl logs`. W stanie
  ustalonym wszystkie pody są `1/1 Running`.

## 5. Wyniki weryfikacji

Migawka zebrana 05.10.2026, po ponownym uruchomieniu węzła minikube.
Nazwy podów i adresy IP są dynamiczne, istotne są kolumny `READY`/`STATUS`
oraz same zasoby.

### 5.1. Węzeł i pody

```
$ kubectl get nodes -o wide
NAME       STATUS   ROLES           AGE   VERSION   INTERNAL-IP    OS-IMAGE                         CONTAINER-RUNTIME
minikube   Ready    control-plane   12d   v1.37.0   192.168.49.2   Debian GNU/Linux 12 (bookworm)   containerd://2.3.4

$ kubectl get pods -o wide
NAME                              READY   STATUS    RESTARTS       AGE   IP           NODE
k8c-edu-order-78b6bbd9c5-xfhzz    1/1     Running   68 (3m1s ago)  11d   10.244.0.6   minikube
k8c-edu-product-56bc84bcf-ldfwl   1/1     Running   3 (3m1s ago)   11d   10.244.0.7   minikube
postgres-7df5c79d47-wjwnj         1/1     Running   3 (3m1s ago)   11d   10.244.0.4   minikube
rabbitmq-85dccfdd9d-9rq98         1/1     Running   62 (3m1s ago)  11d   10.244.0.8   minikube
```

| Pod | Rola | Obraz |
|---|---|---|
| `k8c-edu-order-*` | order-service (port 8080) | `order-service:1.0` |
| `k8c-edu-product-*` | product-service (port 8081) | `product-service:1.0` |
| `postgres-*` | PostgreSQL, baza `ordersdb` | `postgres:16-alpine` |
| `rabbitmq-*` | RabbitMQ, AMQP 5672 + UI 15672 | `rabbitmq:3-management-alpine` |

Licznik `RESTARTS` obejmuje restarty z okresu stabilizacji sond (głównie
`order-service` i `rabbitmq`) oraz restarty kontenerów przy każdym zatrzymaniu
i ponownym uruchomieniu węzła minikube. Ostatni z nich (`exitCode 255`,
`reason: Unknown`) wynika z wyłączenia węzła, a nie z błędu aplikacji.

### 5.2. Zachowanie sond po restarcie węzła

Po uruchomieniu węzła zdarzenia klastra pokazały oczekiwaną sekwencję:
w czasie startu aplikacji sondy zwracały `connection refused`, potem
`503` (Spring Boot już działa, ale zależności nie są gotowe), a po
ok. 2–3 minutach wszystkie pody przeszły w stan Ready bez ręcznej interwencji:

```
Warning  Unhealthy  pod/k8c-edu-order-...    Liveness probe failed: ... connect: connection refused
Warning  Unhealthy  pod/k8c-edu-product-...  Readiness probe failed: ... connect: connection refused
Warning  Unhealthy  pod/rabbitmq-...         Readiness probe failed: command timed out: "rabbitmq-diagnostics -q ping" timed out after 10s
Warning  Unhealthy  pod/k8c-edu-order-...    Readiness probe failed: HTTP probe failed with statuscode: 503

$ kubectl wait --for=condition=Ready pod --all --timeout=400s
pod/k8c-edu-order-78b6bbd9c5-xfhzz condition met
pod/k8c-edu-product-56bc84bcf-ldfwl condition met
pod/postgres-7df5c79d47-wjwnj condition met
pod/rabbitmq-85dccfdd9d-9rq98 condition met
```

### 5.3. Serwisy, deploymenty, endpointy

```
$ kubectl get svc
NAME              TYPE        CLUSTER-IP      PORT(S)              AGE
kubernetes        ClusterIP   10.96.0.1       443/TCP              12d
order-service     NodePort    10.98.94.158    8080:31849/TCP       11d
postgres          ClusterIP   10.99.62.151    5432/TCP             11d
product-service   NodePort    10.105.42.51    8081:30879/TCP       11d
rabbitmq          ClusterIP   10.106.60.122   5672/TCP,15672/TCP   11d

$ kubectl get deploy
NAME              READY   UP-TO-DATE   AVAILABLE   AGE
k8c-edu-order     1/1     1            1           11d
k8c-edu-product   1/1     1            1           11d
postgres          1/1     1            1           11d
rabbitmq          1/1     1            1           11d

$ kubectl get endpoints
NAME              ENDPOINTS                          AGE
order-service     10.244.0.6:8080                    11d
postgres          10.244.0.4:5432                    11d
product-service   10.244.0.7:8081                    11d
rabbitmq          10.244.0.8:5672,10.244.0.8:15672   11d
```

### 5.4. Trwałość danych (PVC)

```
$ kubectl get pvc
NAME           STATUS   VOLUME                                     CAPACITY   ACCESS MODES   STORAGECLASS   AGE
postgres-pvc   Bound    pvc-29320479-80ba-47d7-b8ba-cbaf377690c0   1Gi        RWO            standard       11d
rabbitmq-pvc   Bound    pvc-708c4f06-4f3c-4a31-8832-fc8cbac87593   1Gi        RWO            standard       11d
```

Zawartość bazy po restarcie węzła odpowiada dokładnie wynikowi testu
end-to-end z 23.09.2026:

```
$ kubectl exec deploy/postgres -- psql -U postgres -d ordersdb -c "\dt"
 public | order_items | table | postgres
 public | orders      | table | postgres
 public | products    | table | postgres

 id |  name  |  price  | stock
----+--------+---------+-------
  1 | Laptop | 3500.00 |     8
  2 | Mouse  | 1200.00 |    19

  status   | count
-----------+-------
 COMPLETED |     1
 CANCELLED |     1
```

Interpretacja: Laptop 10 − 2 = 8; Mouse 20 − 1 = 19, a zamówienie na 3 sztuki
zostało anulowane i stan wrócił do 19. Dane przetrwały restarty podów
i całego węzła, więc PVC działa zgodnie z założeniem.

### 5.5. RabbitMQ

```
$ kubectl exec deploy/rabbitmq -- rabbitmqctl list_queues name messages consumers
orders.cancelled.queue   0   1
orders.created.queue     0   1

$ kubectl exec deploy/rabbitmq -- rabbitmqctl list_bindings source_name destination_name routing_key
orders.exchange   orders.cancelled.queue   order.cancelled
orders.exchange   orders.created.queue     order.created
```

Exchange `orders.exchange` (typ `direct`) i obie kolejki zostały odtworzone
z wolumenu. Każda kolejka ma podłączonego konsumenta (`order-service`),
a brak zaległych wiadomości oznacza, że zdarzenia zostały odebrane.

### 5.6. API

Weryfikacja przez `kubectl port-forward` do obiektów Service, tylko zapytania
odczytujące, bez zmiany danych:

| Zapytanie | Wynik |
|---|---|
| `GET order-service /actuator/health` | `200`, `UP`; komponenty `db` (PostgreSQL) i `rabbit` (RabbitMQ 3.13.7) `UP` |
| `GET product-service /actuator/health` | `200`, `UP`; komponent `db` `UP` |
| `GET /api/products` | `200`, 2 produkty (Laptop, Mouse) |
| `GET /api/orders` | `200`, zamówienie 1 `COMPLETED` (8200.00), zamówienie 2 `CANCELLED` (3600.00) |
| `GET /api/products/999` | `404` w jednolitym formacie błędu (`"error":"NOT_FOUND"`) |

Przykładowa odpowiedź:

```json
{"id":1,"status":"COMPLETED","totalPrice":8200.00,
 "items":[{"productId":1,"productName":"Laptop","quantity":2,"price":3500.00,"subtotal":7000.00},
          {"productId":2,"productName":"Mouse","quantity":1,"price":1200.00,"subtotal":1200.00}]}
```

### 5.7. Testy jednostkowe

Testy obu modułów uruchomiłem ponownie przy kontroli końcowej
(`mvn test`, Java 21.0.11):

| Klasa testowa | Testy | Błędy |
|---|---|---|
| `ProductControllerTest` | 4 | 0 |
| `ProductServiceTest` | 6 | 0 |
| `OrderControllerTest` | 5 | 0 |
| `OrderServiceTest` | 7 | 0 |
| **Razem** | **22** | **0** |

## 6. Skalowanie i balansowanie ruchu

Skalowanie przetestowałem na bezstanowym `k8c-edu-product` (`kubectl scale`
1 → 3, `kubectl rollout status`). PostgreSQL i RabbitMQ celowo nie były
skalowane: każdy ma jeden PVC `ReadWriteOnce`, więc kolejna replika nie
dostałaby wolumenu.

Zapis z testu:

```
$ kubectl get pods -l app=k8c-edu-product -o wide
NAME                              READY   STATUS    RESTARTS      AGE     IP            NODE
k8c-edu-product-56bc84bcf-ldfwl   1/1     Running   2 (13h ago)   5d10h   10.244.0.7    minikube
k8c-edu-product-56bc84bcf-nwlmd   1/1     Running   0             2m5s    10.244.0.9    minikube
k8c-edu-product-56bc84bcf-sbhz2   1/1     Running   0             2m5s    10.244.0.10   minikube

$ kubectl get endpoints product-service
NAME              ENDPOINTS                                          AGE
product-service   10.244.0.10:8081,10.244.0.7:8081,10.244.0.9:8081   5d10h
```

Wynik: Deployment uruchomił 2 nowe pody w ok. 2 minuty (wliczając
opóźnienie sondy readiness). Service sam dodał trzy endpointy, bez zmian
w manifestach, więc ruch kierowany na `http://product-service:8081`
(tak odwołuje się do niego `order-service`) rozkładał się między trzy pody.
Po teście przywróciłem 1 replikę i tak też jest w kontroli końcowej
(rozdział 5.3).

## 7. Wnioski

- **Deployment / Service.** Deployment pilnuje wymaganej liczby replik, a
  Service daje stabilną nazwę DNS i wirtualny IP, za którymi pody mogą się
  zmieniać. Po restarcie węzła pody dostały nowe adresy IP
  (np. `order-service` 10.244.0.4 → 10.244.0.6), a ClusterIP serwisów się nie zmienił.
- **DNS w klastrze.** Nazwy `postgres`, `rabbitmq` i `product-service` rozwiązują się na
  ClusterIP, więc w manifestach wystarczyły zmienne środowiskowe i nie było
  potrzeby wpisywać adresów IP na sztywno.
- **ClusterIP vs NodePort.** Komponenty wewnętrzne (baza, broker) korzystają z `ClusterIP`.
  API mikroserwisów jest wystawione przez `NodePort`, a do lokalnego debugowania
  użyłem `port-forward`.
- **Sondy.** Readiness decyduje, kiedy pod dostaje ruch, a liveness o restarcie
  zawieszonego kontenera. Aplikacja Javy startuje wolno, dlatego opóźnienia
  sond muszą to uwzględniać. Po restarcie węzła klaster sam doprowadził
  wszystkie pody do stanu Ready (rozdział 5.2).
- **PVC.** Dane bazy i brokera przetrwały restarty podów i całego węzła
  (rozdział 5.4).
- **Load balancing.** Service ma jeden ClusterIP i listę endpointów, po jednym
  na gotowy pod. kube-proxy zamienia ją na reguły iptables/IPVS i rozdziela
  połączenia między pody. Wewnątrz klastra rolę load balancera pełni więc
  zwykły Service, a na zewnątrz NodePort (w OpenShift: Route).
- **Diagnostyka.** Podstawowy cykl analizy problemów to `get pods -o wide` →
  `describe pod` (zdarzenia, sondy) → `logs` → `exec`. Przydaje się też
  `--show-managed-fields`, które pokazuje, kto i kiedy zmienił obiekt
  (`kubectl-client-side-apply` vs `kubectl-patch`).
- **Zarządzanie konfiguracją.** Ręczny `kubectl patch` szybko rozwiązuje problem,
  ale rozjeżdża stan klastra z repozytorium. Zmiany należy wprowadzać
  w manifestach i aplikować ponownie.

## 8. Ograniczenia i rozbieżności

- **Typ Service w repozytorium.** Pliki `kubernetes/order-service.yaml` i
  `kubernetes/product-service.yaml` deklarują `type: ClusterIP`, a w klastrze
  działa `NodePort` (zmiana przez `kubectl patch` 23.09.2026). Ponowne
  `kubectl apply` tych plików przywróciłoby `ClusterIP`.
- Jedna wspólna baza `ordersdb` zamiast podejścia „database per service”.
- Brak uwierzytelniania i autoryzacji.
- Brak osobnego notification-service: konsument zdarzeń (logowanie) działa
  wewnątrz `order-service`.
- Brak transakcji rozproszonych / Sagi: kompensacja stanu magazynowego działa
  w trybie best-effort.
- `ddl-auto=update`: rozwiązanie wyłącznie szkoleniowe.
- Hasła w zmiennych środowiskowych manifestów minikube, bez Secrets/ConfigMap.
  W wariancie OpenShift przeniesione do `Secret`.
- Wariant OpenShift jest przygotowany i zwalidowany lokalnie, ale nie został
  wdrożony na klaster OpenShift (patrz `openshift/README.md`).
- CI/CD nie było objęte zakresem projektu.

---

## Załącznik A. Punkt wyjścia: Docker Compose

Przed migracją system działał w Docker Compose (`docker-compose.yml`):
PostgreSQL 16 z healthcheckiem `pg_isready` i wolumenem `pgdata`, RabbitMQ 3
z panelem management, oba mikroserwisy budowane z lokalnych Dockerfile.
Serwisy odwoływały się do siebie po nazwach (`DB_HOST=postgres`,
`RABBITMQ_HOST=rabbitmq`, `PRODUCT_SERVICE_URL=http://product-service:8081`).
Ta sama konwencja nazw została zachowana w Kubernetes, dlatego przeniesienie
nie wymagało zmian w kodzie.

| Docker Compose | Kubernetes |
|---|---|
| `services.<nazwa>` | Deployment + Service |
| nazwa serwisu w sieci Compose | nazwa DNS obiektu Service |
| `volumes` (named volume) | PersistentVolumeClaim |
| `healthcheck` | `readinessProbe` / `livenessProbe` |
| `ports` | Service `NodePort` / `port-forward` |
| `depends_on` | kolejność wdrożenia + readiness |

## Załącznik B. Zaimplementowane API

### product-service (port 8081)

| Metoda | Ścieżka | Body | Opis |
|---|---|---|---|
| POST | /api/products | `{name, description, price, stock}` | Utworzenie produktu → 201 |
| GET | /api/products | — | Lista produktów |
| GET | /api/products/{id} | — | Produkt po id (404 gdy brak) |
| PUT | /api/products/{id} | `{name, description, price, stock}` | Aktualizacja produktu |
| PATCH | /api/products/{id}/stock | `{quantity}` | Zmiana stanu (+/-); 400 przy wyniku ujemnym |
| DELETE | /api/products/{id} | — | Usunięcie → 204 |

### order-service (port 8080)

| Metoda | Ścieżka | Body | Opis |
|---|---|---|---|
| POST | /api/orders | `{items:[{productId, quantity}]}` | Utworzenie zamówienia → 201, publikacja `OrderCreatedEvent` |
| GET | /api/orders | — | Lista zamówień |
| GET | /api/orders/{id} | — | Zamówienie z pozycjami |
| PATCH | /api/orders/{id}/status | `{status}` | Zmiana statusu (tylko dozwolone przejścia) |
| POST | /api/orders/{id}/cancel | — | Anulowanie: zwrot stanu magazynowego, publikacja `OrderCancelledEvent` |

Dozwolone przejścia statusów: `NEW → CONFIRMED | CANCELLED`,
`CONFIRMED → COMPLETED | CANCELLED`. Pozostałe kończą się błędem `400`.
Cena jest zapisywana w `OrderItem` w momencie złożenia zamówienia.

Błędy zwracane są w jednolitym formacie (rzeczywista odpowiedź z klastra,
05.10.2026):

```json
{
  "timestamp": "2026-10-05T10:14:50.814712735",
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Product with id 999 not found",
  "path": "/api/products/999"
}
```

Scenariusze zweryfikowane skryptem `scripts/test-api.sh`:

| Scenariusz | Oczekiwany wynik |
|---|---|
| Health obu serwisów | `200` |
| Utworzenie produktów Laptop (10 szt.) i Mouse (20 szt.) | `201` |
| Zamówienie 2× Laptop + 1× Mouse | `201`, `totalPrice = 8200.00` |
| `NEW → CONFIRMED → COMPLETED` | `200`, `200` |
| Zamówienie 3× Mouse i anulowanie | `201`, `200`, stan Mouse wraca do wartości sprzed zamówienia |
| Zamówienie ponad stan magazynowy | `400 INSUFFICIENT_STOCK` |
| Niedozwolona zmiana statusu (`→ NEW`) | `400 INVALID_STATUS_TRANSITION` |
| Nieistniejący produkt | `404 NOT_FOUND` |

## Załącznik C. Struktura projektu

```
.
├── docker-compose.yml         # punkt wyjścia (Compose)
├── README.md                  # ten raport
├── kubernetes/                # manifesty wdrożone w minikube
│   ├── postgres.yaml          # PVC + Deployment + Service (postgres:5432)
│   ├── rabbitmq.yaml          # PVC + Deployment + Service (rabbitmq:5672, 15672)
│   ├── product-service.yaml   # Deployment k8c-edu-product + Service product-service:8081
│   ├── order-service.yaml     # Deployment k8c-edu-order + Service order-service:8080
│   └── kustomization.yaml
├── openshift/                 # wariant OpenShift: Secrets + Routes (raport: openshift/README.md)
│   ├── secrets.yaml
│   ├── postgres.yaml
│   ├── rabbitmq.yaml
│   ├── product-service.yaml   # + Route product-service
│   ├── order-service.yaml     # + Route order-service
│   └── kustomization.yaml
├── scripts/
│   └── test-api.sh            # test end-to-end API (PASS/FAIL)
├── product-service/           # Dockerfile, pom.xml, src/
└── order-service/             # Dockerfile, pom.xml, src/
```

Warstwy w serwisach: `controller`, `service`, `repository`, `entity`, `dto`,
`exception` (w `order-service` dodatkowo `config`, `messaging`, `client`).
