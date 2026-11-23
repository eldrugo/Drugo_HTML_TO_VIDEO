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

## Audio en la versión 0.2

Se pueden añadir manualmente hasta 12 pistas MP3/WAV/M4A/AAC/OGG/FLAC. Cada pista tiene tiempo de inicio (segundos) y volumen (0 a 2). FFmpeg mezcla las pistas, recorta al tiempo del vídeo y codifica AAC 192 kb/s en el MP4. La sincronización es respecto al tiempo de fotograma, no respecto a la reproducción real del navegador.

**No se captura automáticamente** el audio generado internamente por speechSynthesis, Web Audio, ni elementos HTML audio/video. Para incluir narración TTS, exportar o generar previamente un archivo de audio y añadirlo como pista. Los efectos requieren archivos separados o una mezcla previa. Si la animación se captura en modo tiempo real y el equipo no mantiene el FPS, puede haber desfase entre movimiento y sonido.
