# CS ResourceHub — CS Studios

Marketplace Android para descubrir y publicar recursos de Minecraft, bots de Discord, mapas, configuraciones y otros recursos digitales. Inspirado en la idea general de los marketplaces de recursos, con marca y código propios.

## Estado actual

Este repositorio incluye una app Android nativa (WebView con selector de archivos Android), una interfaz adaptable, un esquema SQL con Row Level Security para Supabase, almacenamiento privado para los archivos de recursos, un flujo de moderación, favoritos, reseñas/reportes y Edge Functions para iniciar pagos Stripe.

**Es una base de desarrollo, no un marketplace listo para lanzarse públicamente.** Primero hay que crear/configurar Supabase, ejecutar la migración, desplegar las funciones y configurar secretos. Incluye código de integración Stripe Connect Express + Checkout con cargos de destino y una comisión de plataforma del 10% codificada en la función; no se ha probado contra una cuenta Stripe real y no debe activarse en modo live todavía. El webhook registra un reembolso en la base de datos, pero la reversión de transferencias Connect no está automatizada. Antes de aceptar pagos reales hacen falta pruebas end-to-end, un flujo de reembolsos/reversión, reglas de licencias, impuestos, términos, privacidad y revisión de seguridad.

## Descargar la APK

Cuando el proyecto esté en GitHub:

1. Abre **Actions** y entra en **Build CS ResourceHub APK**.
2. Pulsa **Run workflow** si quieres generar una versión bajo demanda.
3. Espera que la ejecución termine en verde.
4. En esa ejecución, descarga el artefacto `CS-ResourceHub-APK`.
5. Extrae el ZIP del artefacto y abre `CS-ResourceHub.apk` en Android.

La ejecución manual publica además un Release etiquetado `apk-build-N`. Las ejecuciones por `push` guardan la APK en Actions como artefacto de 30 días. El workflow `Deploy ResourceHub web app` publica la misma interfaz como sitio HTTPS en GitHub Pages (habilita Pages con la opción GitHub Actions en Settings → Pages).

> La APK generada por GitHub Actions es de depuración y no está firmada como una app de distribución de producción. Para un lanzamiento público, crea una clave de firma propia, guárdala en GitHub Secrets y añade un job de firma seguro. Nunca subas el keystore ni sus contraseñas al repositorio.

## Subir el proyecto a GitHub desde Android

1. Descarga el ZIP de este proyecto y extráelo.
2. Abre GitHub y crea un repositorio vacío llamado `CS-ResourceHub`.
3. Sube el contenido extraído manteniendo las carpetas y los archivos ocultos, en especial `.github/workflows/build-apk.yml` y `supabase/`.
4. Comprueba la pestaña **Actions**; si las acciones están deshabilitadas para el repositorio, actívalas en Settings → Actions.
5. Ejecuta el workflow manualmente para generar la primera APK.

## Configurar las funciones online

Necesitas un proyecto gratuito/propio de Supabase. El frontend utiliza la URL del proyecto y la clave pública anon/publishable; esas dos opciones se introducen dentro de la aplicación en **Cuenta → Configurar conexión online**. No uses la `service_role` key en la app.

### Base de datos y almacenamiento

