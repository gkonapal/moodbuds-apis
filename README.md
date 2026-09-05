# MoodBuds API

Spring Boot API for the existing MySQL `mb` database.

## Implemented admin modules

- Username/password login with BCrypt, JWT bearer tokens and temporary lockout
- Administrator accounts, roles, permission assignment and audit logs
- Categories, subcategories, GST rates, moods, products and coupons
- Product slugs, images, sizes, mood assignments and inventory adjustments
- Quiz paths, questions, options and mood weights
- Public anonymous Mood Quiz sessions with dynamic branching and weighted results
- Public catalog browsing, search, filters, product detail, availability and size charts
- Customer email/password authentication, rotating refresh sessions, profiles and addresses
- Authenticated customer wishlist with optional sizes and transactional move-to-cart
- Authenticated persistent cart with live stock, price refresh, GST totals and validation
- Public coupon discovery, authenticated coupon application and provider-aware checkout preview
- Customer order placement with idempotency, immutable snapshots and customer-owned order history
- Customer lookup and activation status
- Orders with validated status transitions
- Returns with validated review/status transitions
- Dashboard, sales, GST and inventory reports

Payment/shipping providers, provider webhooks, OTP, blog, SEO and guest-cart APIs are intentionally excluded.

## Customer Authentication and Profile API

Password authentication is available now. OTP persistence already exists in the database, but OTP
request/login/reset endpoints remain disabled until an email or SMS delivery provider is selected.

Public authentication endpoints:

- `POST /api/v1/customer/auth/register`
- `POST /api/v1/customer/auth/login`
- `POST /api/v1/customer/auth/refresh`

Customer bearer-token endpoints:

- `POST /api/v1/customer/auth/logout`
- `POST /api/v1/customer/auth/logout-all`
- `GET /api/v1/customer/profile`
- `PATCH /api/v1/customer/profile`
- `PUT /api/v1/customer/profile/password`
- `DELETE /api/v1/customer/profile`
- `GET|POST /api/v1/customer/addresses`
- `GET|PUT|DELETE /api/v1/customer/addresses/{addressId}`
- `PUT /api/v1/customer/addresses/{addressId}/default`

Customer access tokens default to 15 minutes. Refresh tokens default to 30 days, are returned only
to the client, stored as SHA-256 hashes, rotated on refresh, and revoked on logout. Admin and customer
tokens use separate authorization markers and cannot be used across API domains.

## Customer Wishlist API

All wishlist routes require a customer bearer token:

- `GET /api/v1/customer/wishlist`
- `POST /api/v1/customer/wishlist/items`
- `GET /api/v1/customer/wishlist/status?productSlug={slug}&size={optionalSize}`
- `DELETE /api/v1/customer/wishlist/items/{itemId}`
- `DELETE /api/v1/customer/wishlist`
- `POST /api/v1/customer/wishlist/items/{itemId}/move-to-cart`

Wishlist adds are idempotent for the product and optional size. Moving an item to the cart requires
an available configured size, locks stock during validation, snapshots the current valid price, merges
with an existing matching cart item, and removes the wishlist item in the same transaction.

## Customer Cart API

All cart routes require a customer bearer token:

- `GET /api/v1/customer/cart`
- `GET /api/v1/customer/cart/summary`
- `GET /api/v1/customer/cart/count`
- `POST /api/v1/customer/cart/items`
- `PATCH /api/v1/customer/cart/items/{itemId}`
- `DELETE /api/v1/customer/cart/items/{itemId}`
- `DELETE /api/v1/customer/cart`
- `POST /api/v1/customer/cart/validate`

The cart persists across sessions and merges identical product-size combinations. Stock is locked for
mutations, quantities are limited to ten per product-size, and exact stock counts are not exposed.
Responses use current catalog prices and return MRP subtotal, product discount, selling subtotal, GST,
and grand total in paise. Validation refreshes changed price snapshots and reports blocking availability
issues without silently deleting customer items.

## Coupon and Checkout Preview API

Currently active and globally available public coupons can be discovered anonymously:

