# IxKits — Kit Creation Studio

**Desarrollado por CS Studios.**

IxKits es un plugin administrativo para diseñar, personalizar, organizar, validar e intercambiar proyectos de kits. No añade un comando para que los jugadores reclamen kits.

## Estado actual

La compilación de CI se ejecuta contra Paper API 1.21.11. La GUI debe probarse también en un servidor Paper 1.21.11 real antes de producción.

- GUI de estudio con bordes y editor de inventario en dos páginas.
- Editor de encantamientos: clic izquierdo sube el nivel; clic derecho lo baja; Mayús + clic elimina ese encantamiento.
- Editor de pociones para escoger el tipo base y añadir/quitar efectos personalizados o subir su nivel desde una GUI.
- Menú de propiedades especiales para libros escritos (título, autor y páginas MiniMessage), patrones y colores de estandartes, trims de armadura, propietario de cabezas y fuegos artificiales (potencia, tipo/color de efecto y eliminación de efectos).
- Papelera recuperable en la GUI del navegador de proyectos, con acciones para deshacer y rehacer.
- Portapapeles de objetos para duplicar una copia exacta desde un proyecto a otra ranura vacía.
- Tres plantillas de lore bilingües: PvP limpio, Legendario y Minero.
- Editor de nombre y lore con MiniMessage; separa las líneas de lore usando el separador vertical.
- Alternar irrompible, cambiar durabilidad, custom model data, flags, color de armadura de cuero y modificadores propios de atributos.
- Editor de equipamiento separado para casco, peto, pantalones, botas y mano secundaria.
- Biblioteca de proyectos con paginación y búsqueda: crear desde la GUI, abrir, duplicar, renombrar, eliminar moviendo a papelera, validar y restaurar una copia anterior.
- Backups locales rotativos; importación y exportación del formato nativo.
- Adaptador de EssentialsX en formato legible con avisos por posible pérdida de metadatos.
- Selector de idioma en español e inglés.
- Cliente opcional y servicio REST independiente para el catálogo comunitario.

La compatibilidad con ItemEdit está planteada como **opcional**: IxKits no requiere tener instalado /ie. Los objetos avanzados importados de otro plugin deben probarse, ya que el formato de intercambio puede no representar todas sus propiedades.

## Requisitos

- Paper 1.21.11; se compila contra Paper API 1.21.11.
- Java 21.
- Gradle 8.10.2 o compatible para compilar localmente.

## Idiomas

En plugins/IxKits/config.yml:

    language: es # es = español | en = inglés

También se cambia con /ixkits idioma. Para que el cambio manual del archivo se aplique en caliente, ejecuta /ixkits recargar.

## Comandos principales

| Comando | Función |
|---|---|
| /ixkits | Abrir la GUI principal |
| /ixkits crear <nombre> | Crear un proyecto |
| /ixkits editar <proyecto> | Abrir un proyecto |
| /ixkits proyectos | Abrir la biblioteca paginada y con búsqueda |
| /ixkits plantillas | Explorar los lores predeterminados |
| /ixkits idioma o /ixkits idioma es/en | Cambiar idioma |
| /ixkits duplicar <origen> <nuevo nombre> | Duplicar proyecto |
| /ixkits renombrar <origen> <nuevo nombre> | Renombrar |
| /ixkits eliminar <proyecto> | Mover a papelera recuperable |
| /ixkits papelera | Abrir la papelera desde el servidor |
| /ixkits restaurar-eliminado <proyecto> | Restaurar un proyecto eliminado |
| /ixkits deshacer <proyecto> | Restaurar la última copia de seguridad disponible |
| /ixkits rehacer <proyecto> | Reaplicar el cambio deshecho más reciente |
| /ixkits validar <proyecto> | Comprobar problemas básicos |
| /ixkits equipamiento <proyecto> | Editar las cinco ranuras de equipamiento |
| /ixkits importar-mano <proyecto> <página 1-2> <ranura GUI> | Copiar el objeto de tu mano al proyecto conservando sus metadatos Bukkit/Paper |
| /ixkits exportar <proyecto> | Exportar formato nativo .ixkit.yml |
| /ixkits importar <archivo.yml> [nombre] | Importar desde plugins/IxKits/imports/ |
| /ixkits exportar-essentials <proyecto> | Crear exportación legible para EssentialsX |
| /ixkits importar-essentials <archivo.yml> <kit> <nombre> | Importar un kit legible de EssentialsX |
| /ixkits lore guardar <nombre> | Guardar lore del objeto en mano |
| /ixkits lore aplicar <nombre> | Aplicar una plantilla al objeto en mano |
| /ixkits paleta aplicar <nombre> | Aplicar una paleta al objeto en mano |
| /ixkits encantamientos guardar <nombre> | Guardar encantamientos del objeto en mano |
| /ixkits encantamientos aplicar <nombre> | Aplicar un preset |
| /ixkits recargar | Recargar config.yml y plantillas |
| /ixkits catalogo [búsqueda] | Explorar el catálogo configurado |
| /ixkits publicar <proyecto> <categoría> [descripción] | Publicar un proyecto con el token configurado |
| /ixkits descargar <id> | Descargar una plantilla del catálogo |
| /ixkits valorar <id> <1-5> | Valorar una plantilla |
| /ixkits favorito <id> | Alternar favorito |
| /ixkits actualizar-publicacion <id> <proyecto> [cambios] | Publicar una nueva versión de una plantilla comunitaria |
| /ixkits versiones <id> | Consultar el historial de revisiones de una plantilla comunitaria |

