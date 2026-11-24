package dev.nijatva.reader.demo

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The harness. It opens a local HTML file, hands the string to the engine, and shows the
 * result and the CoverageReport. There is no WebView here and there never will be - if a
 * document cannot be rendered, the demo says so rather than falling back to one.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = TextView(this).apply {
            text = "M0: the modules exist and build. The tokenizer arrives in M1.\n\n" +
                "Nothing is rendered yet, and nothing pretends to be."
            textSize = 16f
            setPadding(48, 96, 48, 48)
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text)
        }

        setContentView(ScrollView(this).apply { addView(column) })
    }
}
