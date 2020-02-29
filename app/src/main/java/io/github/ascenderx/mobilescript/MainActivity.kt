package io.github.ascenderx.mobilescript

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.Menu
import android.view.MenuItem
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
import java.io.InputStream
import java.io.InputStreamReader

class MainActivity : AppCompatActivity(),
    ScriptEventEmitter {
    companion object {
        const val REQUEST_GET_CONTENT = 1
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navView: NavigationView
    override lateinit var engine: ScriptEngine
    private val listeners: MutableList<ScriptEventListener> = mutableListOf()
    private val handler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            for (listener in listeners) {
                listener.onMessage(msg)
            }
        }
    }

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
                val message: Message = handler.obtainMessage(ScriptEngine.STATUS_CLEAR)
                handler.sendMessage(message)
                true
            }
            R.id.action_reset -> {
                val message: Message = handler.obtainMessage(ScriptEngine.STATUS_RESTART)
                handler.sendMessage(message)
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
            try {
                val source: String = loadScriptFromContentUri(fileUri)
                // Tell the UI to update for restart.
                val message: Message = handler.obtainMessage(
                    ScriptEngine.STATUS_SCRIPT_RUN,
                    source
                )
                handler.sendMessage(message)
            } catch (ex: IOException) {
                Log.e("MS.main.openScript", ex.message as String)
            }
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
        engine = ScriptEngine.getInstance(handler)
        val sources: List<String> = loadScriptAssets()
        engine.addSources(sources)
        engine.start()
    }

    private fun loadScriptAssets(): List<String> {
        val sources: MutableList<String> = mutableListOf()
        val fileNames: Array<String>? = assets.list("sources")
        if (fileNames != null) {
            for (fileName in fileNames) {
                try {
                    sources.add(loadScript("sources/$fileName"))
                } catch (ex: IOException) {
                    Log.e("MS.main.loadScripts", ex.message as String)
                }
            }
        }
        return sources
    }

    private fun loadScriptFromContentUri(uri: Uri): String {
        val stream: InputStream = contentResolver.openInputStream(uri)
            ?: throw IOException("Content stream failed to open")
        val reader = InputStreamReader(stream)
        val buffer = StringBuffer()
        var ch: Int = reader.read()
        while (ch >= 0) {
            buffer.append(ch.toChar())
            ch = reader.read()
        }
        return buffer.toString()
    }

    private fun loadScript(path: String): String {
        val stream: InputStream = assets.open(path)
        val reader = InputStreamReader(stream)
        val buffer = StringBuffer()
        var ch: Int = reader.read()
        while (ch >= 0) {
            buffer.append(ch.toChar())
            ch = reader.read()
        }
        return buffer.toString()
    }

    override fun attachScriptEventListener(listener: ScriptEventListener) {
        listeners.add(listener)
    }
}
