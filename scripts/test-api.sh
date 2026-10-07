#!/usr/bin/env bash
# End-to-end weryfikacja API mikroserwisów.
# Uruchomienie jedną komendą (z korzenia projektu):
#   bash scripts/test-api.sh
# Wymagane: uruchomiony stos (docker compose up), curl, python3.

PRODUCTS_URL="${PRODUCTS_URL:-http://127.0.0.1:61655}"
ORDERS_URL="${ORDERS_URL:-http://127.0.0.1:61650}"

PASS=0
FAIL=0

ok()   { PASS=$((PASS + 1)); echo "PASS: $1"; }
fail() { FAIL=$((FAIL + 1)); echo "FAIL: $1 (szczegóły wyżej)"; }

# Żądanie GET, wypisuje body; kod odpowiedzi odkłada w HTTP_CODE
get() {
  HTTP_BODY=$(curl -s -w '\n%{http_code}' "$1")
  HTTP_CODE=$(echo "$HTTP_BODY" | tail -1)
  HTTP_BODY=$(echo "$HTTP_BODY" | sed '$d')
}

# POST/PATCH/PUT z body JSON
send() { # method url json
  HTTP_BODY=$(curl -s -w '\n%{http_code}' -X "$1" "$2" \
    -H 'Content-Type: application/json' -d "$3")
  HTTP_CODE=$(echo "$HTTP_BODY" | tail -1)
  HTTP_BODY=$(echo "$HTTP_BODY" | sed '$d')
}

json_field() { # field — wyciągnięcie pola z $HTTP_BODY przez python3
  echo "$HTTP_BODY" | python3 -c "import json,sys; print(json.load(sys.stdin)$1)"
}

echo "=== 1. Health ==="
get "$PRODUCTS_URL/actuator/health"
[ "$HTTP_CODE" = "200" ] && ok "product-service health UP" || fail "product-service health"
get "$ORDERS_URL/actuator/health"
[ "$HTTP_CODE" = "200" ] && ok "order-service health UP" || fail "order-service health"

echo "=== 2. Create products ==="
send POST "$PRODUCTS_URL/api/products" \
  '{"name":"Laptop","description":"Business laptop","price":3500.00,"stock":10}'
P1=$(json_field "['id']" 2>/dev/null)
[ "$HTTP_CODE" = "201" ] && [ -n "$P1" ] && ok "create Laptop (id=$P1)" || fail "create Laptop"

send POST "$PRODUCTS_URL/api/products" \
  '{"name":"Mouse","description":"Wireless mouse","price":1200.00,"stock":20}'
P2=$(json_field "['id']" 2>/dev/null)
[ "$HTTP_CODE" = "201" ] && [ -n "$P2" ] && ok "create Mouse (id=$P2)" || fail "create Mouse"

echo "=== 3. Create order ==="
send POST "$ORDERS_URL/api/orders" \
  "{\"items\":[{\"productId\":$P1,\"quantity\":2},{\"productId\":$P2,\"quantity\":1}]}"
OID=$(json_field "['id']" 2>/dev/null)
TOTAL=$(json_field "['totalPrice']" 2>/dev/null)
[ "$HTTP_CODE" = "201" ] && [ "$TOTAL" = "8200.0" ] \
  && ok "create order (id=$OID, total=$TOTAL)" || fail "create order"

echo "=== 4. Confirm & complete ==="
send PATCH "$ORDERS_URL/api/orders/$OID/status" '{"status":"CONFIRMED"}'
[ "$HTTP_CODE" = "200" ] && ok "confirm order" || fail "confirm order"
send PATCH "$ORDERS_URL/api/orders/$OID/status" '{"status":"COMPLETED"}'
[ "$HTTP_CODE" = "200" ] && ok "complete order" || fail "complete order"

echo "=== 5. Scenariusz anulowania (stan wraca) ==="
get "$PRODUCTS_URL/api/products/$P2"
STOCK_BEFORE=$(json_field "['stock']" 2>/dev/null)
send POST "$ORDERS_URL/api/orders" "{\"items\":[{\"productId\":$P2,\"quantity\":3}]}"
OID2=$(json_field "['id']" 2>/dev/null)
[ "$HTTP_CODE" = "201" ] && ok "create order for cancel (id=$OID2)" || fail "create order for cancel"
send POST "$ORDERS_URL/api/orders/$OID2/cancel" ''
[ "$HTTP_CODE" = "200" ] && ok "cancel order" || fail "cancel order"
get "$PRODUCTS_URL/api/products/$P2"
STOCK_AFTER=$(json_field "['stock']" 2>/dev/null)
[ "$STOCK_AFTER" = "$STOCK_BEFORE" ] \
  && ok "stock restored ($STOCK_BEFORE -> $STOCK_AFTER)" \
  || fail "stock restored (before=$STOCK_BEFORE after=$STOCK_AFTER)"

echo "=== 6. Przypadki negatywne (oczekujemy 400/404) ==="
send POST "$ORDERS_URL/api/orders" "{\"items\":[{\"productId\":$P1,\"quantity\":100}]}"
[ "$HTTP_CODE" = "400" ] && ok "insufficient stock -> 400" || fail "insufficient stock"
send PATCH "$ORDERS_URL/api/orders/$OID/status" '{"status":"NEW"}'
[ "$HTTP_CODE" = "400" ] && ok "invalid transition -> 400" || fail "invalid transition"
get "$PRODUCTS_URL/api/products/999999"
[ "$HTTP_CODE" = "404" ] && ok "missing product -> 404" || fail "missing product"

echo ""
echo "==============================="
echo "Wynik: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ] && exit 0 || exit 1
