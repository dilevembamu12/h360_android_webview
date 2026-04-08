package cd.h360.pos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cd.h360.pos.databinding.ActivityWidgetSettingsBinding

class WidgetSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetSettingsBinding

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

            H360WidgetUpdater.rememberEnabledModules(this, modules)
            H360WidgetUpdater.rememberRealtimeIntervalSec(this, intervalSec)
            H360WidgetUpdater.refreshAllWidgets(this)
            finish()
        }
    }
}
