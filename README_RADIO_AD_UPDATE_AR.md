# تحديث تطبيق Arab Media Platform

يشمل هذا التحديث:
- حذف زر «الرئيسية» الإضافي، مع الإبقاء على زر الرجوع الأصلي.
- إضافة مشغل وسائط في إشعار أندرويد مع أزرار استئناف/إيقاف مؤقت وإيقاف.
- طلب إذن الإشعارات على Android 13 وما بعده حتى يظهر إشعار الراديو عند السماح به.
- إعادة محاولة الاتصال بالبث تلقائيًا عند انقطاعه، وحفظ حالة التشغيل لاستعادتها إذا أعاد أندرويد تشغيل الخدمة.
- إضافة مساحة إعلانية تحت شريط آخر الأخبار.

## الملفات التي يجب استبدالها في GitHub
1. `app/src/main/java/com/arabmediaplatform/news/MainActivity.java`
2. `app/src/main/java/com/arabmediaplatform/news/RadioPlaybackService.java`
3. `app/src/main/assets/index.html`
4. `app/build.gradle`

ملف `AndroidManifest.xml` لا يحتاج إلى تعديل في هذا التحديث إذا كان يحتوي مسبقًا على صلاحيات `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `POST_NOTIFICATIONS` وعلى تعريف `RadioPlaybackService` بنوع `mediaPlayback`.

بعد البناء والتثبيت، وافق على إذن الإشعارات عند ظهوره. افتح الراديو ثم اقفل الشاشة وتأكد من ظهور إشعار «إذاعة المنصة» وتجربة أزراره. لم يتم تنفيذ بناء Android فعلي داخل بيئة إعداد الملفات؛ يجب تأكيد نجاحه من GitHub Actions.
