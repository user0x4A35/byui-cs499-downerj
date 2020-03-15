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
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.nav_console, R.id.nav_shortcut
        ), drawerLayout)
//        appBarConfiguration = AppBarConfiguration(navController.graph, drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        navView.setNavigationItemSelectedListener { item ->
            onMenuItemClick(item)
            drawerLayout.closeDrawers()
            true
        }

        // Start up the scripting engine.
        val uri: Uri? = intent?.data
        restartScriptEngine(uri)
    }

    private fun onMenuItemClick(item: MenuItem) {
        when (item.itemId) {
            R.id.nav_open_file -> {
                showScriptOpenDialog()
            }
            R.id.nav_view_console -> {
                findNavController(R.id.nav_host_fragment)
                    .navigate(R.id.nav_console)
            }
            R.id.nav_goto_shortcuts -> {
                findNavController(R.id.nav_host_fragment)
                    .navigate(R.id.nav_shortcut)
            }
        }
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
                onMenuItemCreateShortcut()
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

    private fun onMenuItemCreateShortcut() {
        val message: String = getString(R.string.dialog_message_create_shortcut)
        val hint: String = getString(R.string.shortcut_name_hint)
        val inputString = StringReference()
        showInputDialog(message, hint, inputString, DialogInterface.OnClickListener { _, _ ->
            val destination = ShortcutFragment()
            val transaction = supportFragmentManager.beginTransaction()
            transaction.replace(R.id.nav_host_fragment, destination)
            transaction.addToBackStack(null)
            transaction.commit()
        })
    }

    override fun postData(data: String): Boolean = engine.postData(data)

    override fun attachScriptEventListener(listener: ScriptEventListener) {
        listeners.add(listener)
    }

    override fun onBackPressed() {
        engine.interrupt()
    }
}
