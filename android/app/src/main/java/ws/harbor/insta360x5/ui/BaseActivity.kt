package ws.harbor.insta360x5.ui

import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * На Android 15+ (API 35) при targetSdk 35/36 система включает edge-to-edge:
 * контент рисуется под status bar и ActionBar, из‑за чего зелёная полоса
 * перекрывает кнопки и подсказки. Здесь добавляется отступ под них.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun setContentView(layoutResID: Int) {
        prepareEdgeToEdge()
        super.setContentView(layoutResID)
        installContentInsets()
    }

    override fun setContentView(view: View?) {
        prepareEdgeToEdge()
        super.setContentView(view)
        installContentInsets()
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        prepareEdgeToEdge()
        super.setContentView(view, params)
        installContentInsets()
    }

    private fun prepareEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= 35) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    private fun installContentInsets() {
        if (Build.VERSION.SDK_INT < 35) return
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        val actionBarSize = resolveActionBarSize()
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                bars.left,
                bars.top + actionBarSize,
                bars.right,
                bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }

    private fun resolveActionBarSize(): Int {
        val tv = TypedValue()
        return if (theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)) {
            TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        } else {
            0
        }
    }
}
