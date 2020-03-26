package io.github.ascenderx.mobilescript.views.ui.shortcuts

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import io.github.ascenderx.mobilescript.R
import io.github.ascenderx.mobilescript.models.scripting.ScriptEngineHandler

class ShortcutFragment : Fragment() {
    private val viewModel: ShortcutViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is ScriptEngineHandler) {
            viewModel.initializeData(context.shortcuts)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root: View? = inflater.inflate(
            R.layout.fragment_shortcut,
            container,
            false
        )

        val shortcutGrid: GridView = root?.findViewById(R.id.shortcut_grid) as GridView
        val gridAdapter = ShortcutListAdapter(inflater)
        shortcutGrid.adapter = gridAdapter
        viewModel.liveData.observe(viewLifecycleOwner, Observer {
            gridAdapter.data = it
        })

        return root
    }

}
