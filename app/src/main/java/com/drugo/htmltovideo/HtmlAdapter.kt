package com.drugo.htmltovideo

/** Apollo 13 uses JavaScript RAF rendering, so adapt it to an explicit frame clock.
 * Other HTML must expose window.drugoRenderAt(ms), or be adapted separately.
 */
object HtmlAdapter {
    fun adapt(source: String): String {
        if (!source.contains("function animate(timestamp)") || !source.contains("const SCENES = [")) {
            return source
        }
        var html = source
        // Prevent the original animation loop from racing the encoder's frame clock.
        html = html.replace("requestAnimationFrame(animate);",
            "if (!window.__drugoExportMode) requestAnimationFrame(animate);")
        val marker = "<script>\n        /**"
        if (html.contains(marker)) {
            html = html.replace(marker, "<script>window.__drugoExportMode=true;</script>\n" + marker)
        } else {
            html = html.replace("<body", "<script>window.__drugoExportMode=true;</script><body")
        }
        val bridge = """
            <script>
            window.drugoRenderAt = function(ms) {
                const totalMs = SCENES.reduce((a,s)=>a+s.durationMs,0);
                const desired = Math.max(0,Math.min(totalMs-0.001, ms * totalMs / (window.__drugoDurationMs || 45000)));
                let left = desired, idx = 0;
                for (let j=0;j<SCENES.length;j++) {
                    idx=j;
                    if(left < SCENES[j].durationMs) break;
                    left -= SCENES[j].durationMs;
                }
                if (currentSceneIdx !== idx) {
                    currentSceneIdx=idx; particles=[]; updateUI();
                }
                sceneElapsed = left;
                isAutoPlaying = false;
                lastTimestamp = ms;
                animate(ms);
            };
            </script>
        """.trimIndent()
        return html.replace("</body>", bridge + "\n</body>")
    }
}
