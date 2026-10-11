# CS ResourceHub — Android APK

Proyecto de **CS ResourceHub by CS Studios**. El código fuente de Android y los recursos del proyecto se colocan en la carpeta `CS-ResourceHub/` al completar la primera compilación.

## Cómo obtener la APK

1. Abre la pestaña **Actions** de este repositorio.
2. Entra en **Build CS ResourceHub APK**.
3. Para una compilación manual, pulsa **Run workflow** y selecciona la rama `cs-resourcehub-android`.
4. Abre la ejecución más reciente y espera a que el trabajo **Compile Android APK** termine en verde.
5. En **Artifacts**, descarga `CS-ResourceHub-APK`. Dentro estará `CS-ResourceHub.apk`.

Las actualizaciones que subas a la rama `cs-resourcehub-android` volverán a compilar la aplicación automáticamente. Las ejecuciones manuales también publican una Release con la APK adjunta.

## Notas

- La compilación es una APK de depuración para pruebas, no un paquete firmado para Google Play.
- Las funciones que dependen de Supabase/Stripe requieren credenciales y configuración propias.
- El código se organiza en una rama separada; la rama `main` no se modifica.

> Estado: workflow de compilación configurado en `.github/workflows/build-cs-resourcehub.yml`.
