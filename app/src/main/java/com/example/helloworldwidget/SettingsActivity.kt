package com.example.helloworldwidget

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : Activity() {
    private var openedFromWidget = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedFromWidget = intent.getBooleanExtra(EXTRA_OPENED_FROM_WIDGET, false)

        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundResource(R.drawable.settings_panel)
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 22f
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.settings_instructions)
            setPadding(0, padding / 2, 0, padding / 2)
        })
        val endpointInput = EditText(this).apply {
            hint = getString(R.string.endpoint_hint)
            setSingleLine(true)
            setText(CounterRepository.endpoint(this@SettingsActivity))
        }
        addField(content, getString(R.string.backend_url), endpointInput)

        content.addView(Button(this).apply {
            text = getString(R.string.configure_colors)
            setOnClickListener {
                startActivity(android.content.Intent(this@SettingsActivity, ColorSettingsActivity::class.java))
            }
        })

        val saveButton = Button(this).apply { text = getString(R.string.save_url) }
        content.addView(saveButton)
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        setContentView(scrollView)
        window.setLayout(
            (resources.displayMetrics.widthPixels - padding * 2).coerceAtLeast(1),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        window.setGravity(Gravity.CENTER)

        saveButton.setOnClickListener {
            try {
                CounterRepository.saveEndpoint(this, endpointInput.text.toString())
            } catch (exception: Exception) {
                endpointInput.error = exception.message
                return@setOnClickListener
            }
            CounterWidgetProvider.updateAllWidgets(this)

            if (CounterRepository.endpoint(this).isBlank()) {
                finish()
            } else {
                CounterRepository.refreshIfStale(this) {
                    CounterWidgetProvider.updateAllWidgets(this)
                    finish()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (openedFromWidget && !isChangingConfigurations) {
            finish()
        }
    }

    private fun addField(parent: LinearLayout, label: String, input: android.view.View) {
        parent.addView(TextView(this).apply {
            text = label
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
        })
        parent.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
    }

    companion object {
        const val EXTRA_OPENED_FROM_WIDGET = "opened_from_widget"
    }
}