1. Abre [Supabase Dashboard](https://supabase.com/dashboard) y crea un proyecto.
2. Ve a **SQL Editor** y ejecuta todo el archivo `supabase/migrations/001_marketplace.sql`.
3. En **Authentication → Providers**, configura Email. Para pruebas puedes decidir si se exige confirmar el correo.
4. En **Project Settings → API**, copia la Project URL y la clave pública anon/publishable.
5. Abre la aplicación y conecta esos dos valores.
6. Registra una cuenta en la app. Las nuevas publicaciones se guardan como `pending`, no son visibles públicamente hasta que un administrador las aprueba.
7. En el SQL Editor, promociona manualmente TU usuario a administrador. Sustituye el email por el correo que registraste:

```sql
update public.profiles
set role = 'admin'
where id = (select id from auth.users where email = 'tu-correo@example.com');
```

No permitas que los usuarios se den el rol admin a sí mismos. La protección de la base de datos evita que la app cambie la columna `role` para usuarios normales.

### Desplegar Edge Functions

Instala Supabase CLI en un ordenador o entorno compatible y desde la carpeta del repositorio ejecuta:

```bash
supabase login
supabase link --project-ref TU_PROJECT_REF
supabase functions deploy create-checkout-session
supabase functions deploy seller-onboarding
supabase functions deploy stripe-webhook --no-verify-jwt
```

Configura secretos mediante CLI/Secrets, nunca dentro del código cliente:

```bash
supabase secrets set STRIPE_SECRET_KEY=sk_test_TU_CLAVE
supabase secrets set STRIPE_WEBHOOK_SECRET=whsec_TU_SECRETO
supabase secrets set RESOURCEHUB_PUBLIC_URL=https://TU-WEB-HTTPS
```

El secreto de Supabase `SUPABASE_SERVICE_ROLE_KEY` y `SUPABASE_URL`/`SUPABASE_ANON_KEY` los proporciona el entorno de funciones de Supabase. No los pongas en la app. Para pagos reales, configura un webhook de Stripe que apunte a `https://TU_PROJECT_REF.supabase.co/functions/v1/stripe-webhook` y selecciona `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.expired`, `charge.refunded` y `account.updated`. Empieza con claves de prueba.

**Nota de pago importante:** el código de checkout usa Stripe Connect con una cuenta Express del vendedor como destino y una comisión de plataforma del 10% (valor fijo que debe revisarse). La cuenta de plataforma puede asumir las comisiones de procesamiento de Stripe; confirma los importes netos, países y monedas admitidos. El webhook marca la compra como reembolsada al recibir `charge.refunded`, pero no revierte automáticamente la transferencia al vendedor. No uses claves live ni vendas públicamente hasta implementar y probar esa compensación, disputas, impuestos y todos los caminos de error.

## Qué protege el esquema

- Catálogo público: solo recursos `approved`.
- Usuarios: pueden leer perfiles públicos, gestionar sus favoritos y enviar reseñas/reportes bajo políticas RLS.
- Publicaciones: comienzan en `pending`; los cambios a contenido aprobado lo devuelven a revisión.
- Archivos: bucket privado `resource-files`; la descarga requiere ser dueño del recurso, ser gratuito y aprobado, o tener una compra pagada.
- Portadas: bucket público limitado a imágenes, para mostrarlas en fichas.
- Moderación: función `moderate_resource` reservada a administradores.
- Compras: se marcan `paid` únicamente desde la Edge Function que verifica el webhook Stripe.

La seguridad debe probarse en un proyecto de prueba antes de producción. La base no sustituye antivirus para archivos subidos ni una revisión legal del marketplace.

## Compilar localmente

Requisitos: JDK 17, Android SDK Platform 35 y Gradle 8.9.

```bash
gradle --no-daemon assembleDebug
```

APK de depuración:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Enlaces

- Comunidad CS Studios: https://discord.gg/CnHSjMYDhr
- Fuente del proyecto: GitHub

Desarrollado por **CS Studios**.

## Políticas del marketplace

Revisa `docs/MARKETPLACE-RULES.md`. Las normas y la sección de privacidad son una plantilla de trabajo y deben completarse con los datos reales del operador y revisarse antes de publicar la app o aceptar pagos.

## Instalar como aplicación web mientras generas la APK

Después de publicar el sitio en GitHub Pages, abre su URL HTTPS en Chrome para Android, abre el menú ⋮ y selecciona **Añadir a pantalla de inicio** o **Instalar aplicación** si Chrome muestra esa opción. Esto instala la versión web progresiva (PWA); no es un archivo APK. Para obtener el APK real usa el workflow de Android descrito arriba.

Para subir el código desde Android mediante Termux, consulta `docs/UPLOAD-ANDROID.md`. También puedes ejecutar `bash publish-to-github.sh` desde la raíz del proyecto para crear el repositorio, iniciar GitHub Actions y descargar la APK automáticamente (tras autenticarte mediante GitHub CLI).
