# تطبيق Arab Media Platform News - Android WebView

هذا مشروع Android Studio أولي يفتح موقع الأخبار الحالي داخل التطبيق:
https://news.arabmediaplatform.com/

## ما يحتويه
- WebView لعرض موقع WordPress الحالي.
- JavaScript وDOM Storage لتوافق أفضل مع القوالب ومشغلات الوسائط.
- زر الرجوع يتنقل داخل سجل صفحات الموقع.
- شريط تقدم أثناء تحميل الصفحة.
- إبقاء روابط نطاق news.arabmediaplatform.com داخل التطبيق وفتح الروابط الخارجية في المتصفح.
- لا توجد خدمة إشعارات ولا قاعدة بيانات إضافية.

## بناء APK
1. نزّل وثبّت Android Studio من الموقع الرسمي: https://developer.android.com/studio
2. فك ضغط المشروع وافتح مجلد `AMPNewsAndroid` من Android Studio.
3. وافق على تثبيت Android SDK Platform 35 وأدوات Gradle إذا طلبها البرنامج.
4. انتظر انتهاء Gradle Sync.
5. من القائمة اختر Build > Build APK(s).
6. بعد نجاح البناء، اختر Locate لفتح مجلد الملف. عادة يكون في:
   `app/build/outputs/apk/debug/app-debug.apk`

ملف debug APK مناسب للتجربة فقط. إذا أردت توزيعًا عامًا، أنشئ نسخة موقعة من Build > Generate Signed Bundle / APK واحفظ مفتاح التوقيع في مكان آمن.

## ملاحظات مهمة
- هذا مصدر مشروع Android، وليس ملف APK مبنيًا مسبقًا.
- لم يتم بناء المشروع أو اختباره على جهاز Android في بيئة إعداد الملفات الحالية، إذ لا تتوفر فيها Android SDK/Gradle.
- ينبغي اختبار تسجيل الدخول إن وجد، والراديو، وروابط التحميل، وتشغيل الفيديو، وأحجام الشاشات على أجهزة فعلية.
- الروابط الخارجية تُفتح في المتصفح. قد تحتاج بعض الخدمات التي تتطلب نوافذ منبثقة أو تحميل ملفات إلى دعم إضافي.
- هذا المشروع لا ينشئ تطبيق iOS. لأجهزة iPhone يمكن تجهيز PWA منفصلًا، أو بناء تطبيق iOS ضمن بيئة macOS/Xcode.

## بناء APK بدون Android Studio (باستخدام GitHub Actions)
هذا المشروع يحتوي على ملف إعداد تلقائي داخل `.github/workflows/build-apk.yml`.
1. أنشئ مستودعًا جديدًا على GitHub باسم `AMPNewsAndroid`.
2. ارفع محتويات هذا المجلد إلى المستودع، مع مجلد `.github`.
3. افتح تبويب Actions، واختر `Build AMP News APK` ثم `Run workflow` إذا لم يبدأ البناء تلقائيًا.
4. بعد نجاح التشغيل، افتح صفحة التشغيل وانزل إلى Artifacts، ثم نزّل `AMP-News-debug-APK`.
5. فك ضغط ملف الـ Artifact لتحصل على `app-debug.apk` وانقله إلى هاتف Android للتجربة.

ملاحظة: هذا يبني نسخة debug للتجربة، وليس نسخة إنتاج موقعة للنشر العام في المتاجر. يلزم اتصال GitHub بحساب المستخدم، وقد تتغير واجهة GitHub.
