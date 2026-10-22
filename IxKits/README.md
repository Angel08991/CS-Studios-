# IxKits — Kit Creation Studio

**Desarrollado por CS Studios.**

IxKits es un plugin de administración para diseñar, editar y preparar proyectos de kits desde una GUI de inventario. No añade comandos para que los jugadores reclamen kits.

## Primera versión

- GUI principal, biblioteca de proyectos y editor de inventario con bordes de cristal cian.
- Proyectos locales guardados en YAML.
- Plantillas locales de lore, paletas de color y presets de encantamientos.
- Exportación al formato nativo IxKits.
- Comandos administrativos protegidos con `ixkits.admin`.
- GitHub Actions para compilar el JAR automáticamente.

El catálogo comunitario online es un módulo previsto y aún necesita un servicio web/API; esta versión no publica ni descarga plantillas de Internet.

## Requisitos

- Paper 1.21.x (compilado contra Paper API 1.21.4).
- Java 21.
- Para compilar localmente, Gradle 8.10.2 o compatible.

## Obtener el JAR sin instalar Gradle

1. Abre la pestaña [Actions](https://github.com/Angel08991/CS-Studios-/actions).
2. Entra en la última ejecución correcta de **Build IxKits**.
3. Descarga el artefacto **IxKits-JAR** y extrae el archivo `.jar`.
4. Copia el JAR a la carpeta `plugins` de un servidor Paper de pruebas y reinicia.

Cada cambio del código dentro de `IxKits/` vuelve a ejecutar la compilación. También se puede iniciar desde **Actions → Build IxKits → Run workflow**.

## Compilación local

Desde esta carpeta:

```bash
gradle --no-daemon clean build
```

Resultado esperado: `build/libs/IxKits-0.1.0.jar`.

## Comandos

- `/ixkits` — menú principal.
- `/ixkits crear <nombre>` — crear un proyecto.
- `/ixkits editar <proyecto>` — editar un proyecto existente.
- `/ixkits proyectos` — abrir la biblioteca local.
- `/ixkits plantillas` — acceder a las plantillas locales.
- `/ixkits exportar <proyecto>` — exportar el formato nativo.
- `/ixkits lore guardar|aplicar <nombre>` — guardar o aplicar lore del objeto en mano.
- `/ixkits paleta aplicar <nombre>` — aplicar una paleta.
- `/ixkits encantamientos guardar|aplicar <nombre>` — guardar o aplicar un preset.
- `/ixkits recargar` — comprobar las plantillas predeterminadas.

## Estado y límites conocidos

Esta es una base inicial de desarrollo. La exportación de EssentialsX y otros formatos externos necesita adaptadores y pruebas por versión; por ahora no se debe asumir que todos los metadatos se traducen sin pérdida. El catálogo comunitario, CMI, AdvancedKits y la web de plantillas no están conectados en esta versión.

Probar en un servidor separado y hacer copia de seguridad antes de utilizar los archivos exportados en producción.

**IxKits — Kit Creation Studio by CS Studios**  
Comunidad: https://discord.gg/CnHSjMYDhr
