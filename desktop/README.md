# DRUGO HTML TO VIDEO · Windows 11

Aplicación experimental Electron para convertir HTML animado a MP4 (H.264), 1080x1920 o 720x1280, 24/30 fps. Genera instalador NSIS y ejecutable portable en GitHub Releases mediante ejecución manual.

## Requisitos del HTML

Debe exponer `window.drugoRenderAt(ms)`, que dibuje sincrónicamente el fotograma correspondiente al instante `ms`. El exportador no captura audio. Los recursos externos requieren conexión y los HTML que dependan de un reloj real, CSS animations o Web Audio no se exportarán fielmente sin adaptación.

## Limitaciones importantes

El motor inicial utiliza `capturePage()` y JPEG por fotograma hacia FFmpeg. Es un prototipo funcional, **no una solución de alto rendimiento**. No se ha medido aún en el Ryzen 5 PRO 4650U; no se garantiza una exportación más rápida que Android. El siguiente hito es instrumentar rendimiento y sustituir capturas por una vía de composición acelerada si es viable.

## Ejecutar localmente

```powershell
cd desktop
npm install
npm start
```

## Compilar en GitHub

Actions → **Windows instalable y portable (manual)** → Run workflow. Se publican los dos EXE en Releases, sin upload-artifact ni caché de Actions. La compilación sí consume minutos de Actions.
