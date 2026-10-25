# IxKits — Kit Creation Studio

**Desarrollado por CS Studios.**

IxKits es un estudio visual para diseñar, personalizar, organizar, validar, exportar y compartir proyectos de kits. Incluye `/ixkits claim <proyecto>` para entregar los objetos y piezas de equipamiento de un proyecto cuando el servidor habilite esa función.

## Estado actual

La compilación de CI se ejecuta contra Paper API 1.21.11. La GUI debe probarse también en un servidor Paper 1.21.11 real antes de producción.

- GUI de estudio con bordes, editor de inventario en dos páginas y un Taller de objeto reorganizado con detalles, portapapeles, paletas y generación de conjuntos.
- **Rellenar conjunto** genera herramientas, piezas de armadura, escudo y armas a distancia que falten, usando la calidad del objeto seleccionado. Solo usa ranuras vacías, conserva los objetos existentes y aplica encantamientos compatibles por tipo (no aplica Filo a picos o palas). Los valores predeterminados se configuran en `set-fill` dentro de `config.yml`.
- Editor de encantamientos: clic izquierdo sube el nivel, clic derecho lo baja, Mayús + clic izquierdo permite escribir un nivel exacto (1–32767) y Mayús + clic derecho elimina el encantamiento. Incluye presets con niveles personalizados.
- Editor de pociones para escoger el tipo base y añadir/quitar efectos personalizados o subir su nivel desde una GUI.
- Diseñador de escudos y estandartes: modifica patrones, el color de cada patrón y el color base del diseño. También incluye trims de armadura, libros, cabezas y fuegos artificiales.
- Papelera recuperable en la GUI del navegador de proyectos, con acciones para deshacer y rehacer.
- Portapapeles persistente para copiar, inspeccionar, reemplazar y pegar objetos con metadatos serializados, incluso después de reiniciar el servidor; los datos se guardan por UUID en `plugins/IxKits/clipboard/`.
- Biblioteca de lores organizada por categorías: armas, armaduras, objetos especiales, minerales/materiales, consumibles y otros diseños. Incluye estilos originales inspirados en armas AERIS/ONI, armaduras de conjunto, minerales ancestrales, consumibles y tokens de evento, además de las plantillas anteriores.
- Las plantillas pueden añadir automáticamente encantamientos y habilidades reales usando `{enchantments}` y `{abilities}`; `{item}` inserta el nombre del objeto. Al aplicar una plantilla enlazada, el lore se actualiza al cambiar encantamientos o habilidades; al editar el lore manualmente, se desvincula para proteger las modificaciones. Incluye estilos para espada AERIS, conjuntos Ónix/Vanguard, armadura dracónica, sopa ignífuga, mineral ancestral, token KOTH y más.
- Las paletas RGB aplican gradientes carácter por carácter al nombre, al lore o a ambos: Aurora, Plasma, Tóxico, Glaciar, Flujo Real y Oro Rosado. El destino se selecciona en el menú de paletas.
- Editor de nombre y lore con MiniMessage y códigos legacy/RGB combinados, incluidos formatos como `&#F2FF00&lU&#F4F300&lS`; separa las líneas usando `|`, y el editor visual permite insertar líneas vacías sin texto. Las GUI muestran controles más explicativos, vistas previas mejoradas y una firma visual de CS Studios.
- Alternar irrompible, cambiar durabilidad, custom model data, flags, color de armadura de cuero y modificadores propios de atributos.
- Editor de equipamiento separado para casco, peto, pantalones, botas y mano secundaria.
- Biblioteca de proyectos con paginación y búsqueda: crear desde la GUI, abrir, duplicar, renombrar, eliminar moviendo a papelera, validar y restaurar una copia anterior.
- Backups locales rotativos; importación y exportación del formato nativo.
- Adaptador de EssentialsX en formato legible con avisos por posible pérdida de metadatos.
- Selector de idioma con español, inglés, portugués, francés, alemán, italiano, chino y ruso. Las pantallas principales tienen traducciones comunes; las frases no traducidas recurren al inglés.
- Community Hub público para navegar kits/proyectos, lores, paletas RGB y presets de encantamientos; permite descargar recursos y guardarlos de forma segura en las bibliotecas locales.
- Publicación comunitaria de kits con categoría y descripción elegidas desde la GUI; los recursos locales se comparten haciendo clic derecho sobre una plantilla de lore, una paleta o un preset.
- Cooldown individual por objeto y habilidad: clic derecho sobre TNT, rayo o robo de vida abre el editor de segundos (0–86400); clic izquierdo sigue activando/desactivando la habilidad.
- Cancelación de entradas de chat con la palabra clicable **Cancelar** o escribiendo cancelar (también se reconoce -cancelar).

