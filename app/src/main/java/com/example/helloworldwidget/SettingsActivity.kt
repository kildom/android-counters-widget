package com.example.helloworldwidget

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        layout.addView(TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 22f
        })
        layout.addView(TextView(this).apply {
            text = getString(R.string.settings_instructions)
            setPadding(0, padding / 2, 0, padding / 2)
        })
        val endpointInput = EditText(this).apply {
            hint = getString(R.string.endpoint_hint)
            setSingleLine(true)
            setText(CounterRepository.endpoint(this@SettingsActivity))
        }
        layout.addView(endpointInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        val saveButton = Button(this).apply { text = getString(R.string.save_url) }
        layout.addView(saveButton)
        setContentView(layout)

        saveButton.setOnClickListener {
            try {
                CounterRepository.saveEndpoint(this, endpointInput.text.toString())
                CounterRepository.refreshIfStale(this) {
                    CounterWidgetProvider.updateAllWidgets(this)
                    finish()
                }
            } catch (exception: Exception) {
                endpointInput.error = exception.message
            }
        }
    }
}
