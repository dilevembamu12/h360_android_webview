package cd.h360.pos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cd.h360.pos.databinding.ActivityCopilotComposerBinding

class CopilotComposerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCopilotComposerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCopilotComposerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = getString(R.string.copilot_widget_title)
        binding.inputPrompt.setText(intent.getStringExtra(H360CopilotWidgetProvider.EXTRA_PROMPT).orEmpty())

        binding.btnSendPrompt.setOnClickListener {
            val prompt = binding.inputPrompt.text?.toString()?.trim().orEmpty()
            if (prompt.isBlank()) return@setOnClickListener

            H360WidgetUpdater.rememberCopilotPrompt(this, prompt)
            H360WidgetUpdater.rememberCopilotResponse(this, getString(R.string.copilot_response_pending))
            H360CopilotWidgetProvider.refreshAll(this)

            val uri = Uri.parse("h360://shortcut/copilot?prompt=${Uri.encode(prompt)}")
            val intent = Intent(Intent.ACTION_VIEW, uri, this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            finish()
        }
    }
}
