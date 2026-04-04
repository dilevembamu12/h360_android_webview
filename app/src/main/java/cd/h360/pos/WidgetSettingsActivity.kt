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

        binding.btnSave.setOnClickListener {
            val modules = linkedSetOf<String>()
            if (binding.switchSales.isChecked) modules.add("sales")
            if (binding.switchStock.isChecked) modules.add("stock")
            if (binding.switchOffline.isChecked) modules.add("offline")
            if (binding.switchActivity.isChecked) modules.add("activity")

            H360WidgetUpdater.rememberEnabledModules(this, modules)
            H360WidgetUpdater.refreshAllWidgets(this)
            finish()
        }
    }
}
