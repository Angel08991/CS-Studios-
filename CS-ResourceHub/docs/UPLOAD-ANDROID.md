# Subir CS ResourceHub a GitHub desde tu Android

Esta guía usa Termux para conservar carpetas ocultas y el workflow de Actions. No subas claves secretas al repositorio.

## 1. Prepara el ZIP

1. Descarga `CS-ResourceHub-source.zip` desde ChatGPT.
2. Ábrelo con ZArchiver y extráelo directamente en `Almacenamiento interno/Download/`. El ZIP ya contiene una carpeta llamada `CS-ResourceHub`; no crees una segunda carpeta con el mismo nombre antes de extraerlo.
3. Comprueba que `Almacenamiento interno/Download/CS-ResourceHub/` contiene `settings.gradle.kts`, `README.md`, `app/`, `supabase/` y `.github/`.

## 2. Instala Git y autentícate

En Termux ejecuta:

```bash
pkg update
pkg install git gh
termux-setup-storage
```

Acepta el permiso de archivos que Android muestre. Luego entra a la carpeta extraída (ajusta la ruta si ZArchiver creó una carpeta adicional):

```bash
cd /storage/emulated/0/Download/CS-ResourceHub
ls -la
```

La lista debe incluir la carpeta oculta `.github`. Si no aparece, estás en la carpeta equivocada.

Inicia sesión con GitHub CLI:

```bash
gh auth login
```

Selecciona `GitHub.com`, `HTTPS` y la autenticación mediante navegador/código. **No compartas tu código de autenticación ni ningún token.**

## 3. Crea y sube el repositorio

Primero verifica que el repositorio `CS-ResourceHub` no existe todavía en tu cuenta. Si no existe, ejecuta desde la carpeta del proyecto:

```bash
git init -b main
git add .
git commit -m "Initial CS ResourceHub marketplace"
gh repo create CS-ResourceHub --public --source=. --remote=origin --push
```

Si Git informa que falta tu nombre o correo, configúralos con los datos que quieres que aparezcan en los commits y repite el commit:

```bash
git config --global user.name "Angel08991"
git config --global user.email "TU_CORREO_DE_GITHUB"
```

Si el repositorio `CS-ResourceHub` ya existe, no crees otro con el mismo nombre. Abre ese repositorio, sigue sus instrucciones de sincronización y evita forzar un `push` que pudiera borrar commits remotos.

## 4. Genera la APK

1. Abre `https://github.com/Angel08991/CS-ResourceHub/actions`.
2. Activa GitHub Actions si GitHub lo solicita.
3. Ejecuta **Build CS ResourceHub APK** → **Run workflow**.
4. Cuando termine en verde, abre la ejecución y descarga el artefacto `CS-ResourceHub-APK`. Si ejecutaste el workflow manualmente, también aparecerá un Release con `CS-ResourceHub.apk`.
5. Extrae el artefacto ZIP y pulsa el archivo `.apk` para instalarlo. Android podría pedir permiso para instalar desde la app que uses para abrir el archivo.

La APK de este workflow es de depuración, no una versión firmada para producción.

## 5. Publica la versión web

Abre `Settings → Pages` del repositorio y selecciona `GitHub Actions` como origen. El workflow `Deploy ResourceHub web app` publicará una web progresiva (PWA) en una URL similar a `https://Angel08991.github.io/CS-ResourceHub/`. Esa web permite probar el catálogo y se puede añadir a la pantalla de inicio, pero no es un archivo APK.

## 6. Activa el backend

Sigue `README.md` para crear Supabase, ejecutar la migración SQL y añadir sus valores públicos en la app. No pongas `service_role`, Stripe secret keys, contraseñas ni claves de firma en el cliente ni en GitHub.

## 7. Opción automatizada: publicar y descargar la APK desde Termux

Dentro de la carpeta raíz `CS-ResourceHub`, instala lo necesario y autentícate una sola vez:

```bash
pkg update
pkg install git gh
termux-setup-storage
gh auth login
```

Luego ejecuta:

```bash
bash publish-to-github.sh
```

El script crea el repositorio público `CS-ResourceHub` en la cuenta que has autenticado, sube el código sin forzar el historial, inicia la compilación, espera a GitHub Actions y descarga el artefacto de la APK dentro de `dist/CS-ResourceHub.apk`. Si ya existe un repositorio con ese nombre pero no es el remoto de esta carpeta, el script se detiene para evitar sobrescribir contenido por accidente.

La autenticación se realiza en tu propio teléfono mediante GitHub CLI; no envíes tu contraseña, código de dispositivo ni token a nadie.
