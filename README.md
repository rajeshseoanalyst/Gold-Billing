# Gold Billing (DigiGlobal)

An Android billing app for gold and silver shops. One admin and many billing users per shop. Many shops can use the same app, and each shop's data stays separate. There is also an owner console for you.

## What it does
- **Four bill types**
  - Sale: a GST tax invoice.
  - Exchange: a sale minus old gold or silver taken in.
  - Purchase voucher: buying old gold or silver from a customer.
  - Estimate: a quotation, which can later be turned into a sale.
- **Item details**: metal, purity (24K/22K/18K, silver 999/925), HSN, HUID, pieces, gross weight, stone weight, net weight, wastage %, rate per gram, making charges (per gram, % or fixed) and stone charges.
- **Old gold and silver**: gross weight, less %, net weight, rate and value.
- **Invoice details**
  - GST is split into CGST + SGST for customers in your state, or IGST for other states.
  - Other parts: discount, round-off, amount in words (lakh/crore), payment mode, amount paid, balance, customer remarks and terms & conditions.
  - The shop's GSTIN, PAN, state and bank/UPI details are printed on every invoice.
- **Customer details**: name, phone, email, address, GSTIN and state. Customers are saved and filled in again by phone number.
- **Sharing**: a PDF invoice that can be sent on WhatsApp straight to the customer's number, by email to the customer's address, or through any other app. It can also be printed.
- **Admin-only home page**
  - Sales, purchases and exchange shown in amounts and in grams of gold and silver.
  - Periods: today, 7 days, this month, 3 months and this year.
  - Shows each billing user's totals.
  - Billing users never see shop totals. They see only the bills they made.
- **Other features from Call CRM**: today's gold and silver rates, team approval with a shop code, logo and photos, the owner console, and Excel reports (7/15 days, 1/3/6/9 months, 1 year).
- **Bill numbers** follow the financial year: `INV/2026-27/0001`, `PUR/…` and `EST/…`. Only the admin can cancel a bill (with a reason). Cancelled bills stay on record and are left out of all totals.

## Setup (uses your existing Firebase project **DigiCallCRM**)
1. **Firebase → Project settings → Your apps → Add app → Android.** For the package name, enter `com.digiglobal.goldbill` and register it.
2. Download the new **google-services.json**. It now contains both apps.
3. **Firestore → Rules**: copy everything from `firebase/firestore.rules` and paste it there.
   - Replace `YOUR-EMAIL@example.com` with your owner email, the same one you use in Call CRM.
   - Click **Publish**.
   - These rules include the Call CRM rules, so Call CRM keeps working.
4. **GitHub**: create a new repository, for example `Gold-Billing`, and upload all files from this folder.
   - The `.github` folder may not upload by dragging. If so, create `.github/workflows/build-apk.yml` with **Add file → Create new file** and paste the contents of `build-apk.yml`.
5. **Settings → Secrets and variables → Actions → New repository secret.**
   - Name: `GOOGLE_SERVICES_JSON`
   - Value: the full text of the new google-services.json
6. Go to **Actions → Build APK → Run workflow**. When it finishes, download the `GoldBilling-apk` file and install it.
7. Sign in with your owner email. The **Owner console** then appears under **More**.

## First use
- The shop owner chooses **Create a shop**. Then they fill in **More → Shop settings** (GSTIN, state, bank, terms) and **Rates**.
- Staff choose **Join a shop** and enter the shop code from **More → Team**. The admin then approves them.
