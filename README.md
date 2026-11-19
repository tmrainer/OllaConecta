# OllaConecta

Aplicación para gestionar ollas comunes y comedores: registrar personas, pasar
asistencia, calcular raciones y recaudación, preparar el formato B-1 y recordar
las fechas de entrega.

El repositorio contiene una **aplicación web instalable (PWA)**, una **aplicación
Android nativa** y servicios opcionales de **Firebase y Google Apps Script**.
La web funciona con datos guardados en el navegador; no necesita una cuenta ni
configurar la nube para empezar.

## Inicio rápido: ejecutar la web

Necesitas Python 3 y un navegador actualizado. Abre una terminal en la raíz del
repositorio y ejecuta:

```sh
python -m http.server 8000 --bind 127.0.0.1 --directory web
```

En Windows también puedes usar `py -3` en lugar de `python`.

Abre [OllaConecta en tu equipo](http://localhost:8000). Para detener el servidor,
presiona `Ctrl+C` en la terminal.

La web se sirve directamente desde `web/`: no requiere instalar paquetes npm ni
compilar archivos. Usa el servidor local en lugar de abrir `index.html` con
doble clic, porque los módulos JavaScript y el service worker necesitan HTTP o
HTTPS.

## Guía de uso

### 1. Configurar el perfil

En **Inicio → Ajustes**, completa el nombre y el rol de quien usa el teléfono
(presidenta, tesorera o apoyo). Allí también puedes cambiar el tamaño de letra,
el tema y la lectura en voz alta, si el dispositivo la admite.

El perfil ayuda a completar datos del B-1; es independiente de una cuenta de
Google. Desde **Inicio → ¿Cómo se usa?** puedes repetir el recorrido de ayuda.

### 2. Registrar el padrón

1. Abre **Padrón → Inscribir a una persona**.
2. Completa sus datos y confirma la inscripción. Si el dispositivo permite leer
   el DNI con la cámara, puedes usar esa opción y revisar los datos reconocidos.
3. En **Personas afiliadas**, busca y revisa los registros existentes.

Para cargar una lista existente, usa **Cargar un padrón desde Excel → Elegir
archivo de Excel**. También puedes pegar una tabla. Revisa la vista previa y
confirma con **Agregar**. Hay un archivo para practicar en
[`herramientas/padron-de-ejemplo.xlsx`](herramientas/padron-de-ejemplo.xlsx).

La sección **Exportar el padrón** permite descargarlo en Excel o PDF. El Excel
exportado también puede importarse en otro teléfono.

### 3. Pasar asistencia

1. Entra en **Asistencia** o toca **Pasar lista hoy** en Inicio.
2. Toca a cada persona que asistió; puedes buscar por nombre o documento.
3. Registra el tipo de menú y las raciones de personas sin inscribir cuando
   corresponda.
4. Revisa **Precio del menú**, con los valores de menú normal y ayuda social,
   para calcular la recaudación.

Los cambios se guardan automáticamente. **Otros días** permite consultar y
editar fechas anteriores; **Resumen y exportar** genera el reporte del período
en Excel o PDF.

### 4. Preparar el formato B-1

Abre **B-1** y sigue los pasos: **Centro de atención**, **Periodo que se rinde**,
**Ingresos**, **Egresos**, **Firmas** y **Revisar antes de entregar**.

En Egresos, usa **Agregar compra** para registrar los gastos. Revisa los avisos
y los totales antes de exportar el B-1 en PDF o Excel. Los borradores se guardan
automáticamente y los períodos anteriores quedan en **Meses anteriores**.

### 5. Organizar las entregas

En **Entregas**, configura cada cuánto entregas el B-1 y revisa las próximas
fechas. Inicio y Entregas muestran cuánto falta para la siguiente entrega.

### 6. Guardar y trasladar los datos

En **Inicio → Tus datos** puedes:

- **Guardar una copia** del padrón, la asistencia, los B-1, las entregas y la
  configuración de trabajo. Puedes proteger el archivo con contraseña.
- Recuperar una copia guardada y revisar qué datos se van a restaurar.
- Pasar el padrón, la asistencia o las entregas a otro teléfono mediante QR;
  la recepción depende de la cámara y del soporte del dispositivo.

El perfil y las preferencias de apariencia de la web pertenecen al dispositivo
y no se incluyen en las copias. Conserva una copia antes de borrar los datos del
navegador o cambiar de equipo. Para encontrar los mismos datos, utiliza el mismo
navegador y la misma dirección, incluido el puerto.

### Instalar y usar sin conexión

La PWA guarda los archivos de la aplicación con un service worker. Abre la web
con conexión y deja que termine de cargar antes de usarla sin internet. Si el
navegador ofrece **Instalar** o **Añadir a la pantalla de inicio**, puedes abrirla
después como una aplicación.

La instalación y la cámara requieren un contexto seguro: HTTPS en un sitio
publicado o `localhost` durante el desarrollo. El inicio de sesión, la
sincronización y los envíos en la nube necesitan conexión cuando están habilitados.

## Organización del código

```text
OllaConecta/
├── web/                       Aplicación web, servida sin compilación
│   ├── *.html                 Pantallas y puntos de entrada
│   ├── estilos.css            Estilos compartidos
│   ├── sw.js                  Recursos y actualización para uso sin conexión
│   ├── manifest.webmanifest   Configuración de la PWA
│   ├── js/
│   │   ├── nucleo/            Modelo, estado, cálculos, validaciones e importación
│   │   ├── documentos/        Reportes PDF y Excel, lectura de Excel y formato B-1
│   │   ├── camara/            Captura y lectura de DNI y QR
│   │   ├── interfaz/          Componentes compartidos, preferencias y avisos
│   │   ├── paginas/           Controlador de cada pantalla
│   │   ├── nube/              Configuración, cuentas, sincronización y envíos
│   │   └── pruebas/           Casos de prueba y su presentación en el navegador
│   ├── vendor/                Librerías externas incluidas en el repositorio
│   ├── img/                   Imágenes
│   └── fuentes/               Tipografías y licencia
├── nativo/
│   ├── app/                   Aplicación Android con Jetpack Compose
│   ├── nucleo/                Lógica Kotlin y pruebas JVM
│   ├── perfil/                Generación de perfiles de rendimiento Android
│   └── herramientas/          Datos ficticios y comparación con la web
├── nube/
│   ├── apps-script/           Generador de reportes y envío de correos
│   ├── firestore.rules        Reglas de acceso a los datos
│   ├── firestore.indexes.json Índices de consultas
│   └── pruebas-*.mjs          Pruebas con el emulador de Firestore
├── herramientas/              Prueba de humo, diagnóstico y padrón de ejemplo
└── .github/workflows/          Validación y publicación con GitHub Actions
```

Los HTML cargan sus controladores desde `web/js/paginas/`; estos utilizan la
interfaz compartida y los módulos del núcleo. Las herramientas de Android y el
generador de Apps Script también reutilizan módulos de la web.

Al agregar o mover un archivo web, actualiza sus importaciones, los puntos de
entrada HTML y la lista `ARCHIVOS` de `web/sw.js`. Las librerías de `vendor/`
se mantienen separadas del código de la aplicación.

`nube/apps-script/reportes.js` es un archivo generado: modifica los originales
en `web/js/` y vuelve a generarlo con `npm run armar` desde `nube/`.

## Pruebas

### Web

Requiere **Node.js 22** (versión usada en CI) y Chrome o Chromium. Desde la raíz:

```sh
node herramientas/humo.mjs
```

Si el navegador no se encuentra automáticamente, indica su ejecutable. Por
ejemplo, en PowerShell:

```powershell
$env:CHROME = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
$env:TZ = 'America/Lima'
node herramientas/humo.mjs
```

En Linux:

```sh
CHROME=/usr/bin/google-chrome TZ=America/Lima node herramientas/humo.mjs
```

La prueba inicia su propio servidor y un navegador con perfil temporal. Revisa
las pantallas, importaciones, exportaciones, guardado, uso sin conexión y los
casos de `pruebas.html`. No utiliza los datos de tu navegador habitual.

Con el servidor de desarrollo activo, también puedes abrir
[las pruebas en el navegador](http://localhost:8000/pruebas.html).

### Servicios en la nube

Requiere Node.js 22, npm, **Java 21** y `curl` en el `PATH`:

```sh
cd nube
npm ci
npm run pruebas
```

Se inicia un emulador local de Firestore en `127.0.0.1:8080`, con el proyecto
ficticio `demo-ollaconecta`. Se prueban las reglas, la sincronización y el envío
de reportes; los correos se simulan. No hace falta un proyecto real de Firebase.

## Aplicación Android

Abre la carpeta `nativo/` como proyecto en Android Studio. Necesitas **JDK 21**
y el **SDK de Android 36**, según la configuración de Gradle. La aplicación
requiere Android API 24 o superior.

Desde la raíz del repositorio, en Windows:

```powershell
cd nativo
.\gradlew.bat :nucleo:test --no-daemon
.\gradlew.bat :app:assembleDebug
```

En Linux o macOS usa `./gradlew` en lugar de `.\gradlew.bat`. Las pruebas del
núcleo corren en la JVM, aunque configurar el proyecto completo también
requiere el SDK de Android.

El APK de desarrollo se genera en
`nativo/app/build/outputs/apk/debug/app-debug.apk`. Se instala como **OllaConecta
prueba**, con un identificador distinto al de la versión normal.

## Integraciones opcionales

La configuración incluida en
[`web/js/nube/firebase-config.js`](web/js/nube/firebase-config.js) tiene
`projectId` vacío; por eso la web funciona de manera local. No es necesario
cambiarla para seguir la guía de uso.

Para una instalación con cuentas y sincronización, se necesita un proyecto de
Firebase con Authentication de Google, Firestore, sus dominios autorizados y
las reglas e índices de `nube/`. También hay que revisar la política
`Content-Security-Policy` de las páginas: la copia actual restringe las
conexiones con `connect-src 'self'`. Completar las claves por sí solo no habilita
la integración.

Con esos servicios configurados, **Tus datos → Tu cuenta** permite entrar con
Google y aceptar una invitación a una olla. **Coordinación** administra ollas y
miembros según los permisos de la cuenta; el perfil local no concede esos permisos.

Comandos disponibles desde `nube/`:

| Comando | Uso |
| --- | --- |
| `npm ci` | Instalar las dependencias fijadas en el archivo de bloqueo. |
| `npm run pruebas` | Ejecutar las pruebas contra el emulador local. |
| `npm run armar` | Regenerar `apps-script/reportes.js` a partir de la web. |
| `npm run publicar` | Publicar las reglas e índices en el proyecto Firebase seleccionado y autenticado en la CLI. |

El envío de reportes usa `nube/apps-script/Codigo.js` junto con el
`reportes.js` generado. En una instalación de Google Apps Script, la función
`instalar()` programa `revisar()` cada diez minutos. El correo de destino debe
estar configurado en `ajustes/envio` de Firestore; generar `reportes.js` por sí
solo no envía correos ni instala el servicio.

## Problemas frecuentes

| Situación | Qué revisar |
| --- | --- |
| La web no carga al abrir el HTML directamente | Ejecuta el servidor local y entra por `http://localhost:8000`. |
| La cámara o la instalación no están disponibles | Usa HTTPS o localhost y revisa los permisos y el soporte del navegador. |
| Aparece un padrón vacío en otro navegador o dirección | Los datos locales pertenecen a ese navegador y origen; recupera una copia o transfiérelos. |
| No aparecen cuentas o sincronización | Es el comportamiento esperado con `projectId` vacío. |
| La prueba de humo no encuentra Chrome | Define `CHROME` con la ruta real del ejecutable. |
| El emulador no inicia | Revisa Java 21 en el `PATH` y que el puerto 8080 esté libre. |

## Tecnologías

- **Web:** HTML, CSS, JavaScript con módulos ES y PWA (service worker).
- **Nube:** Firebase Authentication, Firestore y Google Apps Script.
- **Android:** Kotlin, Jetpack Compose, CameraX, ML Kit y Gradle.
- **Herramientas:** Node.js, GitHub Actions y GitHub Pages. El flujo de Pages
  publica `web/` después de superar la prueba de humo.
