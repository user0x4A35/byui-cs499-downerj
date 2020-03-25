package io.github.ascenderx.mobilescript

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.*
import android.view.Menu
import android.view.MenuItem
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
import io.github.ascenderx.mobilescript.ui.menu.MenuEventListener
import io.github.ascenderx.mobilescript.ui.menu.MenuHandler

class MainActivity : AppCompatActivity(),
    ScriptEngineHandler,
    MenuHandler {
    companion object {
        const val REQUEST_GET_CONTENT = 1
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navView: NavigationView
    private var menu: Menu? = null
    private val optionItems: List<Int> = listOf(
        R.id.action_clear_history,
        R.id.action_clear_console,
        R.id.action_create_shortcut,
        R.id.action_reset_engine,
        R.id.action_stop_engine
    )
    private var engine: ScriptEngine? = null
    private var scriptListener: ScriptEventListener? = null
    private var menuListener: MenuEventListener? = null
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

        for (id in optionItems) {
            menu.findItem(id)?.isVisible = false
        }
        val visibleItems: List<Int>? = menuListener?.getVisibleOptionItems()
        if (visibleItems != null) {
            for (id in visibleItems) {
                menu.findItem(id)?.isVisible = true
            }
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId in optionItems) {
            menuListener?.onOptionItemEvent(item.itemId)
            true
        } else {
            super.onOptionsItemSelected(item)
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

    override fun onBackPressed() {
        if (engine!!.isBusy) {
            menuListener?.onOptionItemEvent(R.id.action_stop_engine)
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
                scriptListener?.onScriptEvent(msg.what, msg.obj)
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
        return engine!!.postData(data)
    }

    override fun attachScriptEventListener(listener: ScriptEventListener) {
        scriptListener = listener
    }

    override fun detachScriptEventListener() {
        scriptListener = null
    }

    override fun attachMenuEventListener(listener: MenuEventListener) {
        menuListener = listener
    }

    override fun detachMenuEventListener() {
        menuListener = null
    }

    override fun clearCommandHistory() {
        engine?.clearCommandHistory()
    }

    override fun interrupt() {
        engine?.interrupt()
    }

    override fun showOptionItem(id: Int) {
        val item: MenuItem? = menu?.findItem(id)
        item?.isVisible = true
    }

    override fun hideOptionItem(id: Int) {
        val item: MenuItem? = menu?.findItem(id)
        item?.isVisible = false
    }

    override fun navigateTo(destination: Int) {
        val navController: NavController = findNavController(R.id.nav_host_fragment)
        navController.navigate(destination)
    }
}
