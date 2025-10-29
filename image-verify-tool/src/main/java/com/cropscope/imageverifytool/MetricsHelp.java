/* ------------------------------------------------------
 * Copyright [2025] [Copyright 2025 Alfonso Antolínez García and Marina Antolínez Cabrero]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file is part of the CropScope(R) suite.
 * Authors:
 * - Alfonso Antolínez García
 * - Marina Antolínez Cabrero
 * -------------------------------------------------------- */

package com.cropscope.imageverifytool;


import javax.swing.*;
import java.awt.*;

public final class MetricsHelp {
    private MetricsHelp() {}

    public static void showMetricsOverview(Component parent) {
        JTextPane pane = new JTextPane();
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setText(getMetricsHelpHtml());
        pane.setCaretPosition(0);

        JScrollPane sp = new JScrollPane(pane);
        sp.setPreferredSize(new Dimension(720, 520));

        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(parent),
                sp, "Metrics overview", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String getMetricsHelpHtml() {
        return "<html><body style='font-family:sans-serif;'>"
                + "<h2>Image similarity metrics</h2>"
                + "<p>This tool compares <b>consecutive images</b> (sorted by name) in a folder using one of three metrics. "
                + "You can adjust thresholds directly or choose a preset (Loose/Medium/Strict). "
                + "“Near-duplicate” means the value meets or exceeds the chosen threshold.</p>"

                + "<h3>Perceptual hash (dHash)</h3>"
                + "<ul>"
                + "<li>Builds a compact 64-bit fingerprint from image gradients; robust to tiny changes (re-encoding, small blur, slight resize).</li>"
                + "<li>Similarity is measured by <i>Hamming distance</i> between two hashes (number of differing bits).</li>"
                + "<li><b>Lower is more similar</b>. 0 = identical hash.</li>"
                + "</ul>"
                + "<p><b>Typical guide:</b> ≤3 very close, 4–5 very similar, 6–12 similar, &gt;12 different.</p>"

                + "<h3>PSNR (Peak Signal-to-Noise Ratio)</h3>"
                + "<ul>"
                + "<li>Derived from pixel-wise MSE between images (after resizing both to a common size); reported in dB.</li>"
                + "<li><b>Higher is more similar</b>. ∞ dB when images are exactly identical.</li>"
                + "</ul>"
                + "<p><b>Typical guide:</b> ≥45 dB near-identical, 40–45 dB very similar, 35–40 dB similar, &lt;35 dB different.</p>"

                + "<h3>SSIM (Structural Similarity)</h3>"
                + "<ul>"
                + "<li>Perceptual metric (0..1) that models luminance/contrast/structure; better aligned with human vision than PSNR.</li>"
                + "<li><b>Higher is more similar</b>. 1.0 means identical.</li>"
                + "</ul>"
                + "<p><b>Typical guide:</b> ≥0.99 near-identical, ≥0.98 very similar, ≥0.95 similar.</p>"

                + "<h3>Presets</h3>"
                + "<table border='1' cellpadding='6' cellspacing='0'>"
                + "<tr><th>Preset</th><th>dHash ≤</th><th>PSNR ≥ (dB)</th><th>SSIM ≥</th></tr>"
                + "<tr><td>Loose</td><td>8</td><td>35</td><td>0.95</td></tr>"
                + "<tr><td><b>Medium (default)</b></td><td><b>5</b></td><td><b>40</b></td><td><b>0.98</b></td></tr>"
                + "<tr><td>Strict</td><td>3</td><td>45</td><td>0.99</td></tr>"
                + "</table>"

                + "<h3>Notes</h3>"
                + "<ul>"
                + "<li>Images are compared at a shared size (min width/height of the pair) to avoid resolution mismatches.</li>"
                + "<li>dHash is fast and good for pre-filtering; PSNR/SSIM are stronger signals when you need higher confidence.</li>"
                + "<li>“Near-duplicate” is application-specific; calibrate thresholds on your data for best results.</li>"
                + "</ul>"
                + "</body></html>";
    }
}

