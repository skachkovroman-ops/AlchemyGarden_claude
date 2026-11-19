# Алхимический сад: каркас игры (Kotlin + Jetpack Compose)

Пакет: `com.example.alchemygarden`. Если у твоего проекта другой пакет, замени его в исходниках
(в Android Studio: Refactor → Rename package) и в `AndroidManifest.xml`.

## Что внутри
- `domain/GameEngine.kt`: вся логика (рост, полив, варка, экспедиции, бонусы гомункулов, офлайн-прогресс). Чистый Kotlin, без Android.
- `data/`: модели конфига и сохранения, загрузка `game_config.json`, сохранение в DataStore.
- `ui/`: экраны (Оранжерея, Лаборатория, Карта, Энциклопедия), хаб со свайпом, компоненты.
- `audio/`, `notifications/`: звуки (SoundPool) и пуш «Я добрался до …» через WorkManager.
- `assets/game_config.json`: весь баланс и контент. Правится без перекомпиляции логики.

## Подключение
1. Скопируй `app/src/main/java/...` и `app/src/main/assets/game_config.json` в свой проект.
2. Картинки и звуки: см. `IMAGES.md`. Можно не добавлять, игра работает на эмодзи.
3. В `app/build.gradle.kts` (плагины: `kotlin.android`, `kotlin.plugin.serialization`, `kotlin.plugin.compose` при Kotlin 2.0+):

```kotlin
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
```
4. `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<application android:name=".AlchemyApp" ... >
    <activity android:name=".MainActivity" android:exported="true"> <!-- intent-filter MAIN/LAUNCHER --> </activity>
</application>
```
`minSdk` 24 и выше.

## Отладка
В `game_config.json` → `settings.timeScale`: поставь `0.05`, и все таймеры (рост, варка, экспедиции) ускорятся в 20 раз.
Не забудь вернуть `1.0`.

## Сборка через GitHub (без Android Studio)
1. Создай репозиторий на GitHub и загрузи в него **содержимое** папки AlchemyGarden (так, чтобы `settings.gradle.kts` и `.github` лежали в корне репозитория).
2. Открой вкладку **Actions**: сборка запустится сама после загрузки (или нажми Run workflow).
3. Когда сборка станет зелёной, открой её и скачай **Artifacts → alchemy-garden-debug-apk**. Внутри `app-debug.apk`, его можно поставить на телефон (разреши установку из неизвестных источников).
4. Если сборка красная, открой лог упавшего шага и пришли мне текст ошибки.
