package cd.h360.pos

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import cd.h360.pos.databinding.ActivityWidgetSettingsBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WidgetSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetSettingsBinding
    private var locations: List<H360WidgetUpdater.LocationOption> = emptyList()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = getString(R.string.widget_settings_title)

        val selected = H360WidgetUpdater.readEnabledModules(this).toMutableSet()
        if (selected.isEmpty()) {
            selected.addAll(listOf("sales", "stock", "offline", "activity"))
        }
        binding.switchSales.isChecked = selected.contains("sales")
        binding.switchStock.isChecked = selected.contains("stock")
        binding.switchOffline.isChecked = selected.contains("offline")
        binding.switchActivity.isChecked = selected.contains("activity")
        when (H360WidgetUpdater.readRealtimeIntervalSec(this)) {
            30 -> binding.realtime30.isChecked = true
            120 -> binding.realtime120.isChecked = true
            else -> binding.realtime60.isChecked = true
        }
        val dateFrom = H360WidgetUpdater.readFilterDateFrom(this)
        val dateTo = H360WidgetUpdater.readFilterDateTo(this)
        binding.dateFromInput.text = dateFrom
        binding.dateToInput.text = dateTo
        binding.dateFromInput.setOnClickListener { openDatePicker(binding.dateFromInput.text?.toString().orEmpty()) { picked -> binding.dateFromInput.text = picked } }
        binding.dateToInput.setOnClickListener { openDatePicker(binding.dateToInput.text?.toString().orEmpty()) { picked -> binding.dateToInput.text = picked } }

        locations = H360WidgetUpdater.readLocationOptions(this)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            locations.map { it.name }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.locationSpinner.adapter = adapter
        val selectedLocationId = H360WidgetUpdater.readFilterLocationId(this)
        val selectedIndex = locations.indexOfFirst { it.id == selectedLocationId }.takeIf { it >= 0 } ?: 0
        binding.locationSpinner.setSelection(selectedIndex)

        binding.btnSave.setOnClickListener {
            val modules = linkedSetOf<String>()
            if (binding.switchSales.isChecked) modules.add("sales")
            if (binding.switchStock.isChecked) modules.add("stock")
            if (binding.switchOffline.isChecked) modules.add("offline")
            if (binding.switchActivity.isChecked) modules.add("activity")
            val intervalSec = when (binding.realtimeIntervalGroup.checkedRadioButtonId) {
                R.id.realtime30 -> 30
                R.id.realtime120 -> 120
                else -> 60
            }
            val selectedLocation = locations.getOrNull(binding.locationSpinner.selectedItemPosition)
                ?: H360WidgetUpdater.LocationOption(0, getString(R.string.widget_all_locations))
            val start = normalizeDate(binding.dateFromInput.text?.toString())
            val end = normalizeDate(binding.dateToInput.text?.toString())

            H360WidgetUpdater.rememberEnabledModules(this, modules)
            H360WidgetUpdater.rememberRealtimeIntervalSec(this, intervalSec)
            H360WidgetUpdater.rememberWidgetFilters(
                this,
                start,
                end,
                selectedLocation.id,
                selectedLocation.name
            )
            H360WidgetUpdater.refreshAllWidgets(this)
            finish()
        }
    }

    private fun openDatePicker(seed: String, onPicked: (String) -> Unit) {
        val cal = Calendar.getInstance()
        val parsed = runCatching { dateFormatter.parse(seed) }.getOrNull()
        if (parsed != null) cal.time = parsed
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                onPicked(dateFormatter.format(picked.time))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun normalizeDate(raw: String?): String {
        val parsed = runCatching { dateFormatter.parse(raw.orEmpty()) }.getOrNull()
        return if (parsed != null) dateFormatter.format(parsed) else dateFormatter.format(Calendar.getInstance().time)
    }
}
