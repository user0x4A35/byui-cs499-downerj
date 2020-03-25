package io.github.ascenderx.mobilescript

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import io.github.ascenderx.mobilescript.models.scripting.ScriptEngine
import io.github.ascenderx.mobilescript.models.scripting.ScriptEngineHandler
import io.github.ascenderx.mobilescript.models.scripting.ScriptEventListener
import io.github.ascenderx.mobilescript.ui.dialog.ConfirmationDialog
import io.github.ascenderx.mobilescript.ui.dialog.DialogHandler
import io.github.ascenderx.mobilescript.ui.dialog.TextInputDialog
import io.github.ascenderx.mobilescript.ui.menu.MenuHandler

class MainActivity : AppCompatActivity(),
    ScriptEngineHandler,
    ScriptEventListener,
    DialogHandler,
    MenuHandler {
    companion object {
        const val REQUEST_GET_CONTENT = 1
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navView: NavigationView
    private lateinit var menu: Menu
    private var clearHistoryMenuItem: MenuItem? = null
    private var createShortcutMenuItem: MenuItem? = null
    private var stopEngineMenuItem: MenuItem? = null
    private var engine: ScriptEngine? = null
    private val scriptListeners: MutableMap<String, ScriptEventListener> = mutableMapOf()
    override val commandHistory: List<String>
        get() = engine?.commandHistory ?: listOf()
    override val isEngineBusy: Boolean
        get() = engine!!.isBusy
    private lateinit var gson: Gson
    override val shortcuts: MutableMap<String, Uri> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup the UI.
        setupToolbar()
        setupDrawer()

        gson = Gson()
        val sharedPreferences: SharedPreferences? = getSharedPreferences(
            getString(R.string.id_pref_shortcuts), Context.MODE_PRIVATE
        )
        val uriMapString: String? = sharedPreferences?.getString(getString(R.string.key_pref_uri_map), "{}")
        if (uriMapString != null) {
            val uriMap: Map<*,*> = gson.fromJson(uriMapString, Map::class.java)
            for ((key, value) in uriMap) {
                shortcuts[key.toString()] = Uri.parse(value.toString())
            }
        }

        // Start up the scripting engine.
        restartScriptEngine(intent?.data)
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
    }

    private fun setupDrawer() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        val navController: NavController = findNavController(R.id.nav_host_fragment)
        val navMenu: Menu = navView.menu
        val itemConsole: MenuItem? = navMenu.findItem(R.id.nav_view_console)
        val itemShortcuts: MenuItem? = navMenu.findItem(R.id.nav_goto_shortcuts)

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.nav_console, R.id.nav_shortcut
        ), drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_open_file -> {
                    showScriptOpenDialog()
                }
                R.id.nav_view_console -> {
                    navController.navigate(R.id.nav_console)
                    itemConsole?.isVisible = false
                    itemShortcuts?.isVisible = true
                }
                R.id.nav_goto_shortcuts -> {
                    navController.navigate(R.id.nav_shortcut)
                    itemConsole?.isVisible = true
                    itemShortcuts?.isVisible = false
                }
            }
            drawerLayout.closeDrawers()
            true
        }
        // Initializing in the console, so hide its menu item and reveal the other top-level
        // destination items.
        itemConsole?.isVisible = false
        itemShortcuts?.isVisible = true
    }

    override fun onDestroy() {
        engine?.kill()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        this.menu = menu
        menuInflater.inflate(R.menu.main, menu)

        // Initially hide the shortcut creation menu item.
        createShortcutMenuItem = menu.findItem(R.id.action_create_shortcut)
        createShortcutMenuItem?.isVisible = false

        // Initially hide the history clear menu item.
        clearHistoryMenuItem = menu.findItem(R.id.action_clear_history)
        clearHistoryMenuItem?.isVisible = false

        // Initially hide the stop engine menu item.
        stopEngineMenuItem = menu.findItem(R.id.action_stop_engine)
        stopEngineMenuItem?.isVisible = false

        attachScriptEventListener("MS.Main.onScript", this)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_clear_console -> {
                onMenuItemClearConsole()
            }
            R.id.action_stop_engine -> {
                onMenuItemStopEngine()
            }
            R.id.action_reset_engine -> {
                onMenuItemResetEngine()
            }
            R.id.action_create_shortcut -> {
                onMenuItemCreateShortcut()
            }
            R.id.action_clear_history -> {
                onMenuItemClearHistory()
            }
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
        return true
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

    override fun onBackPressed() {
        if (engine!!.isBusy) {
            onMenuItemStopEngine()
        }
    }

    override fun onScriptEvent(eventType: Int, data: Any?) {
        when (eventType) {
            ScriptEngine.EVENT_SCRIPT_RUN -> {
                createShortcutMenuItem?.isVisible = true
                stopEngineMenuItem?.isVisible = true
            }
            ScriptEngine.EVENT_RESULT -> {
                clearHistoryMenuItem?.isVisible = true
                stopEngineMenuItem?.isVisible = false
            }
            ScriptEngine.EVENT_EVALUATE_ERROR -> {
                clearHistoryMenuItem?.isVisible = true
                stopEngineMenuItem?.isVisible = false
            }
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
        engine?.kill()
        engine = ScriptEngine(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                for ((_, listener) in scriptListeners) {
                    listener.onScriptEvent(msg.what, msg.obj)
                }
            }
        }, this)

        if (fileUri != null) {
            engine?.loadUserSource(fileUri)
        }
        engine?.start()
    }

    override fun restartScriptEngine() {
        restartScriptEngine(null)
    }

    override fun postData(data: String): Boolean {
        stopEngineMenuItem?.isVisible = true
        return engine!!.postData(data)
    }

    override fun attachScriptEventListener(id: String, listener: ScriptEventListener) {
        if (!scriptListeners.containsKey(id)) {
            scriptListeners[id] = listener
        }
    }

    override fun detachScriptEventListener(id: String) {
        if (scriptListeners.containsKey(id)) {
            scriptListeners.remove(id)
        }
    }

    override fun clearCommandHistory() {
        engine?.clearCommandHistory()
    }

    override fun interrupt() {
        engine?.interrupt()
    }

    override fun showOptionItem(id: Int) {
        val item: MenuItem? = menu.findItem(R.id.action_clear_history)
        item?.isVisible = true
    }

    override fun hideOptionItem(id: Int) {
        val item: MenuItem? = menu.findItem(R.id.action_clear_history)
        item?.isVisible = false
    }

    override fun navigateTo(destination: Int) {
        val navController: NavController = findNavController(R.id.nav_host_fragment)
        navController.navigate(destination)
    }
}