La compatibilidad con ItemEdit está planteada como **opcional**: IxKits no requiere tener instalado /ie. Los objetos avanzados importados de otro plugin deben probarse, ya que el formato de intercambio puede no representar todas sus propiedades.

## Requisitos

- Paper 1.21.11; se compila contra Paper API 1.21.11.
- Java 21.
- Gradle 8.10.2 o compatible para compilar localmente.

## Permisos

- `ixkits.admin` (por defecto, operadores): crear y editar proyectos, usar las herramientas administrativas y publicar desde la GUI.
- `ixkits.community.publish` (por defecto, operadores): publicar recursos comunitarios desde las bibliotecas locales y usar `/ixkits publicar` para compartir un proyecto existente sin dar acceso al editor completo.
- Cualquier jugador puede abrir `/ixkits comunidad` para explorar kits y recursos. También puede abrir `/ixkits plantillas` y aplicar presets a su objeto en mano; solo los usuarios con permiso de publicación pueden subir recursos.

Con LuckPerms, el permiso `ixkits.community.publish` se puede otorgar por separado a colaboradores de confianza, sin conceder `ixkits.admin`.

## Idiomas

En plugins/IxKits/config.yml:

    language: es # es, en, pt, fr, de, it, zh o ru

`/ixkits idioma` abre el selector de idiomas. Los nombres y mensajes que aún no cuentan con una traducción concreta usan inglés como alternativa.

También se cambia con /ixkits idioma. Para que el cambio manual del archivo se aplique en caliente, ejecuta /ixkits recargar.

Edita `plugins/IxKits/messages.yml` para personalizar el prefijo y los mensajes de portapapeles, reclamación, importación/exportación, generación de conjuntos, idioma y ayuda. Admite `&` legacy y RGB `&#RRGGBB`; hay secciones de mensajes en varios idiomas.

## Comandos principales

| Comando | Función |
|---|---|
| /ixkits | Abrir el taller principal |
| /ixkits help o /ixkits ayuda | Mostrar la ayuda ordenada por secciones |
| /ixkits crear <nombre> | Crear un proyecto |
| /ixkits editar <proyecto> | Abrir un proyecto |
| /ixkits proyectos | Abrir la biblioteca paginada y con búsqueda |
| /ixkits plantillas | Explorar las plantillas de lore por categoría |
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
| /ixkits exportar <proyecto> | Exportar formato nativo .ixkit.yml, con resumen de objetos y metadatos |
| /ixkits claim <proyecto> | Entregar los objetos y el equipamiento del proyecto al jugador |
| /ixkits importar <archivo.yml> [nombre] | Importar desde plugins/IxKits/imports/ |
| /ixkits exportar-essentials <proyecto> | Crear exportación legible para EssentialsX |
| /ixkits importar-essentials <archivo.yml> <kit> <nombre> | Importar un kit legible de EssentialsX |
| /ixkits lore guardar <nombre> | Guardar lore del objeto en mano |
| /ixkits lore aplicar <nombre> | Aplicar una plantilla al objeto en mano |
| /ixkits paleta aplicar <nombre> | Aplicar una paleta al objeto en mano |
| /ixkits encantamientos guardar <nombre> | Guardar encantamientos del objeto en mano |
| /ixkits encantamientos aplicar <nombre> | Aplicar un preset |
| /ixkits recargar | Recargar config.yml y plantillas |
| /ixkits comunidad | Abrir Community Hub; disponible para todos los jugadores |
| /ixkits catalogo [búsqueda] | Explorar los kits del catálogo online |
| /ixkits publicar <proyecto> <categoría> [descripción] | Publicar un proyecto con el token configurado |
| /ixkits descargar <id> | Descargar una plantilla del catálogo |
| /ixkits valorar <id> <1-5> | Valorar una plantilla |
| /ixkits favorito <id> | Alternar favorito |
| /ixkits actualizar-publicacion <id> <proyecto> [cambios] | Publicar una nueva versión de una plantilla comunitaria |
| /ixkits versiones <id> | Consultar el historial de revisiones de una plantilla comunitaria |

