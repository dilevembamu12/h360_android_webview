package cd.h360.pos

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java).apply {
                action = this@SplashActivity.intent?.action
                data = this@SplashActivity.intent?.data
                putExtras(this@SplashActivity.intent ?: return@apply)
            }
            startActivity(intent)
            finish()
        }, BuildConfig.SPLASH_DELAY_MS.toLong())
    }
}