- `GET /api/v1/coupons`
- `GET /api/v1/coupons/{code}`

These routes require a customer bearer token:

- `GET /api/v1/customer/cart/coupon`
- `POST /api/v1/customer/cart/coupon`
- `POST /api/v1/customer/cart/coupon/validate`
- `DELETE /api/v1/customer/cart/coupon`
- `POST /api/v1/customer/checkout/preview`

Coupon application enforces active dates, minimum order value, percentage caps, global usage and
per-customer usage. Private coupons are not listed publicly but may be applied by exact code. Flat
coupon values are converted from the database's rupee decimal to API paise; all returned totals remain
paise. The current schema has no product/category coupon applicability tables, so coupons apply to the
whole selling subtotal. GST is recalculated after proportionally allocating the coupon discount.

Checkout preview validates an address owned by the customer and revalidates the cart and coupon.
`readyForOrderCreation` reflects local cart/address readiness, while `pendingIntegrations` identifies
shipping and payment work that remains. It does not invoke either provider.

## Customer Order API

All order routes require a customer bearer token:

- `POST /api/v1/customer/orders`
- `GET /api/v1/customer/orders?status={optional}&page=0&size=20`
- `GET /api/v1/customer/orders/{orderNumber}`
- `GET /api/v1/customer/orders/{orderNumber}/cancellation-eligibility`
- `POST /api/v1/customer/orders/{orderNumber}/cancel`

Order placement also requires an `Idempotency-Key` header containing 8–128 characters. Repeating the
same key and request safely returns the original order; reusing it with different address/payment data
returns a conflict. Placement revalidates prices, availability, stock and coupons, freezes the address
and product data as JSON snapshots, reserves stock with inventory logs, records coupon usage and clears
the purchased cart atomically.

Online orders remain `PENDING_PAYMENT` until a captured Razorpay payment is verified and expose
`paymentRequired=true` beforehand. Shipping cost is currently zero and `shippingProviderPending=true`.
COD is rejected until its payment and fulfilment behavior is enabled.

Each order freezes a `paymentExpiresAt` timestamp (15 minutes by default). A scheduler scans expired
unpaid orders every minute, reconciles any open Razorpay attempt first, and cancels only when no captured
payment is found. Cancellation atomically restores reserved stock, writes `RESERVATION_RELEASE` inventory
logs, releases coupon usage, expires open local payment attempts, and records order history. Repeated
cancellation/release processing is idempotent. Customer cancellation currently supports unpaid
`PENDING_PAYMENT` and `PAYMENT_FAILED` orders; paid orders are rejected with
`PAYMENT_REFUND_REQUIRED` until provider refunds are implemented.

## Razorpay Payment API

Customer-owned online orders use these authenticated routes:

- `POST /api/v1/customer/orders/{orderNumber}/payments/razorpay`
- `POST /api/v1/customer/orders/{orderNumber}/payments/razorpay/verify`
- `GET /api/v1/customer/orders/{orderNumber}/payments/razorpay`
- `POST /api/v1/customer/orders/{orderNumber}/payments/razorpay/reconcile`

Initiation requires an 8–128 character `Idempotency-Key`. It creates or reuses a Razorpay order using
only the final amount stored by MoodBuds and returns the public Key ID plus Standard Checkout display
data. Verification checks the Checkout HMAC signature against the stored Razorpay order ID, fetches the
payment from Razorpay, validates order, amount and INR currency, and confirms the MoodBuds order only
when the provider status is `captured`. Reconciliation handles lost browser responses using Razorpay's
server API. Raw provider responses are stored for audit but are not exposed by customer APIs.

Current orders explicitly freeze shipping at zero using `shippingPricingStatus=FINALIZED` and
`shippingPricingSource=FREE_SHIPPING_TEMPORARY`. Payment initiation refuses any future order whose
shipping price is not finalized. The shipping provider phase will replace this source with a selected
provider-rate snapshot before Razorpay order creation.

Required Test Mode configuration:

```powershell
$env:MOODBUDS_RAZORPAY_KEY_ID = '<rzp_test key id>'
$env:MOODBUDS_RAZORPAY_KEY_SECRET = '<test key secret>'
```

Optional display configuration includes `MOODBUDS_RAZORPAY_CHECKOUT_NAME`,
`MOODBUDS_RAZORPAY_CHECKOUT_DESCRIPTION`, `MOODBUDS_RAZORPAY_THEME_COLOR`, and
`MOODBUDS_RAZORPAY_BASE_URL`. Order lifecycle settings are configurable with
`MOODBUDS_PAYMENT_TIMEOUT`, `MOODBUDS_PAYMENT_TIMEOUT_BATCH_SIZE`,
`MOODBUDS_PAYMENT_TIMEOUT_SCAN_INTERVAL`, and `MOODBUDS_PAYMENT_TIMEOUT_SCAN_INITIAL_DELAY`.
Payment webhooks remain deferred; payment reconciliation currently uses the provider API.

## Customer Return API

Returns require a customer bearer token and are available only after delivery:

- `GET /api/v1/customer/orders/{orderNumber}/return-eligibility`
- `POST /api/v1/customer/orders/{orderNumber}/returns`
- `GET /api/v1/customer/returns?status={optional}&page=0&size=20`
- `GET /api/v1/customer/returns/{returnId}`

Return creation requires an 8–128 character `Idempotency-Key`, a customer-owned active pickup
address, a request reason, and one or more order-item quantities. `OTHER` requires a description.
Requests are accepted only when order history contains `DELIVERED`, the product's purchase-time return
window remains open, and the requested quantity does not exceed the quantity not already included in
a non-rejected return. Product return-window days and the pickup address are snapshotted so later
catalog or address changes do not alter the request. Creating the first request transitions a delivered
order to `RETURN_INITIATED`. Refund initiation is a separate admin action after warehouse receipt,
item inspection, and quality-check approval.

## Razorpay Refund API

Warehouse and refund operations require an admin bearer token with `returns.manage` (or a super-admin
role):

- `PATCH /api/v1/admin/returns/{returnId}/items/{returnItemId}/inspection`
- `POST /api/v1/admin/returns/{returnId}/refunds`
- `POST /api/v1/admin/refunds/{refundId}/reconcile`
- `GET /api/v1/admin/refunds?page=0&size=20`
- `GET /api/v1/admin/refunds/{refundId}`

Customers can read only refunds belonging to their own orders:

- `GET /api/v1/customer/refunds?page=0&size=20`
- `GET /api/v1/customer/refunds/{refundId}`

Refund initiation requires a 10–128 character `Idempotency-Key` and accepts `NORMAL` or `OPTIMUM`
speed (default `OPTIMUM`). MoodBuds computes the amount in paise from the returned quantities, allocated
coupon discount, and GST; callers cannot submit an amount. A refund is permitted only after all return
items are inspected and the return reaches `QUALITY_CHECK_PASSED`. Good-condition inventory is restored
once, while damaged, used, or missing-tag items are not returned to sellable stock.

The Razorpay refund ID and provider reference are retained. Pending refunds are reconciled every five
minutes by default, and the manual reconcile endpoint handles immediate status refreshes. Processed
refunds update the payment and order to `PARTIALLY_REFUNDED` or `REFUNDED` and complete the return.
A provider-declared failed refund can be initiated again with a new idempotency key. Refund webhooks are
deferred until the provider-webhook phase.

Optional scheduler settings are `MOODBUDS_REFUND_POLLING_BATCH_SIZE`,
`MOODBUDS_REFUND_POLLING_INTERVAL`, and `MOODBUDS_REFUND_POLLING_INITIAL_DELAY`.

## Public Catalog API

All catalog endpoints are anonymous, return active records only, and express money in paise.