En la biblioteca de proyectos, clic izquierdo abre y clic derecho muestra las herramientas del proyecto.

### Flujo de trabajo del editor

1. Ejecuta `/ixkits` y pulsa **Crear proyecto** o abre **Mis proyectos**.
2. En el inventario de edición, coloca un objeto en una ranura vacía con el cursor. También puedes hacer **Mayús + clic** sobre un objeto de tu inventario para colocarlo automáticamente en la primera ranura libre.
3. Al poner un objeto en una ranura, IxKits guarda el proyecto y abre el menú **Editor de objeto**. Para volver a editar un objeto existente, haz **clic derecho** en su ranura.
4. Desde ese menú puedes cambiar nombre, encantamientos y propiedades, abrir el **Editor visual de lore** o configurar **Habilidades especiales**. En el editor de lore, clic normal edita una línea, clic derecho la borra, Mayús + clic la sube, Mayús + clic derecho la baja y clic central duplica la línea. La vista previa conserva sus colores y formato.
5. En `/ixkits equipamiento <proyecto>`, equipa con **Mayús + clic** desde tu inventario para colocar una pieza compatible. Tanto al colocar una pieza manualmente como con Mayús + clic se abre el mismo editor del objeto automáticamente. También puedes hacer **clic derecho** en una pieza ya puesta para volver a editarla, incluidos encantamientos, lore y habilidades.

### Habilidades de objetos

Desde `Editor de objeto → Habilidades especiales` puedes activar individualmente estas habilidades en el objeto seleccionado. Se guardan como metadatos del objeto y se activan al golpear con él en la mano principal:

- **TNT al golpear:** aparece una TNT junto al objetivo. Puedes configurar mecha, potencia y enfriamiento.
- **Rayo al golpear:** invoca un rayo sobre el objetivo, con sus efectos normales.
- **Robo de vida:** recupera un porcentaje del daño del golpe.
- **Veneno:** aplica el efecto de veneno al objetivo.
- **Escarcha:** aplica lentitud al objetivo.
- **Llama:** prende fuego al objetivo.
- **Impacto reforzado:** lanza al objetivo hacia atrás con una fuerza configurable.

Cada habilidad tiene enfriamiento independiente por objeto. Haz clic izquierdo en una habilidad para activarla o desactivarla y clic derecho para cambiar el cooldown. Los efectos solo se activan cuando el objeto está en la mano principal y golpeas a una entidad viva. Los valores iniciales (duración, amplificador, fuerza y cooldown) se pueden modificar en `plugins/IxKits/config.yml`.

#### Lore dinámico de encantamientos y habilidades

En una plantilla de lore, coloca estos marcadores **cada uno en su propia línea**:

- `{enchantments}`: genera una línea RGB por encantamiento real del objeto; por ejemplo, `Filo X` se mostrará como `Filo X`.
- `{abilities}`: genera una línea por cada habilidad de IxKits activada en ese objeto.
- `{item}`: inserta un nombre legible del tipo del objeto.

Ejemplo de estilo configurable en `config.yml`:

    lore:
      enchantment-line-format: "<#FBBF24>⚔ <#D1D5DB>{name} <#FDE68A>{level}"
      ability-line-format: "<#F87171>✦ <#FECACA>{name} <#9CA3AF>({cooldown})"

