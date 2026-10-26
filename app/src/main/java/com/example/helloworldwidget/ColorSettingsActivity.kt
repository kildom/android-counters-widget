package com.example.helloworldwidget

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class ColorSettingsActivity : Activity() {
    private data class ColorOption(
        val label: Int,
        val currentColor: () -> Int,
        val update: (Int) -> Unit,
    )

    private lateinit var options: List<ColorOption>
    private var widgetBackground = 0
    private var itemText = 0
    private var overdueText = 0
    private var positiveProgress = 0
    private var negativeProgress = 0
    private var progressBackground = 0
    private var progressText = 0
    private var buttonBackground = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetBackground = CounterRepository.backgroundColor(this)
        itemText = CounterRepository.textColor(this)
        overdueText = CounterRepository.overdueColor(this)
        positiveProgress = CounterRepository.positiveProgressColor(this)
        negativeProgress = CounterRepository.negativeProgressColor(this)
        progressBackground = CounterRepository.progressBackgroundColor(this)
        progressText = CounterRepository.progressTextColor(this)
        buttonBackground = CounterRepository.buttonBackgroundColor(this)

        options = listOf(
            ColorOption(R.string.widget_background_color, { widgetBackground }) { widgetBackground = it },
            ColorOption(R.string.text_color, { itemText }) { itemText = it },
            ColorOption(R.string.overdue_color, { overdueText }) { overdueText = it },
            ColorOption(R.string.positive_progress_color, { positiveProgress }) { positiveProgress = it },
            ColorOption(R.string.negative_progress_color, { negativeProgress }) { negativeProgress = it },
            ColorOption(R.string.progress_background_color, { progressBackground }) { progressBackground = it },
            ColorOption(R.string.progress_text_color, { progressText }) { progressText = it },
            ColorOption(R.string.button_background_color, { buttonBackground }) { buttonBackground = it },
        )

        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundResource(R.drawable.settings_panel)
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.widget_colors_title)
            textSize = 22f
        })

        options.forEach { option ->
            val button = colorButton(option.currentColor())
            addField(content, getString(option.label), button)
            button.setOnClickListener {
                showColorPicker(option.currentColor()) { selected ->
                    option.update(selected)
                    updateColorButton(button, selected)
                }
            }
            button
        }

        content.addView(Button(this).apply {
            text = getString(R.string.save_colors)
            setOnClickListener {
                CounterRepository.saveWidgetColors(
                    this@ColorSettingsActivity,
                    widgetBackground,
                    itemText,
                    overdueText,
                    positiveProgress,
                    negativeProgress,
                    progressBackground,
                    progressText,
                    buttonBackground,
                )
                CounterWidgetProvider.updateAllWidgets(this@ColorSettingsActivity)
                finish()
            }
        })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        })
        window.setLayout(
            (resources.displayMetrics.widthPixels - padding * 2).coerceAtLeast(1),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        window.setGravity(Gravity.CENTER)
    }

    private fun colorButton(color: Int) = Button(this).apply {
        updateColorButton(this, color)
    }

    private fun updateColorButton(button: Button, color: Int) {
        button.text = String.format("#%06X", 0xFFFFFF and color)
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        button.setTextColor(if (isLight(color)) Color.BLACK else Color.WHITE)
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
            addView(
                preview,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
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
        parent.addView(
            input,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
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
}