- `GET /api/v1/home?collectionSize=12`
- `GET /api/v1/categories`
- `GET /api/v1/categories/{slug}`
- `GET /api/v1/categories/{slug}/subcategories`
- `GET /api/v1/subcategories/{slug}`
- `GET /api/v1/moods`
- `GET /api/v1/moods/{slug}`
- `GET /api/v1/moods/{slug}/products`
- `GET /api/v1/products` (also `/api/v1/products/search`)
- `GET /api/v1/products/{slug}`
- `GET /api/v1/products/{slug}/availability`
- `GET /api/v1/products/{slug}/size-chart`

Product search accepts `q`, `category`, `subcategory`, `mood`, `productSize`, `color`,
`minPrice`, `maxPrice`, `inStock`, `featured`, `newArrival`, `bestSeller`, `page`, and
`size`. Supported sort values are `newest`, `price-asc`, `price-desc`, `name-asc`, and
`featured`. Public availability intentionally exposes only an in-stock boolean, not stock counts.

## Public Mood Quiz API

The quiz is anonymous and does not require a customer or admin bearer token.

1. `GET /api/v1/quiz/config`
2. `POST /api/v1/quiz/sessions`
3. `GET /api/v1/quiz/sessions/{sessionId}`
4. `GET /api/v1/quiz/sessions/{sessionId}/question`
5. `POST /api/v1/quiz/sessions/{sessionId}/answers`
6. `POST /api/v1/quiz/sessions/{sessionId}/complete`
7. `GET /api/v1/quiz/sessions/{sessionId}/result`

Session creation returns a one-time `sessionToken`. Send it on every subsequent quiz request:

```http
X-Quiz-Session-Token: <session-token>
```

Only a SHA-256 hash of this token is stored. Incomplete sessions expire after 24 hours.
The answer payload is:

```json
{
  "questionId": 1,
  "optionId": 1001
}
```

Q1 selects one of the four paths for Q2-Q5; Q6-Q10 are universal. Revising an earlier
answer discards all later answers so branch and score state cannot become inconsistent.

## Required environment variables

```powershell
$env:MOODBUDS_DB_PASSWORD = '<local database password>'
$env:MOODBUDS_JWT_SECRET = '<random secret containing at least 32 characters>'
```

Optional variables include `MOODBUDS_DB_HOST`, `MOODBUDS_DB_PORT`, `MOODBUDS_DB_NAME`,
`MOODBUDS_DB_USERNAME`, `MOODBUDS_ADMIN_TOKEN_VALIDITY`, `MOODBUDS_CUSTOMER_ACCESS_TOKEN_VALIDITY`,
`MOODBUDS_CUSTOMER_REFRESH_TOKEN_VALIDITY`, and `MOODBUDS_ALLOWED_ORIGINS`.

## Run

```powershell
mvn spring-boot:run
```

Flyway baselines the pre-existing database and then adds `admin_users.username` and the approved slug columns.
It also inserts the `SUPER_ADMIN` role and permission catalogue, but never creates an administrator account.

Swagger UI is available at `http://localhost:8080/swagger-ui.html` after startup.

## Insert the first super admin

Generate a BCrypt password hash without placing the password in source files:

```powershell
$env:MOODBUDS_ADMIN_PASSWORD_TO_HASH = '<chosen admin password>'
mvn -q exec:java -Dexec.mainClass=com.moodbuds.tools.AdminPasswordHashTool
Remove-Item Env:MOODBUDS_ADMIN_PASSWORD_TO_HASH
```

Then insert the account using the generated hash:

```sql
INSERT INTO admin_users
    (username, role_id, email, password_hash, full_name, mobile, is_active,
     require_2fa, failed_attempts, created_at, updated_at)
SELECT
    'owner', id, NULL, '<bcrypt-hash>', 'MoodBuds Owner', NULL, 1,
    0, 0, UTC_TIMESTAMP(), UTC_TIMESTAMP()
FROM admin_roles
WHERE name = 'SUPER_ADMIN';
```

## API conventions

- Base path: `/api/v1/admin`
- Monetary values: paise
- Authentication: `Authorization: Bearer <access-token>`
- Pagination: zero-based `page` and bounded `size`
- Product/category/mood deletion is a soft deactivation
- Error responses use Spring `ProblemDetail`