Puedes personalizar los colores y símbolos de estas líneas con MiniMessage/RGB. El contenido generado se guarda como parte del lore cuando aplicas la plantilla; si luego cambias encantamientos o habilidades, vuelve a aplicar la plantilla para actualizar las líneas generadas. En el editor de encantamientos, cada encantamiento muestra un icono propio (por ejemplo, 🔥 para Aspecto ígneo); los iconos pueden personalizarse en `lore.enchantment-icons` en `config.yml`. Mayús + clic izquierdo permite indicar el nivel exacto (1–32767), incluso superior al máximo normal del juego; usa niveles altos solo si encajan con el balance de tu servidor.

Al solicitar texto, pulsa Cancelar en el mensaje del chat o escribe cancelar. Ambas formas abandonan esa entrada sin guardar el cambio pendiente.

Ajustes en `plugins/IxKits/config.yml`:

    abilities:
      tnt-on-hit-cooldown-ms: 3000
      tnt-on-hit-fuse-ticks: 40
      tnt-on-hit-yield: 2.0
      lightning-on-hit-cooldown-ms: 5000
      lifesteal-on-hit-cooldown-ms: 1000
      lifesteal-on-hit-percent: 0.25
      poison-on-hit-cooldown-ms: 4500
      poison-on-hit-duration-ticks: 60
      poison-on-hit-amplifier: 0
      frost-on-hit-cooldown-ms: 3000
      frost-on-hit-duration-ticks: 40
      frost-on-hit-amplifier: 1
      flame-on-hit-cooldown-ms: 3500
      flame-on-hit-duration-ticks: 80
      knockback-on-hit-cooldown-ms: 2500
      knockback-on-hit-strength: 1.25
      knockback-on-hit-y: 0.35

Las habilidades se ejecutan en el servidor que tiene IxKits instalado y habilitado. Algunas exportaciones a formatos de otros plugins no conservan metadatos personalizados de IxKits; valida el resultado antes de poner esos objetos en producción.


### Personalizar las plantillas desde YAML

Después del primer inicio del plugin, abre `plugins/IxKits/templates/lore.yml`. Cada plantilla está agrupada por identificador y usa estos campos:

```yaml
espada_aeris:
  display: "&#38BDF8✦ &lEspada de AERIS"
  display_en: "&#38BDF8✦ &lAERIS Blade"
  description: "Una espada elemental de estilo azul."
  description_en: "A blue elemental sword style."
  category: weapons
  lore:
    - "<gradient:#0EA5E9:#A5F3FC><bold>✦ ESPADA DE AERIS ✦</bold></gradient>"
    - "<#7DD3FC>El poder de las corrientes."
    - ""
    - "<#38BDF8>⚔ Encantamientos"
    - "{enchantments}"
    - "<#38BDF8>✧ Habilidades"
    - "{abilities}"
  lore_en:
    - "<gradient:#0EA5E9:#A5F3FC><bold>✦ AERIS BLADE ✦</bold></gradient>"
    - "<#7DD3FC>The power of the currents."
    - ""
    - "<#38BDF8>⚔ Enchantments"
    - "{enchantments}"
    - "<#38BDF8>✧ Abilities"
    - "{abilities}"
```

Valores admitidos para `category`: `weapons`, `armor`, `items`, `minerals`, `consumables` y `misc`. La categoría decide en qué sección se muestra la plantilla y qué icono se utiliza. Para añadir otra plantilla, crea otro bloque con una clave única. No elimines las claves de plantillas que ya quieras conservar; puedes editar sus nombres, colores, descripciones y líneas.

Los colores y formatos admiten MiniMessage, códigos clásicos con `&` y RGB hexadecimal con `&#RRGGBB`. También funciona el estilo RGB por carácter, por ejemplo:

```text
&#F2FF00&lU&#F4F300&lS&#F6E800&lU&#F9DC00&lA&#FBD000&lR&#FDC500&lI&#FFB900&lO
```

