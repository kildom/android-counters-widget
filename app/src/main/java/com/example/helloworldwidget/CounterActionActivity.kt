package com.example.helloworldwidget

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.WindowManager
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONObject

class CounterActionActivity : Activity() {
    private val counterId by lazy { intent.getStringExtra(EXTRA_COUNTER_ID).orEmpty() }
    private val counterName by lazy { intent.getStringExtra(EXTRA_COUNTER_NAME).orEmpty() }
    private val counterPeriod by lazy { intent.getIntExtra(EXTRA_COUNTER_PERIOD, 0) }
    private val counterTarget by lazy { intent.getStringExtra(EXTRA_COUNTER_TARGET).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_ADD -> showEditDialog(null)
            ACTION_WIDGET_OPTIONS -> showWidgetOptions()
            else -> showCounterMenu()
        }
    }

    private fun showWidgetOptions() {
        val items = arrayOf(getString(R.string.refresh), getString(R.string.setup))
        AlertDialog.Builder(this)
            .setItems(items) { _, selected ->
                when (selected) {
                    0 -> CounterRepository.refreshNow(this) {
                        CounterWidgetProvider.updateAllWidgets(this)
                        finish()
                    }
                    1 -> {
                        startActivity(
                            android.content.Intent(this, SettingsActivity::class.java)
                                .putExtra(SettingsActivity.EXTRA_OPENED_FROM_WIDGET, true),
                        )
                        finish()
                    }
                    else -> finish()
                }
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showCounterMenu() {
        val items = arrayOf(
            getString(R.string.reset),
            getString(R.string.edit),
            getString(R.string.postpone),
            getString(R.string.speed_up),
            getString(R.string.delete),
        )
        AlertDialog.Builder(this)
            .setTitle(counterName)
            .setItems(items) { _, selected ->
                when (selected) {
                    0 -> confirmReset()
                    1 -> showEditDialog(Counter(counterId, counterName, counterPeriod, counterTarget))
                    2 -> showDayOffsetDialog(1)
                    3 -> showDayOffsetDialog(-1)
                    4 -> confirmDelete()
                }
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_counter)
            .setMessage(getString(R.string.reset_confirmation, counterName))
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setPositiveButton(R.string.reset) { _, _ ->
                sendEdit(targetDate = Counter.todayPlusDays(counterPeriod))
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showEditDialog(counter: Counter?) {
        val nameInput = EditText(this).apply {
            hint = getString(R.string.name)
            setSingleLine(true)
            if (counter != null) setText(counter.name)
        }
        val periodInput = EditText(this).apply {
            hint = getString(R.string.period_days)
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            if (counter != null) setText(counter.period.toString())
        }
        val content = dialogFields(nameInput, periodInput)
        AlertDialog.Builder(this)
            .setTitle(if (counter == null) R.string.add_counter else R.string.edit_counter)
            .setView(content)
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setPositiveButton(android.R.string.ok, null)
            .setOnCancelListener { finish() }
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = nameInput.text.toString().trim()
                        val period = periodInput.text.toString().toIntOrNull()
                        if (name.isBlank()) {
                            nameInput.error = getString(R.string.name_required)
                            return@setOnClickListener
                        }
                        if (period == null || period < 1) {
                            periodInput.error = getString(R.string.period_required)
                            return@setOnClickListener
                        }
                        if (counter == null) {
                            send(JSONObject()
                                .put("command", "add")
                                .put("name", name)
                                .put("period", period))
                        } else {
                            send(JSONObject()
                                .put("command", "edit")
                                .put("id", counter.id)
                                .put("name", name)
                                .put("period", period))
                        }
                        dismiss()
                    }
                }
                show()
            }
    }

    private fun showDayOffsetDialog(direction: Int) {
        val daysInput = EditText(this).apply {
            hint = getString(R.string.days)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1")
            setSelectAllOnFocus(true)
        }
        val inputContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val margin = (24 * resources.displayMetrics.density).toInt()
            setPadding(margin, margin / 2, margin, 0)
            addView(daysInput, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        AlertDialog.Builder(this)
            .setTitle(if (direction > 0) R.string.postpone else R.string.speed_up)
            .setView(inputContainer)
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setPositiveButton(android.R.string.ok, null)
            .setOnCancelListener { finish() }
            .create()
            .apply {
                setOnShowListener {
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    daysInput.requestFocus()
                    daysInput.post {
                        daysInput.selectAll()
                    }
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val days = daysInput.text.toString().toIntOrNull()
                        if (days == null || days < 1) {
                            daysInput.error = getString(R.string.period_required)
                            return@setOnClickListener
                        }
                        try {
                            val updatedDate = Counter.addDays(counterTarget, days * direction)
                            sendEdit(targetDate = updatedDate)
                            dismiss()
                        } catch (exception: Exception) {
                            daysInput.error = exception.message
                        }
                    }
                }
                show()
            }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_counter)
            .setMessage(getString(R.string.delete_confirmation, counterName))
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setPositiveButton(R.string.delete) { _, _ ->
                send(JSONObject().put("command", "delete").put("id", counterId))
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun sendEdit(targetDate: String) {
        send(JSONObject()
            .put("command", "edit")
            .put("id", counterId)
            .put("targetDate", targetDate))
    }

    private fun send(command: JSONObject) {
        CounterRepository.sendCommand(this, command) { result ->
            result.fold(
                onSuccess = {
                    CounterWidgetProvider.updateAllWidgets(this)
                    finish()
                },
                onFailure = { exception ->
                    CounterWidgetProvider.updateAllWidgets(this)
                    Toast.makeText(this, exception.message ?: getString(R.string.request_failed), Toast.LENGTH_LONG).show()
                    finish()
                },
            )
        }
    }

    private fun dialogFields(first: EditText, second: EditText): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(first, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            addView(second, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }

    companion object {
        const val EXTRA_ACTION = "action"
        const val EXTRA_COUNTER_ID = "counter_id"
        const val EXTRA_COUNTER_NAME = "counter_name"
        const val EXTRA_COUNTER_PERIOD = "counter_period"
        const val EXTRA_COUNTER_TARGET = "counter_target"
        const val ACTION_ADD = "add"
        const val ACTION_WIDGET_OPTIONS = "widget_options"
    }
}
