package io.github.ascenderx.mobilescript

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.*
import android.renderscript.Script
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.RequiresApi
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
import io.github.ascenderx.mobilescript.models.scripting.ScriptEventEmitter
import io.github.ascenderx.mobilescript.models.scripting.ScriptEventListener
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity(),
    ScriptEventEmitter {
    companion object {
        const val REQUEST_GET_CONTENT = 1
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navView: NavigationView
    override lateinit var engine: ScriptEngine
    private val listeners: MutableList<ScriptEventListener> = mutableListOf()

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
        navView.setNavigationItemSelectedListener(object : NavigationView.OnNavigationItemSelectedListener {
            override fun onNavigationItemSelected(item: MenuItem): Boolean {
                when (item.itemId) {
                    R.id.nav_open -> showScriptOpenDialog()
                }
                return true
            }
        })

        // Start up the scripting engine.
        initScriptEngine()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear -> {
                engine.sendMessage(ScriptEngine.STATUS_CLEAR, null)
                true
            }
            R.id.action_reset -> {
                engine.sendMessage(ScriptEngine.STATUS_RESTART, null)
                true
            }
            R.id.action_shortcut -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    createScriptShortcut()
                }
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
        if (requestCode == REQUEST_GET_CONTENT && resultCode == Activity.RESULT_OK) {
            val fileUri: Uri = data?.data ?: return
            engine.loadUserSource(fileUri)
        }
    }

    private fun showScriptOpenDialog() {
        val intent = Intent()
        intent.type = "*/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(
            Intent.createChooser(intent, "Select a file"),
            REQUEST_GET_CONTENT
        )
    }

    private fun initScriptEngine() {
        engine = ScriptEngine.getInstance(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                for (listener in listeners) {
                    listener.onMessage(msg)
                }
            }
        }, this)
        engine.start()
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun createScriptShortcut() {
        if (engine.currentFileUri == null) {
            return
        }

        val shortcutManager: ShortcutManager = getSystemService(
            ShortcutManager::class.java
        ) as ShortcutManager
        val uri: Uri = engine.currentFileUri as Uri
        val shortcut = ShortcutInfo.Builder(this, "id0")
            .setShortLabel("Script")
            .setLongLabel("Run user script")
            .setIntent(Intent(Intent.ACTION_VIEW, uri))
            .build()
        shortcutManager.dynamicShortcuts = listOf(shortcut)
        engine.sendMessage(
            ScriptEngine.STATUS_SHORTCUT_CREATED,
            uri.path
        )
    }

    override fun attachScriptEventListener(listener: ScriptEventListener) {
        listeners.add(listener)
    }

    override fun onBackPressed() {
        engine.interrupt()
    }
}
