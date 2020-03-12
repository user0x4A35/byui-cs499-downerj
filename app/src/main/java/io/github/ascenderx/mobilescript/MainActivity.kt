package io.github.ascenderx.mobilescript

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.*
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
        val uri: Uri? = intent?.data
        initScriptEngine(uri)
    }

    override fun onDestroy() {
        engine.kill()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)

        // Initially hide the shortcut creation menu item.
        val shortcutMenuItem: MenuItem? = menu.findItem(R.id.action_shortcut)
        shortcutMenuItem?.isVisible = false

        attachScriptEventListener(object : ScriptEventListener {
            override fun onMessage(msg: Message) {
                when (msg.what) {
                    ScriptEngine.STATUS_SCRIPT_RUN -> {
                        // Disable shortcut creation if phone is too old.
                        shortcutMenuItem?.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    }
                }
            }
        })

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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        if (requestCode == REQUEST_GET_CONTENT && resultCode == RESULT_OK) {
            val fileUri: Uri = data?.data ?: return
            engine.loadUserSource(fileUri)
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

    private fun initScriptEngine(fileUri: Uri?) {
        engine = ScriptEngine(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                for (listener in listeners) {
                    listener.onMessage(msg)
                }
            }
        }, this)

        if (fileUri != null) {
            engine.loadUserSource(fileUri)
        } else {
            engine.startEmpty()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createScriptShortcut() {
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

    override fun attachScriptEventListener(listener: ScriptEventListener) {
        listeners.add(listener)
    }

    override fun onBackPressed() {
        engine.interrupt()
    }
}
