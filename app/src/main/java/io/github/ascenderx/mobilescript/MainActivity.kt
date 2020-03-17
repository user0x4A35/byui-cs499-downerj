package io.github.ascenderx.mobilescript

import android.content.DialogInterface
import android.content.Intent
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
import io.github.ascenderx.mobilescript.models.data.StringReference
import io.github.ascenderx.mobilescript.models.scripting.ScriptEngine
import io.github.ascenderx.mobilescript.models.scripting.ScriptEngineHandler
import io.github.ascenderx.mobilescript.models.scripting.ScriptEventListener
import io.github.ascenderx.mobilescript.ui.shortcuts.ShortcutFragment

class MainActivity : AppCompatActivity(),
    ScriptEngineHandler {
    companion object {
        const val REQUEST_GET_CONTENT = 1
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navView: NavigationView
    private var clearHistoryMenuItem: MenuItem? = null
    private var createShortcutMenuItem: MenuItem? = null
    private var stopEngineMenuItem: MenuItem? = null
    private lateinit var engine: ScriptEngine
    private val listeners: MutableMap<String, ScriptEventListener> = mutableMapOf()
    override val commandHistory: List<String>
        get() = engine.commandHistory
    override val isEngineBusy: Boolean
        get() = engine.isBusy

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup the UI.
        setupToolbar()
        setupDrawer()

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
        val navController = findNavController(R.id.nav_host_fragment)
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

        // Initially hide the stop engine menu item.
        stopEngineMenuItem = menu.findItem(R.id.action_stop_engine)
        stopEngineMenuItem?.isVisible = false

        attachScriptEventListener("MS.Main.onScript", object : ScriptEventListener {
            override fun onScriptEvent(eventType: Int, data: Any?) {
                when (eventType) {
                    ScriptEngine.EVENT_SCRIPT_RUN -> {
                        // Disable shortcut creation if phone is too old.
                        createShortcutMenuItem?.isVisible =
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
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
        })
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
                for ((_, listener) in listeners) {
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
        val promptView: View = layoutInflater.inflate(R.layout.prompt_confirm, null)
        val dialogBuilder = AlertDialog.Builder(this)
            .setView(promptView)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_button_ok, okCallback)
            .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ -> dialog?.cancel() }
        val lblMessage: TextView = promptView.findViewById(R.id.lbl_confirm_message)
        lblMessage.text = message
        val alertDialog: AlertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    private fun showInputDialog(
        message: String,
        hint: String,
        inputString: StringReference,
        okCallback: DialogInterface.OnClickListener
    ) {
        val promptView: View = layoutInflater.inflate(R.layout.prompt_input, null)
        val dialogBuilder = AlertDialog.Builder(this)
            .setView(promptView)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_button_ok, okCallback)
            .setNegativeButton(R.string.dialog_button_cancel) { dialog, _ -> dialog?.cancel() }
        val lblMessage: TextView = promptView.findViewById(R.id.lbl_input_message)
        lblMessage.text = message
        val txtInput: EditText = promptView.findViewById(R.id.txt_input_prompt)
        txtInput.hint = hint
        txtInput.addTextChangedListener(object : TextWatcher {
            // TODO: Find an efficient way to modify the incoming string reference without
            //  using a custom StringReference class.
            override fun afterTextChanged(text: Editable?) {
                inputString.value = text?.toString() ?: inputString.value
            }
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        val alertDialog: AlertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    private fun onMenuItemClearConsole() {
        val message: String = getString(R.string.dialog_message_clear_console)
        showConfirmationDialog(message, DialogInterface.OnClickListener { _, _ ->
            for ((_, listener) in listeners) {
                listener.onScriptEvent(ScriptEngine.EVENT_CLEAR_CONSOLE)
            }
        })
    }

    private fun onMenuItemResetEngine() {
        val message: String = getString(R.string.dialog_message_reset_engine)
        showConfirmationDialog(message, DialogInterface.OnClickListener { _, _ ->
            for ((_, listener) in listeners) {
                listener.onScriptEvent(ScriptEngine.EVENT_RESTART)
            }
            engine.kill()
            restartScriptEngine(null)
        })
        stopEngineMenuItem?.isVisible = false
        createShortcutMenuItem?.isVisible = false
    }

    private fun onMenuItemClearHistory() {
        val message: String = getString(R.string.dialog_message_clear_history)
        showConfirmationDialog(message, DialogInterface.OnClickListener { _, _ ->
            clearHistoryMenuItem?.isVisible = false
            engine.clearCommandHistory()
            engine.sendMessage(ScriptEngine.EVENT_HISTORY_CLEAR)
        })
    }

    private fun onMenuItemCreateShortcut() {
        val message: String = getString(R.string.dialog_message_create_shortcut)
        val hint: String = getString(R.string.shortcut_name_hint)
        val inputString = StringReference()
        showInputDialog(message, hint, inputString, DialogInterface.OnClickListener { _, _ ->
            val destination =
                ShortcutFragment()
            val transaction = supportFragmentManager.beginTransaction()
            transaction.replace(R.id.nav_host_fragment, destination)
            transaction.addToBackStack(null)
            transaction.commit()
        })
    }

    private fun onMenuItemStopEngine() {
        val message: String = getString(R.string.dialog_message_stop_engine)
        showConfirmationDialog(message, DialogInterface.OnClickListener { _, _ ->
            engine.interrupt()
            stopEngineMenuItem?.isVisible = false
        })
    }

    override fun postData(data: String): Boolean {
        stopEngineMenuItem?.isVisible = true
        return engine.postData(data)
    }

    override fun attachScriptEventListener(id: String, listener: ScriptEventListener) {
        if (!listeners.containsKey(id)) {
            listeners[id] = listener
        }
    }

    override fun detachScriptEventListener(id: String) {
        if (listeners.containsKey(id)) {
            listeners.remove(id)
        }
    }

    override fun onBackPressed() {
        if (engine.isBusy) {
            onMenuItemStopEngine()
        }
    }
}
