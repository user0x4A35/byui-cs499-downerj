package io.github.ascenderx.mobilescript

import android.content.res.AssetManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import io.github.ascenderx.mobilescript.models.scripting.ScriptEngine
import io.github.ascenderx.mobilescript.models.scripting.ScriptEventEmitter
import io.github.ascenderx.mobilescript.models.scripting.ScriptEventListener
import io.github.ascenderx.mobilescript.models.scripting.ScriptMessageStatus
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class MainActivity : AppCompatActivity(),
    ScriptEventEmitter {
    private lateinit var appBarConfiguration: AppBarConfiguration
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
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(setOf(R.id.nav_console), drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

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
                val message: Message = handler.obtainMessage(ScriptMessageStatus.CLEAR.value)
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

    private fun initScriptEngine() {
        engine = ScriptEngine.getInstance(handler)
        val sources: List<String> = loadScriptAssets()
        engine.addSources(sources)
        engine.start()
    }

    private fun loadScriptAssets(): List<String> {
        val sources: MutableList<String> = mutableListOf()
        val paths: Array<String>? = assets.list("sources")
        if (paths != null) {
            for (path in paths) {
                try {
                    val stream: InputStream = assets.open("sources/$path")
                    val reader = InputStreamReader(stream)
                    val buffer = StringBuffer()
                    var ch: Int = reader.read()
                    while (ch >= 0) {
                        buffer.append(ch.toChar())
                        ch = reader.read()
                    }
                    sources.add(buffer.toString())
                } catch (ex: IOException) {
                    Log.e("MS.main.loadScripts", ex.message as String)
                }
            }
        }
        return sources
    }

    override fun attachScriptEventListener(listener: ScriptEventListener) {
        listeners.add(listener)
    }
}
