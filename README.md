# RSS إلى Blogger (Java + GitHub Actions) — تكلفة $0

## الإعداد (مرة واحدة)
1. أنشئ مستودعاً **عاماً** على GitHub وارفع محتويات هذا المجلد.
2. Settings → Pages → Source: `main` / مجلد `/docs`.
3. Settings → Actions → General → Workflow permissions: **Read and write**.
4. Google Cloud Console: فعّل Blogger API v3، أنشئ OAuth client من نوع **Desktop app**، وحوّل OAuth consent screen إلى **In production**.
5. على جهازك (Java 17 + Maven):
   ```
   mvn -q -DskipTests package
   export GOOGLE_CLIENT_ID=... GOOGLE_CLIENT_SECRET=...
   java -cp target/rss-to-blogger.jar app.OAuthSetup
   ```
   وافق في المتصفح، ثم انسخ `GOOGLE_REFRESH_TOKEN` المطبوع.
6. Settings → Secrets and variables → Actions: أضف `GOOGLE_CLIENT_ID` و`GOOGLE_CLIENT_SECRET` و`GOOGLE_REFRESH_TOKEN`.
7. عدّل `data/sources.json` (ضع روابط RSS وفعّلها) و`data/settings.json`.
8. Actions → Sync RSS to Blogger → Run workflow.

## الإعدادات (`data/settings.json`)
- `target`: `test` أو `production` (الروابط في `targets`).
- `publishMode`: `draft` (مسودات) أو `live` (نشر مباشر). ابدأ بـ draft.
- `maxPostsPerRun`: الحد الأقصى للنشر في كل تشغيل؛ الباقي يبقى «قيد الانتظار».
- `blogId`: اختياري، يتجاوز التحديد التلقائي من الرابط.

## ملاحظات
- الأسرار لا تظهر في الواجهة ولا في المستودع، والواجهة تقرأ `docs/data/status.json` فقط.
- المحاولة تتكرر حتى 3 مرات لكل كتاب ثم يصبح «فشل».