El código mezcla colores hexadecimales individuales con negrita (`&l`). Para un degradado más sencillo, usa `<gradient:#F2FF00:#FFB900><bold>USUARIO</bold></gradient>`. En `plugins/IxKits/config.yml`, `lore.template-preview-lines` ajusta cuántas líneas se muestran como vista previa; `lore.enchantment-line-format` y `lore.ability-line-format` controlan las líneas dinámicas de encantamientos y habilidades. Después de editar la configuración o las plantillas, ejecuta `/ixkits recargar`.

### Reclamar los objetos de un proyecto

`/ixkits claim espada-pvp` entrega al jugador los objetos que hay en las dos páginas del proyecto y las cinco ranuras de equipamiento. Usa el identificador/nombre del proyecto tal como aparece en la biblioteca. Si el inventario no tiene espacio, los objetos sobrantes se dejan en el suelo cerca del jugador.

La opción está configurada en `plugins/IxKits/config.yml`:

```yaml
claim:
  enabled: true
  permission: ""
  cooldown-seconds: 0
```

Por defecto la reclamación está activada, no exige un permiso aparte y no tiene enfriamiento. Para restringirla, define `permission: "ixkits.claim"` y administra ese permiso con tu plugin de permisos. Cambia `cooldown-seconds` a un valor mayor que cero para limitar reclamaciones por jugador. Si esto se utiliza en un servidor público, protege los proyectos cuyo contenido no quieras regalar: cualquier jugador autorizado puede recibir sus objetos completos.

### Importar objetos creados con otros plugins

IxKits no depende de ItemEdit ni de otro editor de objetos. Para copiar a un proyecto un objeto personalizado que tengas en la mano (incluidos objetos editados con ItemEdit), usa:

    /ixkits importar-mano <proyecto> <página 1-2> <ranura GUI>

Ejemplo: `/ixkits importar-mano espada-pvp 1 10` guarda una copia del objeto en la ranura GUI 10 de la primera página. El objeto se guarda mediante la serialización de Bukkit/Paper para conservar los metadatos que esa versión pueda serializar. Haz una prueba con objetos de plugins externos antes de producción: metadatos privados o componentes no estándar de otro plugin pueden no ser portables entre versiones. Esta función copia el objeto; no ejecuta comandos ni necesita que ItemEdit esté instalado.

### Importación y exportación

Copia los archivos a plugins/IxKits/imports/ antes de usar /ixkits importar. La exportación nativa conserva objetos serializados de Bukkit/Paper y el equipamiento de IxKits.

La exportación a EssentialsX está en plugins/IxKits/exports/. Comprueba el resultado antes de incorporarlo a tu kits.yml: el formato legible no puede representar todos los metadatos avanzados. Los kits de EssentialsX guardados como NBT/base64 no se convierten con el importador básico; usa el serializador de EssentialsX para esos archivos.

## Catálogo online y recursos comunitarios

**Importante:** IxKits Community viene desactivado. Instalar el JAR solo habilita el menú local; para usar el catálogo compartido online, primero hay que desplegar su API.

El servicio está en IxKits/catalog-service/, usa Node.js 20+ y no necesita dependencias npm externas. Además de kits completos, admite recursos tipados: lore, palette y enchantments. Dentro del plugin, abre Community Hub con /ixkits comunidad. Las bibliotecas locales permiten aplicar un recurso con clic izquierdo o publicarlo con clic derecho. Los recursos descargados se validan y guardan en plugins/IxKits/templates/; no se ejecutan scripts del catálogo.

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

Después del despliegue, copia IXKITS_PUBLISH_TOKEN al campo catalog.publish-token e IXKITS_INTERACTION_TOKEN al campo catalog.interaction-token de plugins/IxKits/config.yml. Las publicaciones de kits usan el asistente de Herramientas del proyecto para elegir categoría y descripción; los lores, paletas y presets se comparten con clic derecho desde sus bibliotecas locales. No pongas el token de administración en el plugin ni publiques ninguno de los secretos en un repositorio o chat. Las valoraciones y favoritos requieren el token de interacción; la moderación usa el token administrativo solo en la API.

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