En la biblioteca de proyectos, clic izquierdo abre y clic derecho muestra las herramientas del proyecto.

### Importar objetos creados con otros plugins

IxKits no depende de ItemEdit ni de otro editor de objetos. Para copiar a un proyecto un objeto personalizado que tengas en la mano (incluidos objetos editados con ItemEdit), usa:

    /ixkits importar-mano <proyecto> <página 1-2> <ranura GUI>

Ejemplo: `/ixkits importar-mano espada-pvp 1 10` guarda una copia del objeto en la ranura GUI 10 de la primera página. El objeto se guarda mediante la serialización de Bukkit/Paper para conservar los metadatos que esa versión pueda serializar. Haz una prueba con objetos de plugins externos antes de producción: metadatos privados o componentes no estándar de otro plugin pueden no ser portables entre versiones. Esta función copia el objeto; no ejecuta comandos ni necesita que ItemEdit esté instalado.

### Importación y exportación

Copia los archivos a plugins/IxKits/imports/ antes de usar /ixkits importar. La exportación nativa conserva objetos serializados de Bukkit/Paper y el equipamiento de IxKits.

La exportación a EssentialsX está en plugins/IxKits/exports/. Comprueba el resultado antes de incorporarlo a tu kits.yml: el formato legible no puede representar todos los metadatos avanzados. Los kits de EssentialsX guardados como NBT/base64 no se convierten con el importador básico; usa el serializador de EssentialsX para esos archivos.

## Catálogo online opcional

El servicio está en IxKits/catalog-service/, usa Node.js 20+ y no necesita dependencias npm externas.

1. Para Render, importa el repositorio y selecciona el Blueprint raíz render.yaml. Este blueprint usa un plan Starter y un disco persistente; revisa el coste en Render antes de confirmar. Alternativamente, despliega la carpeta como servicio Node en otro host con almacenamiento persistente.
2. El blueprint genera IXKITS_PUBLISH_TOKEN, IXKITS_ADMIN_TOKEN e IXKITS_INTERACTION_TOKEN como secretos independientes. Si despliegas manualmente, crea los tres con valores aleatorios diferentes.
3. El blueprint configura IXKITS_DATA_DIR en el disco persistente. Si usas otro host, define esa variable apuntando a almacenamiento persistente; con disco efímero el catálogo puede perder los datos al reiniciar.
4. Verifica https://TU-DOMINIO/health.
5. En plugins/IxKits/config.yml, configura la URL real:

    catalog:
      enabled: true
      base-url: "https://TU-DOMINIO"
      publish-token: "EL_TOKEN_DE_PUBLICACION"
      interaction-token: "EL_TOKEN_DE_INTERACCIONES"
      minecraft-version: "1.21.11"
      default-license: "all-rights-reserved"

Después del despliegue, copia IXKITS_PUBLISH_TOKEN al campo catalog.publish-token e IXKITS_INTERACTION_TOKEN al campo catalog.interaction-token de plugins/IxKits/config.yml. No pongas el token de administración en el plugin ni publiques ninguno de los secretos en un repositorio o chat. Las valoraciones y favoritos requieren el token de interacción; la moderación usa el token administrativo solo en la API.

El token de interacción evita que cualquier visitante anónimo escriba valoraciones o favoritos directamente. Aun así, los identificadores los proporciona el servidor que posee ese secreto; para una comunidad pública grande conviene añadir cuentas verificadas, moderación y controles adicionales. La plantilla se guarda como texto y el servicio no ejecuta comandos ni código de los archivos subidos; aun así, el plugin valida antes de importar.

Consulta IxKits/catalog-service/README.md para los endpoints y las variables de entorno.

## Obtener el JAR sin instalar Gradle

1. Abre GitHub Actions: https://github.com/Angel08991/CS-Studios-/actions
2. Entra en la ejecución más reciente de Build IxKits cuyo resultado sea correcto.
3. Descarga el artefacto IxKits-JAR y extrae el .jar.
4. Copia el JAR a plugins/ de un servidor Paper de pruebas y reinicia.

Cada cambio dentro de IxKits/ inicia una compilación mediante GitHub Actions.

## Compilación local

Desde la raíz del repositorio:

    gradle --no-daemon -p IxKits clean build

Para probar el servicio de catálogo:

    npm test --prefix IxKits/catalog-service
    npm start --prefix IxKits/catalog-service

## Notas de seguridad y pruebas

- Haz una copia de seguridad de plugins/IxKits/ antes de actualizar.
- Prueba en un servidor Paper separado antes de producción.
- La compilación no sustituye las pruebas de GUI dentro de Paper 1.21.11.
- No compartas tokens del catálogo ni los subas a Git.
- Los archivos importados se consideran datos, no scripts ejecutables.
- El catálogo no queda publicado en Internet automáticamente; necesita desplegar el servicio y configurar su URL.

**IxKits — Kit Creation Studio by CS Studios**  
Comunidad: https://discord.gg/CnHSjMYDhr
