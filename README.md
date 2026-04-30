# Android SMS апп

Энэхүү төсөл нь `Fossify Messages` дээр суурилсан, SMS харилцааг илүү ухаалаг ангилж удирдах зорилготой сайжруулсан Android апп юм.

## 1) Төслийн зорилго

- Inbox ашиглалтыг хурдан, ойлгомжтой болгох
- Харилцан яриаг swipe, pin, tag/category-аар удирдах
- Scheduled message (товлосон илгээлт) найдвартай ажиллуулах
- Foldable төхөөрөмж дээр UI эвдрэлгүй, тасралтгүй ажиллах

## 2) Хийж хэрэгжүүлсэн ажлууд

- **Хажуу тийш чирэх үйлдэл**: Archive, Delete, Block, Read/Unread үйлдлүүдийг тохиргооноос сонгодог болгосон
- **Нассаар ангилах**: Today / Yesterday / This week / This month / Older бүлэглэл
- **Монгол хэл**: Өөр package-аас дуудаж байгаа үгсийг орчуулаагүй байгаа
- **Category/Tag систем**:
  - Category CRUD (үүсгэх, засах, устгах)
  - Thread дээр category chip харуулах
  - Олон tag-ийг зэрэг харуулах, шүүх логик
- **Saved Views**:
  - Main view + custom view-үүд
  - Доод талын switch bar + icon
  - Сүүлд ашигласан view-г дахин сэргээх
- **Search ба UI polish**: Түргэн хайлт, жижиг UI/UX засварууд

## 3) Техникийн хэрэгжилт

- **Хэл/платформ**: Kotlin, Android SDK
- **Persistence**: Room (conversation/message/category өгөгдөл)
- **Background ажил**: WorkManager (scheduled ажлууд)
- **Build**: Gradle (KTS), Android Studio
- **Архитектур**: Activity + Adapter + Extension helper загвар

## 4) Төслийн явцад шийдсэн асуудлууд

- Merge conflict-оос үүссэн алдаанууд (constants/import/resource)
- Swipe branch нэгтгэсний дараах unresolved reference асуудлууд
- Category/tag refresh үеийн overwrite асуудал (олон tag алдагдах)
- UI дээр chip clipping болон flicker асуудлууд
- Room migration холбоотой schema mismatch эрсдэлүүд

## 5) Одоогийн тест, баталгаажуулалт

- Үндсэн урсгалууд дээр гар тест хийсэн:
  - Inbox list ачаалалт
  - Swipe action trigger ба update
  - Category үүсгэх/засах/устгах
  - Saved view сонгох/үүсгэх/устгах
- Шинэ мессеж ирэхэд list refresh, category update урсгалыг шалгасан
- Зарим migration/test coverage-г нэмэгдүүлэх шаардлагатай хэвээр байна

## 6) Үлдсэн ажил (дараагийн алхам)

- Room migration-уудыг бүрэн тестжүүлэх (MigrationTestHelper)
- Tag/Folder дата моделийг тогтворжуулах, цэвэрлэх
- Settings/UI жижиг зөрчлүүдийг нэг мөр болгох
- Readme, тайлан, demo материалуудыг эцэслэх

## 7) Демо ажиллуулах товч заавар

```bash
./gradlew :app:assembleDebug
```

- Android Studio-оор `app` модулийг run хийж тестлэх
- SMS/Contacts permission зөвшөөрч байж бүх feature бүрэн ажиллана

## 8) Ашигласан суурь төсөл, лиценз

- Суурь код: `Fossify Messages`
- License: `GPLv3` (`LICENSE` файлыг харна уу)

## 9) Холбоос

- GitHub: `https://github.com/Timekilla-tech/Messages`
- GitLab (demo/work): `https://gitlab.sict.edu.mn/B220000135/Messages`
