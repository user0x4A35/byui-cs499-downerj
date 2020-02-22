package io.github.ascenderx.mobilescript

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8ScriptExecutionException
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.ascenderx.mobilescript.ui.home.HomeFragment
import io.github.ascenderx.mobilescript.ui.home.OnResultListener

class MainActivity : AppCompatActivity(), HomeFragment.Evaluator {
    override var callback: OnResultListener? = null
    private var command: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Provided.
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun bootRuntime(fragment: HomeFragment) {
        // Initialize the JavaScript runtime.
        val thread = Thread(object : Runnable {
            override fun run() {
                val runtime = V8.createV8Runtime()

                runtime.registerJavaMethod(HomeFragment.PrintCallback(fragment), "print")
                runtime.registerJavaMethod(HomeFragment.PrintLineCallback(fragment), "println")
                runtime.registerJavaMethod(HomeFragment.ClearCallback(fragment), "clear")

                while (true) {
                    if (command != null) {
                        var result: Any?
                        var error: String?

                        try {
                            result = runtime.executeScript(command)
                            error = null
                        } catch (ex: V8ScriptExecutionException) {
                            result = null
                            error = ex.message.toString()
                        }
                        callback?.onResult(command, result, error)
                        command = null
                    }
                }
            }
        })
        thread.start()
    }

    override fun evaluate(command: String) {
        this.command = command
    }
}


