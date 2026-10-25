package com.example.helloworldwidget

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
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

        var backgroundColor = CounterRepository.backgroundColor(this)
        var textColor = CounterRepository.textColor(this)
        var overdueColor = CounterRepository.overdueColor(this)
        val backgroundButton = colorButton(backgroundColor)
        addField(content, getString(R.string.background_color), backgroundButton)
        val textButton = colorButton(textColor)
        addField(content, getString(R.string.text_color), textButton)
        val overdueButton = colorButton(overdueColor)
        addField(content, getString(R.string.overdue_color), overdueButton)
        backgroundButton.setOnClickListener {
            showColorPicker(backgroundColor) { selected ->
                backgroundColor = selected
                updateColorButton(backgroundButton, selected)
            }
        }
        textButton.setOnClickListener {
            showColorPicker(textColor) { selected ->
                textColor = selected
                updateColorButton(textButton, selected)
            }
        }
        overdueButton.setOnClickListener {
            showColorPicker(overdueColor) { selected ->
                overdueColor = selected
                updateColorButton(overdueButton, selected)
            }
        }

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
            CounterRepository.saveColors(this, backgroundColor, textColor, overdueColor)
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

    private fun colorButton(color: Int) = Button(this).apply {
        setTextColor(Color.WHITE)
        updateColorButton(this, color)
    }

    private fun updateColorButton(button: Button, color: Int) {
        button.text = String.format("#%06X", 0xFFFFFF and color)
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        val luminance = 0.2126 * linear(Color.red(color)) +
            0.7152 * linear(Color.green(color)) +
            0.0722 * linear(Color.blue(color))
        button.setTextColor(if (luminance < 0.179) Color.WHITE else Color.BLACK)
    }

    private fun showColorPicker(initialColor: Int, onSelected: (Int) -> Unit) {
        val preview = TextView(this).apply {
            text = String.format("#%06X", 0xFFFFFF and initialColor)
            gravity = Gravity.CENTER
            setTextColor(if (isLight(initialColor)) Color.BLACK else Color.WHITE)
            setBackgroundColor(initialColor)
            minHeight = (48 * resources.displayMetrics.density).toInt()
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(preview, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        val channels = intArrayOf(
            Color.red(initialColor),
            Color.green(initialColor),
            Color.blue(initialColor),
        )
        arrayOf(R.string.red, R.string.green, R.string.blue).forEachIndexed { index, labelId ->
            controls.addView(TextView(this).apply { text = getString(labelId) })
            controls.addView(SeekBar(this).apply {
                max = 255
                progress = channels[index]
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        channels[index] = progress
                        val selected = Color.rgb(channels[0], channels[1], channels[2])
                        preview.text = String.format("#%06X", 0xFFFFFF and selected)
                        preview.setBackgroundColor(selected)
                        preview.setTextColor(if (isLight(selected)) Color.BLACK else Color.WHITE)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                })
            })
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_color)
            .setView(controls)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onSelected(Color.rgb(channels[0], channels[1], channels[2]))
            }
            .show()
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

    private fun isLight(color: Int): Boolean =
        0.2126 * linear(Color.red(color)) +
            0.7152 * linear(Color.green(color)) +
            0.0722 * linear(Color.blue(color)) >= 0.179

    private fun linear(channel: Int): Double {
        val normalized = channel / 255.0
        return if (normalized <= 0.04045) {
            normalized / 12.92
        } else {
            Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
    }

    companion object {
        const val EXTRA_OPENED_FROM_WIDGET = "opened_from_widget"
    }
}
