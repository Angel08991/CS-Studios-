# IxKits — Kit Creation Studio

**Desarrollado por CS Studios.**

IxKits es un plugin administrativo para diseñar, editar, guardar y preparar proyectos de kits desde una GUI de inventario. No añade comandos para que los jugadores reclamen kits.

## Funciones disponibles

- GUI principal y biblioteca local de proyectos.
- Editor visual de inventario con dos páginas y bordes de cristal cian.
- Personalización de objetos: haz **clic derecho sobre un objeto** dentro del editor para abrir su ficha.
- Editor de encantamientos en GUI: clic izquierdo para subir nivel, clic derecho para bajar y Mayús + clic para eliminar. Se pueden recorrer páginas de encantamientos.
- Tres plantillas de lore predeterminadas con textos en español e inglés: **PvP limpio, Legendario y Minero**. Puedes personalizarlas en `plugins/IxKits/templates/lore.yml`.
- Selector de idioma desde `/ixkits idioma`; la selección se guarda automáticamente en `config.yml`.
- Idiomas disponibles: español (`es`) e inglés (`en`).
- Paletas de color y presets locales de encantamientos.
- Exportación al formato nativo IxKits.
- Comandos administrativos protegidos con `ixkits.admin`.

El estilo de lore usa patrones comunes de ítems de servidores —separadores, rareza, categoría y descripciones breves—, pero las plantillas están redactadas para IxKits y se pueden editar. La estructura toma como referencia la personalización de lore, paletas y niveles de rareza que se ven en herramientas públicas de estilo de objetos, sin copiar el texto de un servidor concreto.

## Requisitos

- Paper 1.21.x (compilado contra Paper API 1.21.4).
- Java 21.
- Para compilar localmente, Gradle 8.10.2 o compatible.

## Configuración del idioma

En `plugins/IxKits/config.yml`:

```yaml
language: es # es = español | en = inglés
```

También puedes ejecutar `/ixkits idioma` y escoger el idioma desde el menú. Si editas el archivo manualmente, usa `/ixkits recargar` o reinicia el servidor. El idioma se configura en `config.yml`, no en `plugin.yml`, porque este último contiene metadatos del plugin.

## Comandos

- `/ixkits` — abrir el menú principal.
- `/ixkits crear <nombre>` — crear un proyecto.
- `/ixkits editar <proyecto>` — abrir un proyecto.
- `/ixkits proyectos` — abrir la biblioteca local.
- `/ixkits plantillas` — explorar las plantillas.
- `/ixkits idioma` — abrir el selector de idioma.
- `/ixkits idioma es|en` — cambiar idioma directamente.
- `/ixkits exportar <proyecto>` — exportar el formato nativo.
- `/ixkits lore guardar|aplicar <nombre>` — guardar o aplicar lore al objeto en mano.
- `/ixkits paleta aplicar <nombre>` — aplicar una paleta.
- `/ixkits encantamientos guardar|aplicar <nombre>` — guardar o aplicar un preset.
- `/ixkits recargar` — recargar configuración y plantillas.

## Obtener el JAR sin instalar Gradle

1. Abre la pestaña [Actions](https://github.com/Angel08991/CS-Studios-/actions).
2. Entra en la última ejecución correcta de **Build IxKits**.
3. Descarga el artefacto **IxKits-JAR** y extrae el archivo `.jar`.
4. Copia el JAR a la carpeta `plugins` de un servidor Paper de pruebas y reinicia.

Cada cambio dentro de `IxKits/` inicia una compilación mediante GitHub Actions.

## Compilación local

Desde la carpeta `IxKits`:

```bash
gradle --no-daemon clean build
```

Resultado esperado: `build/libs/IxKits-0.1.0.jar`.

## Límites conocidos

La exportación a EssentialsX y otros formatos externos requiere adaptadores específicos y pruebas; este proyecto todavía exporta al formato nativo IxKits. El catálogo comunitario online aún necesita un servicio web/API y por ahora no publica ni descarga plantillas. El código debe probarse en un servidor Paper de pruebas antes de utilizarse en producción.

**IxKits — Kit Creation Studio by CS Studios**  
Comunidad: https://discord.gg/CnHSjMYDhr
