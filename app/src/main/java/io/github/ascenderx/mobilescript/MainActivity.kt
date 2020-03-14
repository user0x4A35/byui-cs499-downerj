package io.github.ascenderx.mobilescript

import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.*
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import io.github.ascenderx.mobilescript.models.scripting.ScriptEngine
import io.github.ascenderx.mobilescript.models.scripting.ScriptEngineHandler
import io.github.ascenderx.mobilescript.models.scripting.ScriptEventListener

class MainActivity : AppCompatActivity(),
    ScriptEngineHandler {
    companion object {
        const val REQUEST_GET_CONTENT = 1
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navView: NavigationView
    private var clearHistoryMenuItem: MenuItem? = null
    private var createShortcutMenuItem: MenuItem? = null
    private lateinit var engine: ScriptEngine
    private val listeners: MutableList<ScriptEventListener> = mutableListOf()
    override val commandHistory: List<String>
        get() = engine.commandHistory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(setOf(R.id.nav_console), drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_open -> showScriptOpenDialog()
            }
            true
        }

        // Start up the scripting engine.
        val uri: Uri? = intent?.data
        restartScriptEngine(uri)
    }

    override fun onDestroy() {
        engine.kill()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)

        // Initially hide the shortcut creation menu item.
        createShortcutMenuItem = menu.findItem(R.id.action_create_shortcut)
        createShortcutMenuItem?.isVisible = false

        // Initially hide the history clear menu item.
        clearHistoryMenuItem = menu.findItem(R.id.action_clear_history)
        clearHistoryMenuItem?.isVisible = false

        attachScriptEventListener(object : ScriptEventListener {
            override fun onScriptEvent(eventType: Int, data: Any?) {
                when (eventType) {
                    ScriptEngine.EVENT_SCRIPT_RUN -> {
                        // Disable shortcut creation if phone is too old.
                        createShortcutMenuItem?.isVisible =
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    }
                    ScriptEngine.EVENT_RESULT -> {
                        // Enable the clear history menu item.
                        clearHistoryMenuItem?.isVisible = true
                    }
                    ScriptEngine.EVENT_EVALUATE_ERROR -> {
                        // Enable the clear history menu item.
                        clearHistoryMenuItem?.isVisible = true
                    }
                }
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_console -> {
                onMenuItemClearConsole()
                true
            }
            R.id.action_reset_engine -> {
                onMenuItemResetEngine()
                true
            }
            R.id.action_create_shortcut -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    onMenuItemCreateShortcut()
                }
                true
            }
            R.id.action_clear_history -> {
                onMenuItemClearHistory()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_GET_CONTENT && resultCode == RESULT_OK) {
            val fileUri: Uri = data?.data ?: return
            restartScriptEngine(fileUri)
        }
    }

    private fun showScriptOpenDialog() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        startActivityForResult(
            Intent.createChooser(intent, "Select a file"),
            REQUEST_GET_CONTENT
        )
    }

    private fun restartScriptEngine(fileUri: Uri?) {
        engine = ScriptEngine(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                for (listener in listeners) {
                    listener.onScriptEvent(msg.what, msg.obj)
                }
            }
        }, this)

        if (fileUri != null) {
            engine.loadUserSource(fileUri)
        }
        engine.start()
    }

    private fun showConfirmationDialog(message: String, okCallback: DialogInterface.OnClickListener) {
        // Confirm the user's decision.
        val promptView: View = layoutInflater.inflate(R.layout.confirm_prompt, null)
        val dialogBuilder = AlertDialog.Builder(this)
            .setView(promptView)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_button_ok, okCallback)
            .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ -> dialog?.cancel() }
        val lblMessage: TextView = promptView.findViewById(R.id.lbl_message)
        lblMessage.text = message
        val alertDialog: AlertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    private fun onMenuItemClearConsole() {
        val message: String = getString(R.string.dialog_message_clear_console)
        showConfirmationDialog(message, DialogInterface.OnClickListener { _, _ ->
            for (listener: ScriptEventListener in listeners) {
                listener.onScriptEvent(ScriptEngine.EVENT_CLEAR_CONSOLE)
            }
        })
    }

    private fun onMenuItemResetEngine() {
        val message: String = getString(R.string.dialog_message_reset_engine)
        showConfirmationDialog(message, DialogInterface.OnClickListener { _, _ ->
            for (listener: ScriptEventListener in listeners) {
                listener.onScriptEvent(ScriptEngine.EVENT_RESTART)
            }
            engine.kill()
            restartScriptEngine(null)
        })
    }

    private fun onMenuItemClearHistory() {
        val message: String = getString(R.string.dialog_message_clear_history)
        showConfirmationDialog(message, DialogInterface.OnClickListener { _, _ ->
            clearHistoryMenuItem?.isVisible = false
            engine.clearCommandHistory()
            engine.sendMessage(ScriptEngine.EVENT_HISTORY_CLEAR)
        })
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun onMenuItemCreateShortcut() {
        val shortcutManager: ShortcutManager? = getSystemService(ShortcutManager::class.java)
        if (shortcutManager!!.isRequestPinShortcutSupported) {
            if (engine.currentFileUri == null) {
                return
            }

            val uri: Uri = engine.currentFileUri as Uri
            val intent = Intent(
                Intent.ACTION_MAIN,
                uri,
                this,
                MainActivity::class.java
            )
            intent.type = "*/*"
            intent.data = uri
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            intent.`package` = "io.github.ascenderx.mobilescript"
            // TODO: Create fragment to let user customize shortcut label.
            val pinShortcutInfo: ShortcutInfo = ShortcutInfo.Builder(this, "scriptShortcut")
                .setIcon(Icon.createWithResource(this, R.drawable.ic_script))
                .setShortLabel(uri.toString())
                .setIntent(intent)
                .build()
            shortcutManager.requestPinShortcut(pinShortcutInfo, null)
        }
    }

    override fun postData(data: String): Boolean = engine.postData(data)

    override fun attachScriptEventListener(listener: ScriptEventListener) {
        listeners.add(listener)
    }

    override fun onBackPressed() {
        engine.interrupt()
    }
}
