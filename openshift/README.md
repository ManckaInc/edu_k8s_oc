# EDU: wariant wdrożenia pod OpenShift

Raport z przygotowania wersji manifestów pod platformę OpenShift.

## Streszczenie

Jako rozszerzenie projektu (raport główny: `../README.md`) przygotowałem
wariant manifestów z katalogu `kubernetes/` dostosowany do OpenShift.
Logika działania systemu pozostała bez zmian. Dodałem mechanizmy typowe dla
OpenShift i dobrych praktyk:

- **Secret**: hasła do bazy i brokera przeniesione z jawnych zmiennych
  środowiskowych do obiektów `Secret`,
- **Route**: zewnętrzny dostęp do API przez Route z terminacją TLS (edge)
  zamiast `NodePort`,
- **Kustomize**: całość wdrażana jedną komendą (`oc apply -k openshift/`).

**Status:** manifesty są przygotowane i zwalidowane lokalnie (05.10.2026),
ale nie zostały wdrożone na klaster OpenShift. W ramach projektu nie miałem
dostępu do klastra OpenShift (CRC / Developer Sandbox). Działające
środowisko referencyjne to klaster minikube opisany w raporcie głównym.

## 1. Zawartość katalogu

| Plik | Zawartość |
|---|---|
| `secrets.yaml` | `Secret/postgres-credentials` (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`), `Secret/rabbitmq-credentials` (`RABBITMQ_DEFAULT_USER`, `RABBITMQ_DEFAULT_PASS`); wartości szkoleniowe w `stringData` |
| `postgres.yaml` | PVC + Deployment + Service `ClusterIP`; dane logowania z Secret |
| `rabbitmq.yaml` | PVC + Deployment + Service `ClusterIP`; login/hasło z Secret |
| `product-service.yaml` | Deployment + Service `ClusterIP` + **Route** `product-service` |
| `order-service.yaml` | Deployment + Service `ClusterIP` + **Route** `order-service` |
| `kustomization.yaml` | lista zasobów w kolejności: Secrets → infrastruktura → aplikacje |

## 2. Różnice względem wersji minikube

| Obszar | minikube (`kubernetes/`) | OpenShift (`openshift/`) |
|---|---|---|
| Izolacja | namespace `default` | własny Project (np. `edu`) |
| Dostęp z zewnątrz | Service `NodePort` + `port-forward` | Service `ClusterIP` + Route (HTTPS, edge, przekierowanie HTTP → HTTPS) |
| Hasła | jawne wartości w `env` | `Secret` + `valueFrom.secretKeyRef` |
| Obrazy | `minikube image build` (lokalnie w węźle) | internal registry albo budowa w klastrze (`oc new-build`, BuildConfig + ImageStream) |
| Bezpieczeństwo | brak ograniczeń | SCC `restricted`: kontenery nie mogą działać jako root |
| CLI | `kubectl` | `oc` (te same `get/describe/logs/exec` + `oc status`, `oc get route`) |

Route w wersji wygenerowanej przez kustomize:

```yaml
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: order-service
spec:
  port:
    targetPort: 8080
  tls:
    insecureEdgeTerminationPolicy: Redirect
    termination: edge
  to:
    kind: Service
    name: order-service
```

`product-service` ma analogiczny Route na porcie `8081`.

## 3. Walidacja przeprowadzona lokalnie

Bez klastra OpenShift sprawdziłem wszystko, co da się zweryfikować na
dostępnej infrastrukturze (`oc` client `4.22.14`, kustomize `v5.7.1`,
API server minikube `v1.37.0`).

**Budowanie kustomize.** `kubectl kustomize openshift/` generuje bez błędów
14 obiektów: 2 × Secret, 4 × Service, 2 × PersistentVolumeClaim,
4 × Deployment, 2 × Route.

**Walidacja schematu po stronie serwera.** Wszystkie obiekty poza Route
(to CRD specyficzne dla OpenShift, nieobecne w czystym Kubernetes) przeszły
`kubectl apply --dry-run=server` na API serverze minikube. Tryb dry-run
nie wprowadza żadnych zmian w klastrze:

```
secret/postgres-credentials created (server dry run)
secret/rabbitmq-credentials created (server dry run)
service/order-service configured (server dry run)
service/postgres unchanged (server dry run)
service/product-service configured (server dry run)
service/rabbitmq unchanged (server dry run)
persistentvolumeclaim/postgres-pvc unchanged (server dry run)
persistentvolumeclaim/rabbitmq-pvc unchanged (server dry run)
deployment.apps/k8c-edu-order configured (server dry run)
deployment.apps/k8c-edu-product configured (server dry run)
deployment.apps/postgres configured (server dry run)
deployment.apps/rabbitmq configured (server dry run)
```

Interpretacja:

- `unchanged`: obiekt jest identyczny jak w wersji minikube (PVC, Service bazy i brokera).
- `configured`: różnice zamierzone, czyli Deploymenty pobierające hasła
  z Secret oraz Service aplikacji jako `ClusterIP` zamiast `NodePort`.
- `created`: nowe obiekty Secret.

**Route.** Ze względu na brak CRD `route.openshift.io` w minikube Route
zweryfikowałem tylko na poziomie wygenerowanego YAML: poprawne `apiVersion`,
odwołanie do istniejących Service i portów, konfiguracja TLS edge.

## 4. Analiza ryzyk wdrożenia na OpenShift

W trakcie przygotowania zidentyfikowałem kwestie, które trzeba rozwiązać przy
rzeczywistym wdrożeniu:

1. **Dostarczenie obrazów.** Manifesty odwołują się do lokalnych obrazów
   `order-service:1.0` i `product-service:1.0`, które w OpenShift nie
   istnieją. Przewidziane są dwa rozwiązania:
   - push do internal registry projektu i podmiana `image` na pełną ścieżkę
     (`image-registry.openshift-image-registry.svc:5000/edu/<serwis>:1.0`),
   - budowa w klastrze z istniejących Dockerfile
     (`oc new-build --binary --strategy=docker` + `oc start-build --from-dir`)
     i wskazanie obrazu z ImageStream.

   Bez tego kroku pody aplikacji skończyłyby w stanie `ImagePullBackOff`.
2. **SCC `restricted`.** Obrazy mikroserwisów są na to przygotowane:
   Dockerfile kończą się `USER appuser`, więc aplikacja nie działa jako root.
   Ryzyko dotyczy obrazów `postgres:16-alpine` i
   `rabbitmq:3-management-alpine`, które mogą wymagać konkretnego UID.
   Na lokalnym CRC można to obejść przez SCC `anyuid` dla konta `default`.
   W środowisku współdzielonym lepiej użyć obrazów przygotowanych pod OpenShift
   (np. Red Hat / Bitnami).
3. **Sekrety w repozytorium.** `secrets.yaml` zawiera wartości szkoleniowe
   w `stringData`. W środowisku nieszkoleniowym Secret powinien być tworzony
   poza repozytorium (`oc create secret generic ... --from-literal=...`)
   albo zarządzany przez narzędzie typu Sealed Secrets / Vault.

## 5. Oczekiwany rezultat wdrożenia

Po rozwiązaniu punktu 4.1 wdrożenie (`oc new-project edu`,
`oc apply -k openshift/`) powinno dać ten sam stan co w minikube:
4 pody `Running`, 2 PVC `Bound`, a dodatkowo dwa Route z adresami
`order-service-edu.apps.<domena-klastra>` i
`product-service-edu.apps.<domena-klastra>`. Weryfikacja API byłaby
identyczna jak w raporcie głównym, z tą różnicą, że przez `https://` i adres
Route. Służy do tego ten sam skrypt `scripts/test-api.sh` ze zmiennymi
`ORDERS_URL` / `PRODUCTS_URL`.

## 6. Wnioski

- Przejście z czystego Kubernetes na OpenShift wymaga niewielu zmian
  w manifestach: Deployment, Service i PVC są wspólne, dochodzą Route
  i (opcjonalnie) BuildConfig/ImageStream.
- Największe różnice dotyczą obszarów okołowdrożeniowych: skąd klaster bierze
  obrazy (registry/S2I zamiast lokalnego builda) i z jakimi uprawnieniami
  działają kontenery (SCC).
- Przeniesienie haseł do Secret i wystawienie API przez Route z TLS to
  zmiany, które warto przenieść także do wersji minikube.
