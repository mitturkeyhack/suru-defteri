# Sürü Defteri

İnternetsiz çalışan koyun sürüsü takip uygulaması.

## Özellikler

- **Otomatik sayım (canlı):** Telefonu geçide tut; telefonda çalışan yapay zekâ koyunları tanır, takip eder ve sarı çizgiyi geçenleri sayar. Geri dönen koyun düşülür.
- **Videodan sayım:** Çekilmiş videoyu kare kare, daha hassas modelle inceler.
- **Koyun kartları:** Küpe no, isim, cinsiyet, doğum yılı, anne, durum, notlar, fotoğraf.
- **Küpe okuma:** Kamerayı küpeye tut, numara internetsiz okunur ve kartı açılır.
- **Sayım geçmişi:** "129 / 132 · 3 eksik" gibi.
- **Otomatik güncelleme:** İnternet bulunca GitHub'dan yeni sürümü alır.

## Teknik

- Koyun tanıma: Google MediaPipe EfficientDet (COCO "sheep"), modeller derlemede indirilir.
- Takip ve sayım: `count/Tracker.kt` (`LineCounter`), birim testleriyle doğrulanır.
- Küpe okuma: ML Kit metin tanıma (uygulamaya gömülü).
- Kotlin, Jetpack Compose, CameraX, Room.
